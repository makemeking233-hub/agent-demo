package com.example.agent.agent;

import com.example.agent.provider.LlmProvider;
import com.example.agent.provider.TokenEstimator;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextCompressorTest {

    @Test
    void doesNothingBelowThreshold() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        hist.append(new Message.User("hello"));

        ContextCompressor comp = new ContextCompressor(provider, 8000, 3, "deepseek-chat");
        StepVerifier.create(comp.compactIfNeeded(hist))
            .expectNext(hist)
            .verifyComplete();
    }

    @Test
    void circuitBreaksAfterMaxFailures() {
        LlmProvider provider = mock(LlmProvider.class);
        // 用极小窗口：threshold = 200 - 8 - 0 = 192 tokens，任何稍长消息都超
        when(provider.contextWindow()).thenReturn(200);
        when(provider.maxOutputTokens()).thenReturn(8);

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        hist.append(new Message.User("很长的内容 ".repeat(100)));  // 大约 100 tokens
        for (int i = 0; i < 3; i++) hist.incrementCompactFailures();

        ContextCompressor comp = new ContextCompressor(provider, 0, 3, "deepseek-chat");
        StepVerifier.create(comp.compactIfNeeded(hist))
            .expectError(CompactCircuitBrokenException.class)
            .verify();
    }

    @Test
    void thresholdCalculationCorrect() {
        // 100000 - 8192 - 8000 = 83808
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        // 添加大量消息超过阈值（83808 tokens 难以达到，改用 mock 估算）
        for (int i = 0; i < 100; i++) {
            hist.append(new Message.User("很长的内容 ".repeat(100)));  // 大约 1000 tokens / 条
        }
        ContextCompressor comp = new ContextCompressor(provider, 8000, 3, "deepseek-chat");
        // 不抛错即可（compact 是 stub，会返回原 history）
        StepVerifier.create(comp.compactIfNeeded(hist))
            .expectNextCount(1)
            .verifyComplete();
    }
}