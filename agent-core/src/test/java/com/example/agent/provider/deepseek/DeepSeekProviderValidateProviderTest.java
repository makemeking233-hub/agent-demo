package com.example.agent.provider.deepseek;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * DeepSeekProvider.validateProviderHook 单测（add-provider-catalog-abstract task 5.4）。
 *
 * <p>通过 LlmProvider.streamChat 入口触发 hook；不通网络,只校验 hook 逻辑
 * （用反射 / 异常断言）。{@link com.example.agent.provider.anthropic.AnthropicProviderTest} 同模式。
 */
class DeepSeekProviderValidateProviderTest {

    private final LlmProvider provider = new DeepSeekProvider("test-key");

    @Test
    void validateProviderHookAcceptsNullExtra() {
        // 兼容 v0.1 调用方（req.extra 为 null）
        ChatRequest req = new ChatRequest(
                "deepseek-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096, null);
        assertDoesNotThrow(() -> provider.streamChat(req)
                // 立即取消；不关心网络响应
                .take(0).collectList().block());
    }

    @Test
    void validateProviderHookAcceptsExtraWithoutProviderField() {
        ChatRequest req = new ChatRequest(
                "deepseek-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096,
                Map.of("reasoning_effort", "high"));
        assertDoesNotThrow(() -> provider.streamChat(req)
                .take(0).collectList().block());
    }

    @Test
    void validateProviderHookAcceptsMatchingProvider() {
        ChatRequest req = new ChatRequest(
                "deepseek-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096,
                Map.of("provider", "deepseek"));
        assertDoesNotThrow(() -> provider.streamChat(req)
                .take(0).collectList().block());
    }

    @Test
    void validateProviderHookRejectsMismatchedProvider() {
        ChatRequest req = new ChatRequest(
                "deepseek-chat", null,
                List.of(new Message.User("hi")), List.of(), 1.0, 4096,
                Map.of("provider", "anthropic"));
        assertThrows(IllegalArgumentException.class,
                () -> provider.streamChat(req)
                        .take(0).collectList().block());
    }
}
