package com.example.agent.provider;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * LLM 调用重试（详见 design.md §11.2）。
 *
 * <p>由于 Spring Boot 3.2.5 自带 Reactor 3.2.x（无 {@code reactor.util.retry.Retry} 工具类，3.4+ 才有）， 本类手写基于
 * {@code Mono.onErrorResume + Mono.delay + 递归} 的指数退避实现。
 *
 * <p>流式语义（§11.3）：重试只包在"建立连接 + 首个 chunk"阶段的 Mono 上；流中途断开不重试。
 */
public class LlmRetry {
  private static final Logger log = LoggerFactory.getLogger(LlmRetry.class);

  /** 指数退避序列：1s, 2s, 4s, 8s（最多 3 次重试 = 4 次尝试） */
  private static final long[] DEFAULT_BACKOFF_MS = {1000L, 2000L, 4000L, 8000L};

  /** 单次退避上限（防止指数爆炸） */
  private static final long MAX_BACKOFF_MS = 10_000L;

  /** 429 重试最大次数 */
  private static final int MAX_RATE_LIMIT_RETRIES = 5;

  /** 默认瞬时错误最大重试次数 */
  private static final int MAX_TRANSIENT_RETRIES = 3;

  /**
   * 重试策略（聚合 maxAttempts + backoff 序列 + 可重试判定）。
   *
   * @param maxAttempts 最大尝试次数（含首次）
   * @param backoffMs 每次重试前的退避毫秒（超出时回落 {@link #MAX_BACKOFF_MS}）
   * @param predicate 判定异常是否可重试
   */
  public record RetryPolicy(
      int maxAttempts, long[] backoffMs, Predicate<Throwable> predicate) {
    /**
     * 构造策略。
     *
     * @param predicate 判定异常是否可重试
     * @return 使用 {@link #DEFAULT_BACKOFF_MS} 的策略
     */
    public static RetryPolicy of(int maxAttempts, Predicate<Throwable> predicate) {
      return new RetryPolicy(maxAttempts, DEFAULT_BACKOFF_MS, predicate);
    }
  }

  /**
   * 网络错 / 5xx 指数退避，最多 3 次。
   *
   * @param source 原始请求 Mono
   */
  public static <T> Mono<T> retryOnTransient(Mono<T> source) {
    return retry(source, RetryPolicy.of(MAX_TRANSIENT_RETRIES, LlmRetry::isTransientError));
  }

  /** 429 限流按 Retry-After header 退避，最多 5 次。 */
  public static <T> Mono<T> retryOnRateLimit(Mono<T> source) {
    return retry(
        source,
        RetryPolicy.of(
            MAX_RATE_LIMIT_RETRIES,
            e ->
                e instanceof WebClientResponseException wcre
                    && wcre.getStatusCode().value() == 429));
  }

  /**
   * 按自定义策略重试。
   *
   * @param source 原始请求 Mono
   * @param policy 重试策略
   */
  public static <T> Mono<T> retry(Mono<T> source, RetryPolicy policy) {
    return retry(source, policy, 0);
  }

  static <T> Mono<T> retry(Mono<T> source, RetryPolicy policy, int attempt) {
    return source.onErrorResume(
        e -> {
          if (attempt >= policy.maxAttempts() || !policy.predicate().test(e)) {
            return Mono.error(e);
          }
          long delay =
              Math.min(
                  policy.backoffMs()[Math.min(attempt, policy.backoffMs().length - 1)],
                  MAX_BACKOFF_MS);
          log.warn("retrying after {} attempt(s): {}", attempt + 1, e.toString());
          return Mono.delay(Duration.ofMillis(delay))
              .then(retry(source, policy, attempt + 1));
        });
  }

  /**
   * 判断是否为瞬时错误（网络错 / 5xx）。
   *
   * @param e 异常
   * @return true=可重试
   */
  public static boolean isTransientError(Throwable e) {
    if (e instanceof IOException) return true;
    if (e instanceof WebClientRequestException) return true;
    if (e instanceof WebClientResponseException wcre && wcre.getStatusCode().is5xxServerError())
      return true;
    return false;
  }

  /**
   * 从 Retry-After header 解析退避毫秒，解析失败回退到 fallback。
   *
   * @param e 异常
   * @param fallback 解析失败时的兜底值
   * @return 退避毫秒
   */
  public static long parseRetryAfterOr(Throwable e, long fallback) {
    if (e instanceof WebClientResponseException wcre) {
      String header = wcre.getHeaders().getFirst("Retry-After");
      if (header != null) {
        try {
          return Long.parseLong(header) * 1000L;
        } catch (NumberFormatException ignored) {
        }
      }
    }
    return fallback;
  }
}
