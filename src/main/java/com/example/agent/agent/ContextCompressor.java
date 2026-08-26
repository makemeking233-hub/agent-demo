package com.example.agent.agent;

import com.example.agent.provider.ChatRequest;
import com.example.agent.provider.LlmProvider;
import com.example.agent.provider.StreamChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 上下文压缩器（详见 design.md §8）。
 *
 * <p>触发：{@code hist.estimateTokens() >= threshold}（threshold = contextWindow - maxOutput - buffer）。
 *
 * <p>熔断：连续失败 {@code maxConsecutiveFailures} 次后抛 {@link CompactCircuitBrokenException}。
 *
 * <p>v0.1 简化：compact 返回 summary 文本并写入 history 头部（不真做消息坍缩；M4.2 是真坍缩）。
 * PTL fallback 在 v0.1 不实现。
 */
public class ContextCompressor {
    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);

    /** summary 请求 temperature（低温度保证稳定摘要，详见 design.md §8.2） */
    private static final double SUMMARY_TEMPERATURE = 0.3;
    /** summary 输出 token 上限（与 summarize.txt 第 5 条约束一致） */
    private static final int SUMMARY_MAX_TOKENS = 2000;
    /** summary 失败的熔断阈值 */
    private static final int DEFAULT_MAX_FAILURES = 3;

    private final LlmProvider provider;
    private final int autoCompactBuffer;
    private final int maxConsecutiveFailures;
    private final String summaryModel;

    public ContextCompressor(LlmProvider provider, int autoCompactBuffer,
                             int maxConsecutiveFailures, String summaryModel) {
        this.provider = provider;
        this.autoCompactBuffer = autoCompactBuffer;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.summaryModel = summaryModel;
    }

    public Mono<MessageHistory> compactIfNeeded(MessageHistory hist) {
        int threshold = provider.contextWindow() - provider.maxOutputTokens() - autoCompactBuffer;
        if (hist.estimateTokens() < threshold) return Mono.just(hist);
        if (hist.consecutiveCompactFailures() >= maxConsecutiveFailures) {
            log.warn("compact circuit-broken after {} failures", hist.consecutiveCompactFailures());
            return Mono.error(new CompactCircuitBrokenException());
        }
        return compact(hist)
            .doOnSuccess(r -> hist.resetCompactFailures())
            .doOnError(e -> { hist.incrementCompactFailures(); log.warn("compact failed", e); })
            .onErrorResume(this::ptlFallback);
    }

    /** 真坍缩：调 summary 模型，坍缩旧消息，重注入最近文件，保留 system + 最近 3 轮。 */
    private Mono<MessageHistory> compact(MessageHistory hist) {
        return requestSummary(hist)
            .map(summary -> collapseMessages(hist, summary))
            .doOnNext(compacted -> {
                compacted.prependSystemBoundaryMessage(summaryFrom(compacted));
                compacted.reinjectRecentFileContents(200);
            });
    }

    private Mono<String> requestSummary(MessageHistory hist) {
        String prompt = loadPrompt().replace("[消息历史 JSONL]", serializeHistory(hist));
        ChatRequest req = new ChatRequest(
            summaryModel, prompt, List.of(), List.of(),
            SUMMARY_TEMPERATURE, SUMMARY_MAX_TOKENS,
            Map.of("stream_options", Map.of("include_usage", true)));

        return provider.streamChat(req)
            .filter(c -> c instanceof StreamChunk.TextDelta)
            .map(c -> ((StreamChunk.TextDelta) c).text())
            .collectList()
            .map(parts -> String.join("", parts));
    }

    private String loadPrompt() {
        try (var in = getClass().getResourceAsStream("/prompts/summarize.txt")) {
            if (in == null) return "请将以下对话压缩为摘要：\n\n[消息历史 JSONL]";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("summary prompt 加载失败，使用 fallback", e);
            return "请将以下对话压缩为摘要：\n\n[消息历史 JSONL]";
        }
    }

    private String serializeHistory(MessageHistory hist) {
        StringBuilder sb = new StringBuilder();
        for (var m : hist.all()) {
            sb.append("[").append(m.role()).append("] ")
              .append(m.content() == null ? "" : m.content())
              .append("\n");
        }
        return sb.toString();
    }

    private MessageHistory collapseMessages(MessageHistory hist, String summary) {
        var all = hist.all();
        if (all.size() <= 6) return hist;  // 不够多，不压缩
        var system = all.stream().filter(m -> m instanceof Message.System).toList();
        int recentStart = Math.max(0, all.size() - 6);
        var recent = all.subList(recentStart, all.size());

        List<Message> collapsed = new ArrayList<>();
        collapsed.add(new Message.System("[SUMMARY]\n" + summary));
        collapsed.addAll(system);
        for (var m : recent) {
            if (m instanceof Message.Assistant a && a.toolCalls() != null && !a.toolCalls().isEmpty()) {
                collapsed.add(new Message.Assistant("- **做了什么**: " + a.content(), a.toolCalls()));
            } else if (m instanceof Message.ToolResult tr) {
                collapsed.add(new Message.Assistant("- **结果**: " + tr.content(), null));
            } else {
                collapsed.add(m);
            }
        }

        MessageHistory newHist = new MessageHistory(new com.example.agent.provider.TokenEstimator());
        collapsed.forEach(newHist::append);
        return newHist;
    }

    private String summaryFrom(MessageHistory hist) {
        // 取第一条 system 作为 summary 内容
        Optional<Message> first = hist.all().stream()
            .filter(m -> m instanceof Message.System s && s.content().startsWith("[SUMMARY]"))
            .findFirst();
        return first.map(m -> ((Message.System) m).content()).orElse("");
    }

    private Mono<MessageHistory> ptlFallback(Throwable e) {
        log.warn("PTL fallback not implemented in v0.1: {}", e.toString());
        return Mono.error(e);
    }
}