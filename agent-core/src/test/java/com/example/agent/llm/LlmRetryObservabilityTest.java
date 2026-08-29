package com.example.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.log.SessionLogSink;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LlmRetry 可观测性：每次重试广播 system/retry 事件（attempt 递增）。
 */
class LlmRetryObservabilityTest {

    static final class CapturingSink implements SessionLogSink {
        final List<String> retries = new ArrayList<>();

        @Override
        public void onSystemEvent(String type, Map<String, Object> payload) {
            if ("system/retry".equals(type)) retries.add(String.valueOf(payload));
        }
    }

    @Test
    void eachRetryBroadcastsSystemRetryEvent() {
        CapturingSink sink = new CapturingSink();
        AtomicInteger calls = new AtomicInteger(0);
        Mono<String> source =
                Mono.fromCallable(
                        () -> {
                            if (calls.incrementAndGet() < 3) {
                                throw new java.io.IOException("network flaky");
                            }
                            return "ok";
                        });

        Mono<String> result =
                LlmRetry.retry(
                        source,
                        LlmRetry.RetryPolicy.of(3, LlmRetry::isTransientError),
                        sink);

        StepVerifier.create(result).expectNext("ok").verifyComplete();

        // 第 1、2 次尝试失败 → 2 次重试事件
        assertEquals(2, sink.retries.size());
        assertTrue(sink.retries.get(0).contains("attempt=1"), "首次重试 attempt=1，实际: " + sink.retries.get(0));
        assertTrue(sink.retries.get(1).contains("attempt=2"), "二次重试 attempt=2，实际: " + sink.retries.get(1));
        assertTrue(sink.retries.get(0).contains("errorClass=IOException"));
    }

    @Test
    void noSinkRetryStillWorks() {
        AtomicInteger calls = new AtomicInteger(0);
        Mono<String> source =
                Mono.fromCallable(
                        () -> {
                            if (calls.incrementAndGet() < 2) throw new java.io.IOException("x");
                            return "ok";
                        });
        // 不带 sink 的旧入口不受影响
        StepVerifier.create(LlmRetry.retry(source, LlmRetry.RetryPolicy.of(2, LlmRetry::isTransientError)))
                .expectNext("ok")
                .verifyComplete();
    }
}
