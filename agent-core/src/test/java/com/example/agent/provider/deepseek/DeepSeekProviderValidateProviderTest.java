package com.example.agent.provider.deepseek;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * DeepSeekProvider.validateProvider 单测（add-provider-catalog-abstract task 5.4）。
 *
 * <p>与 {@link com.example.agent.provider.anthropic.AnthropicProviderTest} 同模式。
 */
class DeepSeekProviderValidateProviderTest {

    @Test
    void validateProviderAcceptsNullExtra() {
        // 兼容 v0.1 调用方（req.extra 为 null）
        ChatRequest req = new ChatRequest(
                "deepseek-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096, null);
        assertDoesNotThrow(() -> DeepSeekProvider.validateProvider(req));
    }

    @Test
    void validateProviderAcceptsExtraWithoutProviderField() {
        ChatRequest req = new ChatRequest(
                "deepseek-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096,
                Map.of("reasoning_effort", "high"));
        assertDoesNotThrow(() -> DeepSeekProvider.validateProvider(req));
    }

    @Test
    void validateProviderAcceptsMatchingProvider() {
        ChatRequest req = new ChatRequest(
                "deepseek-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096,
                Map.of("provider", "deepseek"));
        assertDoesNotThrow(() -> DeepSeekProvider.validateProvider(req));
    }

    @Test
    void validateProviderRejectsMismatchedProvider() {
        ChatRequest req = new ChatRequest(
                "deepseek-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096,
                Map.of("provider", "anthropic"));
        assertThrows(IllegalArgumentException.class,
                () -> DeepSeekProvider.validateProvider(req));
    }
}
