package com.example.agent.agent;

import com.example.agent.provider.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 上下文压缩器（详见 design.md §8）。
 *
 * <p>触发条件：{@code hist.estimateTokens() >= threshold}（threshold = contextWindow - maxOutput - buffer）。
 *
 * <p>熔断：连续失败 {@code maxConsecutiveFailures} 次后立即抛 {@link CompactCircuitBrokenException}，
 * 不重试（避免死循环）。计数器挂在 {@link MessageHistory} 上，每会话独立。
 *
 * <p>PTL fallback：compact 请求本身超限（context_too_long）→ 剥 20% 旧消息重试 summary。
 */
public class ContextCompressor {
    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);

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

    /** M4 Task 4.2 实现 summary prompt + 消息坍缩 + Post-Compact 重注入 */
    private Mono<MessageHistory> compact(MessageHistory hist) {
        return Mono.fromCallable(() -> hist);
    }

    private Mono<MessageHistory> ptlFallback(Throwable e) {
        // v0.1 简化：PTL 直接抛错，让用户 /clear
        log.warn("PTL fallback not implemented in v0.1: {}", e.toString());
        return Mono.error(e);
    }
}