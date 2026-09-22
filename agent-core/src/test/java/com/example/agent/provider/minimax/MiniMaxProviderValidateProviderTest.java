package com.example.agent.provider.minimax;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * MiniMaxProvider.validateProviderHook 单测（add-provider-catalog-abstract task 5.4）。
 *
 * <p>与 {@link com.example.agent.provider.deepseek.DeepSeekProviderValidateProviderTest} 同模式。
 */
class MiniMaxProviderValidateProviderTest {

    private final LlmProvider provider = new MiniMaxProvider("test-key");

    @Test
    void validateProviderHookAcceptsNullExtra() {
        ChatRequest req = new ChatRequest(
                "abab6.5s-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096, null);
        assertDoesNotThrow(() -> provider.streamChat(req)
                .take(0).collectList().block());
    }

    @Test
    void validateProviderHookAcceptsExtraWithoutProviderField() {
        ChatRequest req = new ChatRequest(
                "abab6.5s-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096,
                Map.of("reasoning_effort", "high"));
        assertDoesNotThrow(() -> provider.streamChat(req)
                .take(0).collectList().block());
    }

    @Test
    void validateProviderHookAcceptsMatchingProvider() {
        ChatRequest req = new ChatRequest(
                "abab6.5s-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096,
                Map.of("provider", "minimax"));
        assertDoesNotThrow(() -> provider.streamChat(req)
                .take(0).collectList().block());
    }

    @Test
    void validateProviderHookRejectsMismatchedProvider() {
        ChatRequest req = new ChatRequest(
                "abab6.5s-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096,
                Map.of("provider", "anthropic"));
        assertThrows(IllegalArgumentException.class,
                () -> provider.streamChat(req)
                        .take(0).collectList().block());
    }
}
