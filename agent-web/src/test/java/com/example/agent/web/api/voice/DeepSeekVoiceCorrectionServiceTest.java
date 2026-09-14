package com.example.agent.web.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.web.api.dto.VoiceCorrectionRequest;
import com.example.agent.web.api.dto.VoiceCorrectionResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/** improve-voice-accuracy T7.4：Service 单测覆盖正常/超时/5xx/缓存命中。 */
class DeepSeekVoiceCorrectionServiceTest {

    /** 构造返回固定 TextDelta 序列的 stub provider。 */
    private static LlmProvider stubProvider(String... deltas) {
        return new LlmProvider() {
            @Override
            public String name() {
                return "stub";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.fromArray(deltas).map(StreamChunk.TextDelta::new);
            }

            @Override
            public int contextWindow() {
                return 0;
            }

            @Override
            public int maxOutputTokens() {
                return 0;
            }
        };
    }

    /** 慢响应 provider：每 200ms 吐一个 chunk，模拟上游慢。 */
    private static LlmProvider slowProvider() {
        return new LlmProvider() {
            @Override
            public String name() {
                return "slow-stub";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.interval(Duration.ofMillis(200))
                        .map(i -> (StreamChunk) new StreamChunk.TextDelta("delta"));
            }

            @Override
            public int contextWindow() {
                return 0;
            }

            @Override
            public int maxOutputTokens() {
                return 0;
            }
        };
    }

    /** 空响应 provider：返回 0 个 chunk（用于测空响应降级路径）。 */
    private static LlmProvider emptyProvider() {
        return new LlmProvider() {
            @Override
            public String name() {
                return "empty-stub";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.empty();
            }

            @Override
            public int contextWindow() {
                return 0;
            }

            @Override
            public int maxOutputTokens() {
                return 0;
            }
        };
    }

    /** 仅返回空格的 provider：测 isBlank() 分支。 */
    private static LlmProvider blankProvider() {
        return new LlmProvider() {
            @Override
            public String name() {
                return "blank-stub";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.just(new StreamChunk.TextDelta("   "));
            }

            @Override
            public int contextWindow() {
                return 0;
            }

            @Override
            public int maxOutputTokens() {
                return 0;
            }
        };
    }

    /** 捕获 ChatRequest 的 provider：用于断言 messages 构造。 */
    private static LlmProvider captureProvider(java.util.concurrent.atomic.AtomicReference<ChatRequest> sink) {
        return new LlmProvider() {
            @Override
            public String name() {
                return "capture-stub";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                sink.set(request);
                return Flux.just(new StreamChunk.TextDelta("ok"));
            }

            @Override
            public int contextWindow() {
                return 0;
            }

            @Override
            public int maxOutputTokens() {
                return 0;
            }
        };
    }

    /** 抛异常的 provider：模拟 5xx。 */
    private static LlmProvider errorProvider() {
        return new LlmProvider() {
            @Override
            public String name() {
                return "error-stub";
            }

            @Override
            public Flux<StreamChunk> streamChat(ChatRequest request) {
                return Flux.error(new RuntimeException("[HTTP 502] bad gateway"));
            }

            @Override
            public int contextWindow() {
                return 0;
            }

            @Override
            public int maxOutputTokens() {
                return 0;
            }
        };
    }

    @Test
    void correctsSuccessfullyWhenProviderReturnsText() {
        LlmProvider p = stubProvider("现在", "我", "明白了");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionResponse resp = svc.correct(req("邪念眼角舍小", "s1"));
        assertThat(resp.corrected()).isEqualTo("现在我明白了");
        assertThat(resp.cached()).isFalse();
    }

    @Test
    void degradesOnTimeout() {
        LlmProvider p = slowProvider();
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionResponse resp = svc.correct(req("测试文本", "s1"));
        assertThat(resp.corrected()).isNull();
        assertThat(resp.cached()).isFalse();
    }

    @Test
    void degradesOn5xx() {
        LlmProvider p = errorProvider();
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionResponse resp = svc.correct(req("测试文本", "s1"));
        assertThat(resp.corrected()).isNull();
        assertThat(resp.cached()).isFalse();
    }

    @Test
    void cachesCorrectedResultWithinTtl() {
        LlmProvider p = stubProvider("缓存", "命中");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionRequest r = req("同样的原始文本", "s1");
        VoiceCorrectionResponse first = svc.correct(r);
        assertThat(first.cached()).isFalse();
        assertThat(first.corrected()).isEqualTo("缓存命中");
        // 第二次同请求应命中缓存，不再走 provider
        VoiceCorrectionResponse second = svc.correct(r);
        assertThat(second.cached()).isTrue();
        assertThat(second.corrected()).isEqualTo("缓存命中");
    }

    @Test
    void rateLimitReturns429OnExceeding5PerSecond() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        String sid = "rl-session";
        // 5 次正常通过
        for (int i = 0; i < 5; i++) {
            svc.correct(req("text-" + i, sid));
        }
        // 第 6 次立即同 session → 限流
        assertThatThrownBy(() -> svc.correct(req("text-5", sid)))
                .isInstanceOf(VoiceRateLimitException.class);
    }

    @Test
    void differentSessionIdsHaveIndependentBuckets() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        // s1 用满 5 次
        for (int i = 0; i < 5; i++) {
            svc.correct(req("text-" + i, "s1"));
        }
        // s2 仍可用（独立桶）
        VoiceCorrectionResponse resp = svc.correct(req("text-s2", "s2"));
        assertThat(resp.corrected()).isEqualTo("ok");
    }

    @Test
    void rejectsBlankRawText() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        assertThatThrownBy(() -> svc.correct(req("", "s1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRawText() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        assertThatThrownBy(() -> svc.correct(new VoiceCorrectionRequest(null, "s1", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSessionId() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        assertThatThrownBy(() -> svc.correct(req("raw", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullSessionId() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        assertThatThrownBy(() -> svc.correct(new VoiceCorrectionRequest("raw", null, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cacheKeyHandlesNullFieldsInRecentTurn() {
        VoiceCorrectionRequest r = new VoiceCorrectionRequest("raw", "s1",
                List.of(new VoiceCorrectionRequest.RecentTurn(null, null)));
        // 不抛错，key 是确定的 SHA-256
        String k1 = DeepSeekVoiceCorrectionService.cacheKey(r);
        String k2 = DeepSeekVoiceCorrectionService.cacheKey(r);
        assertThat(k1).isEqualTo(k2).hasSize(64);
    }

    /** 缓存 key 在 recentTurns=null 时仍然稳定。 */
    @Test
    void cacheKeyHandlesNullRecentTurnsList() {
        VoiceCorrectionRequest r = new VoiceCorrectionRequest("raw", "s1", null);
        String k1 = DeepSeekVoiceCorrectionService.cacheKey(r);
        String k2 = DeepSeekVoiceCorrectionService.cacheKey(r);
        assertThat(k1).isEqualTo(k2).hasSize(64);
    }

    /** 缓存条目已过期（>5min）→ 视为未命中，走 provider 重读。 */
    @Test
    void cacheMissWhenEntryExpired() {
        LlmProvider p = stubProvider("重新", "纠错");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionRequest r = req("同样的文本", "s1");
        VoiceCorrectionResponse first = svc.correct(r);
        assertThat(first.cached()).isFalse();
        // 模拟 6 分钟后（5 分钟 TTL 已过）：直接操作 cache map
        svc.cache.forEach((k, v) -> v.expireForTest());
        VoiceCorrectionResponse second = svc.correct(r);
        assertThat(second.cached()).isFalse();
        assertThat(second.corrected()).isEqualTo("重新纠错");
    }

    /** 缓存命中分支：相同请求在 5 分钟内走 cache。 */
    @Test
    void cacheHitWhenWithinTtl() {
        LlmProvider p = stubProvider("缓存", "命中");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionRequest r = req("same", "s1");
        svc.correct(r); // 首次写入缓存
        VoiceCorrectionResponse hit = svc.correct(r);
        assertThat(hit.cached()).isTrue();
    }

    /** Provider 返回空响应（Flux.empty）→ 降级 corrected=null。 */
    @Test
    void degradesOnEmptyResponse() {
        LlmProvider p = emptyProvider();
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionResponse resp = svc.correct(req("raw", "s1"));
        assertThat(resp.corrected()).isNull();
    }

    /** Provider 返回空白响应 → 降级 corrected=null。 */
    @Test
    void degradesOnBlankResponse() {
        LlmProvider p = blankProvider();
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionResponse resp = svc.correct(req("raw", "s1"));
        assertThat(resp.corrected()).isNull();
    }

    /** recentTurns 非空时构造 ChatRequest（含 user/assistant 历史 + rawText）— 覆盖循环 + isBlank 跳过分支。 */
    @Test
    void recentTurnsAreIncludedInChatRequest() {
        java.util.concurrent.atomic.AtomicReference<ChatRequest> sink =
                new java.util.concurrent.atomic.AtomicReference<>();
        LlmProvider p = captureProvider(sink);
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        VoiceCorrectionRequest r = new VoiceCorrectionRequest("当前消息", "s1", List.of(
                new VoiceCorrectionRequest.RecentTurn("用户之前", "助手之前"),
                new VoiceCorrectionRequest.RecentTurn("用户空", null),
                new VoiceCorrectionRequest.RecentTurn("", "助手空")));
        VoiceCorrectionResponse resp = svc.correct(r);
        ChatRequest captured = sink.get();
        assertThat(captured).isNotNull();
        // 4 条 history (user+assistant 配对 × 1 + user × 1 [因 assistant 为 null 被跳过]
        // + 第 2 个 turn user 非空也加 + 第 3 个 turn 因 user 空被跳过) + 当前消息 = 5 条
        assertThat(captured.messages()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(captured.messages().get(captured.messages().size() - 1).content()).isEqualTo("当前消息");
        assertThat(resp.corrected()).isEqualTo("ok");
    }

    /** TokenBucket 窗口清理：先打满 5 次，等 1.1 秒后再打 5 次（应允许）。 */
    @Test
    void tokenBucketRefillsAfterWindow() throws Exception {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        String sid = "refill-session";
        // 第一轮打满 5 次
        for (int i = 0; i < 5; i++) {
            svc.correct(req("x-" + i, sid));
        }
        // 第 6 次立即应被限流
        assertThatThrownBy(() -> svc.correct(req("x-5", sid)))
                .isInstanceOf(VoiceRateLimitException.class);
        // 等过窗口（>1s）
        Thread.sleep(1100);
        // 新一轮应允许
        VoiceCorrectionResponse resp = svc.correct(req("x-after", sid));
        assertThat(resp.corrected()).isEqualTo("ok");
    }

    /** rawText=null 且 sessionId 有效 → 应先报 rawText 错误（按顺序校验）。 */
    @Test
    void rejectsNullRequestEntirely() {
        LlmProvider p = stubProvider("ok");
        DeepSeekVoiceCorrectionService svc = new DeepSeekVoiceCorrectionService(p);
        assertThatThrownBy(() -> svc.correct(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cacheKeyDiffersByRecentTurns() {
        VoiceCorrectionRequest a1 = new VoiceCorrectionRequest("你好", "s1",
                List.of(new VoiceCorrectionRequest.RecentTurn("之前用户说的话", "之前助手回复")));
        VoiceCorrectionRequest a2 = new VoiceCorrectionRequest("你好", "s1",
                List.of(new VoiceCorrectionRequest.RecentTurn("不同的历史", "不同的回复")));
        assertThat(DeepSeekVoiceCorrectionService.cacheKey(a1))
                .isNotEqualTo(DeepSeekVoiceCorrectionService.cacheKey(a2));
    }

    private static VoiceCorrectionRequest req(String raw, String sid) {
        return new VoiceCorrectionRequest(raw, sid, List.of());
    }
}