package com.example.agent.web.api.voice;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.web.api.dto.VoiceCorrectionRequest;
import com.example.agent.web.api.dto.VoiceCorrectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeepSeek 驱动的语音纠错服务（improve-voice-accuracy T7）。
 *
 * <h2>行为</h2>
 * <ul>
 *   <li>系统提示 + 用户 prompt 构造 {@link ChatRequest}，调 {@link LlmProvider#streamChat(ChatRequest)}
 *   <li>1500ms 整体超时；超时 / 5xx / 空响应都降级返回 {@code corrected=null}
 *   <li>5 分钟内同 {@code (rawText, recentTurns 哈希)} 命中缓存
 *   <li>sessionId 令牌桶限流（5 req/s）→ 超限抛 {@link VoiceRateLimitException}
 * </ul>
 *
 * <h2>线程模型</h2>
 * 本服务为同步阻塞调用者。{@code streamChat} 是 Reactor Flux；为避免阻塞事件循环线程，
 * Controller 在 {@code Schedulers.boundedElastic()} 上调用本方法。
 */
public class DeepSeekVoiceCorrectionService implements VoiceCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekVoiceCorrectionService.class);

    /** 整体请求-响应超时（含网络往返 + provider 解析） */
    static final Duration TIMEOUT = Duration.ofMillis(1500);

    /** 缓存 TTL：5 分钟 */
    static final long CACHE_TTL_MS = 5L * 60 * 1000;

    /** sessionId 令牌桶容量（每会话每秒最多 5 次） */
    static final int TOKEN_BUCKET_CAPACITY = 5;

    /** 令牌桶窗口（1 秒） */
    static final long TOKEN_BUCKET_WINDOW_MS = 1000L;

    /** 默认纠错模型（与 add-deepseek-v4-models 对齐：v4-flash 平替 deepseek-chat） */
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";

    /** 系统提示词（voice-correction 专用） */
    private static final String SYSTEM_PROMPT =
            "你是语音识别后处理助手。用户输入是 ASR（语音转文本）的原始识别结果，可能存在同音字错误、"
                    + "语序混乱、语气词重复等问题。请根据对话上下文，将 rawText 修正为自然中文。\n"
                    + "要求：\n"
                    + "1. 只输出修正后的最终文本，不要任何解释、标点符号、引号或 markdown\n"
                    + "2. 如果 rawText 已经合理，原样返回\n"
                    + "3. 长度不超过 rawText 的 2 倍";

    private final LlmProvider provider;
    private final String model;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public DeepSeekVoiceCorrectionService(LlmProvider provider) {
        this(provider, DEFAULT_MODEL);
    }

    public DeepSeekVoiceCorrectionService(LlmProvider provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    @Override
    public VoiceCorrectionResponse correct(VoiceCorrectionRequest req) {
        if (req == null || req.rawText() == null || req.rawText().isBlank()) {
            throw new IllegalArgumentException("rawText 不能为空");
        }
        if (req.sessionId() == null || req.sessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        // 1. 限流：令牌桶
        bucketFor(req.sessionId()).consume();

        // 2. 缓存检查
        String key = cacheKey(req);
        long now = System.currentTimeMillis();
        CacheEntry hit = cache.get(key);
        if (hit != null && now - hit.timestamp < CACHE_TTL_MS) {
            return new VoiceCorrectionResponse(hit.corrected, true, 0L);
        }

        // 3. 调 DeepSeek（含 1500ms 超时）
        long start = now;
        String corrected;
        try {
            corrected = callDeepSeek(req);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("voice-correction 失败（降级 rawText）: {}", e.toString());
            return new VoiceCorrectionResponse(null, false, latency);
        }
        long latency = System.currentTimeMillis() - start;

        // 4. 空响应 → 降级
        if (corrected == null || corrected.isBlank()) {
            return new VoiceCorrectionResponse(null, false, latency);
        }

        // 5. 写缓存
        cache.put(key, new CacheEntry(corrected, now));
        return new VoiceCorrectionResponse(corrected, false, latency);
    }

    /**
     * 同步阻塞调用 DeepSeek（1500ms timeout）。
     *
     * <p>异常路径（超时 / 5xx / provider 内部错误）抛 {@link RuntimeException}，由调用方降级。
     */
    private String callDeepSeek(VoiceCorrectionRequest req) {
        List<Message> messages = new ArrayList<>();
        // 把 recentTurns 作为对话历史喂给模型（user/assistant 交替）
        if (req.recentTurns() != null) {
            for (VoiceCorrectionRequest.RecentTurn t : req.recentTurns()) {
                if (t.user() != null && !t.user().isBlank()) {
                    messages.add(new Message.User(t.user()));
                }
                if (t.assistant() != null && !t.assistant().isBlank()) {
                    messages.add(new Message.Assistant(t.assistant(), java.util.List.of()));
                }
            }
        }
        messages.add(new Message.User(req.rawText()));

        ChatRequest chatReq = new ChatRequest(
                model, SYSTEM_PROMPT, messages, null, 0.2, 200, Map.of());

        Flux<String> flux = provider.streamChat(chatReq)
                .ofType(StreamChunk.TextDelta.class)
                .map(StreamChunk.TextDelta::text);

        // 整体 1500ms 超时；空响应返回 null（区分于异常）。
        String joined = flux
                .collectList()
                .map(list -> String.join("", list))
                .timeout(TIMEOUT)
                .onErrorResume(e -> {
                    // 超时 / 5xx / 任何 provider 异常：降级抛给外层
                    log.warn("DeepSeek 语音纠错异常: {}", e.toString());
                    return Mono.error(e);
                })
                .block();
        return joined;
    }

    /** 缓存 key = SHA-256(rawText + '|' + 排序后的 recentTurns) */
    static String cacheKey(VoiceCorrectionRequest req) {
        StringBuilder sb = new StringBuilder(req.rawText());
        if (req.recentTurns() != null) {
            for (VoiceCorrectionRequest.RecentTurn t : req.recentTurns()) {
                sb.append('|').append(t.user() == null ? "" : t.user());
                sb.append('#').append(t.assistant() == null ? "" : t.assistant());
            }
        }
        return sha256(sb.toString());
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private TokenBucket bucketFor(String sessionId) {
        return buckets.computeIfAbsent(sessionId, k -> new TokenBucket());
    }

    /** 简单滑动窗口令牌桶：1 秒内最多 TOKEN_BUCKET_CAPACITY 次；超限抛 {@link VoiceRateLimitException} */
    static final class TokenBucket {
        private final ArrayList<Long> timestamps = new ArrayList<>();

        synchronized void consume() {
            long now = System.currentTimeMillis();
            long cutoff = now - TOKEN_BUCKET_WINDOW_MS;
            // 清掉窗口外的旧时间戳
            while (!timestamps.isEmpty() && timestamps.get(0) < cutoff) {
                timestamps.remove(0);
            }
            if (timestamps.size() >= TOKEN_BUCKET_CAPACITY) {
                throw new VoiceRateLimitException(
                        "voice-correction 限流：每个 sessionId 每秒最多 " + TOKEN_BUCKET_CAPACITY + " 次");
            }
            timestamps.add(now);
        }
    }

    /** 缓存条目 */
    static final class CacheEntry {
        final String corrected;
        final long timestamp;

        CacheEntry(String corrected, long timestamp) {
            this.corrected = corrected;
            this.timestamp = timestamp;
        }
    }
}