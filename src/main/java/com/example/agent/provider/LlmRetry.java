package com.example.agent.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Predicate;

/**
 * LLM 调用重试（详见 design.md §11.2）。
 *
 * <p>由于 Spring Boot 3.2.5 自带 Reactor 3.2.x（无 {@code reactor.util.retry.Retry} 工具类，3.4+ 才有），
 * 本类手写基于 {@code Mono.onErrorResume + Mono.delay + 递归} 的指数退避实现。
 *
 * <p>流式语义（§11.3）：重试只包在"建立连接 + 首个 chunk"阶段的 Mono 上；流中途断开不重试。
 */
public class LlmRetry {
    private static final Logger log = LoggerFactory.getLogger(LlmRetry.class);

    private static final long[] DEFAULT_BACKOFF_MS = {1000L, 2000L, 4000L, 8000L};
    private static final long MAX_BACKOFF_MS = 10_000L;

    /** 网络错 / 5xx 指数退避，最多 3 次 */
    public static <T> Mono<T> retryOnTransient(Mono<T> source) {
        return retry(source, 3, LlmRetry::isTransientError);
    }

    /** 429 限流按 Retry-After header 退避，最多 5 次 */
    public static <T> Mono<T> retryOnRateLimit(Mono<T> source) {
        return retry(source, 5, e -> e instanceof WebClientResponseException wcre
            && wcre.getStatusCode().value() == 429);
    }

    public static <T> Mono<T> retry(Mono<T> source, int maxAttempts, Predicate<Throwable> predicate) {
        return retry(source, maxAttempts, predicate, DEFAULT_BACKOFF_MS, 0);
    }

    static <T> Mono<T> retry(Mono<T> source, int maxAttempts, Predicate<Throwable> predicate,
                              long[] backoffMs, int attempt) {
        return source.onErrorResume(e -> {
            if (attempt >= maxAttempts || !predicate.test(e)) {
                return Mono.error(e);
            }
            long delay = Math.min(backoffMs[Math.min(attempt, backoffMs.length - 1)], MAX_BACKOFF_MS);
            log.warn("retrying after {} attempt(s): {}", attempt + 1, e.toString());
            return Mono.delay(Duration.ofMillis(delay))
                .then(retry(source, maxAttempts, predicate, backoffMs, attempt + 1));
        });
    }

    public static boolean isTransientError(Throwable e) {
        if (e instanceof IOException) return true;
        if (e instanceof WebClientRequestException) return true;
        if (e instanceof WebClientResponseException wcre && wcre.getStatusCode().is5xxServerError()) return true;
        return false;
    }

    public static long parseRetryAfterOr(Throwable e, long fallback) {
        if (e instanceof WebClientResponseException wcre) {
            String header = wcre.getHeaders().getFirst("Retry-After");
            if (header != null) {
                try { return Long.parseLong(header) * 1000L; }
                catch (NumberFormatException ignored) {}
            }
        }
        return fallback;
    }
}