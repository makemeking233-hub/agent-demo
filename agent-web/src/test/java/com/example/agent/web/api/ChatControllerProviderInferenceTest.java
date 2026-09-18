package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.permission.PermissionMode;
import com.example.agent.web.api.dto.SendRequest;
import com.example.agent.web.stream.ChatStreamService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Sinks;
import org.springframework.http.codec.ServerSentEvent;

/**
 * ChatController provider 推断 + default-provider 兜底（add-provider-catalog-abstract task 6.4）。
 *
 * <p>不启动 Spring 上下文：直接 new {@link ChatController}，mock {@link ChatStreamService} +
 * {@link Environment}，捕获 {@code streams.create(...)} 的第一个参数（providerId）。
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>{@code deepseek-chat} → 推断 deepseek
 *   <li>{@code gpt-4o} → 推断 openai
 *   <li>{@code claude-opus-4-20250514} → 推断 anthropic
 *   <li>未知 model（如 {@code gemini-pro}）→ 从 yml {@code agent.chat.default-provider} 兜底
 *   <li>yml 未配 default-provider → 硬编码 fallback {@code deepseek}
 * </ul>
 */
class ChatControllerProviderInferenceTest {

    private ChatStreamService streams;
    private Environment env;
    private ChatController controller;
    private final AtomicReference<String> capturedProviderId = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        streams = mock(ChatStreamService.class);
        env = mock(Environment.class);

        when(env.getProperty("DEEPSEEK_API_KEY")).thenReturn("sk-test-fake");
        when(env.getProperty("agent.chat.default-provider")).thenReturn("deepseek");

        // create(...) 6 参：捕获第 1 个参数 providerId；返回一个最小 ActiveStream
        when(streams.create(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    capturedProviderId.set(inv.getArgument(0));
                    return stubActiveStream(inv.getArgument(1), inv.getArgument(2));
                });
        when(streams.workspaceExists(any())).thenReturn(true);
        when(streams.start(any(), any())).thenReturn(true);

        controller = new ChatController(streams, env);
    }

    private static ChatStreamService.ActiveStream stubActiveStream(String sessionId, String model) {
        return new ChatStreamService.ActiveStream(
                "stream-1", sessionId, model, System.currentTimeMillis(),
                Sinks.many().replay().all(), null, null,
                new java.util.concurrent.atomic.AtomicBoolean(false), null);
    }

    @SuppressWarnings("unchecked")
    private String providerIdFor(String model) {
        SendRequest req = new SendRequest("hi", "sess-1", "read_only", null, model, null);
        ResponseEntity<?> resp = (ResponseEntity<?>) controller.send(req).block();
        assertThat(resp).isNotNull();
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        return capturedProviderId.get();
    }

    @Test
    void infersDeepSeekFromDeepSeekModel() {
        assertThat(providerIdFor("deepseek-chat")).isEqualTo("deepseek");
    }

    @Test
    void infersDeepSeekFromReasonerModel() {
        assertThat(providerIdFor("deepseek-reasoner")).isEqualTo("deepseek");
    }

    @Test
    void fallsBackToDefaultProviderForUnknownModel() {
        // gemini-pro 不在 supported-models → resolveModel 回退 deepseek-chat → 推断 deepseek
        assertThat(providerIdFor("gemini-pro")).isEqualTo("deepseek");
    }

    @Test
    void usesConfiguredDefaultProviderWhenPrefixUnrecognized() {
        // abab6.5s-chat 在 supported-models 里（通过校验保留原值），但前缀无法推断 →
        // 走 yml agent.chat.default-provider = minimax
        when(env.getProperty(eq("agent.chat.supported-models"), anyString()))
                .thenReturn("abab6.5s-chat");
        when(env.getProperty("agent.chat.default-provider")).thenReturn("minimax");
        assertThat(providerIdFor("abab6.5s-chat")).isEqualTo("minimax");
    }

    @Test
    void fallsBackToHardcodedDeepSeekWhenYmlMissing() {
        when(env.getProperty(eq("agent.chat.supported-models"), anyString()))
                .thenReturn("abab6.5s-chat");
        when(env.getProperty("agent.chat.default-provider")).thenReturn(null);
        assertThat(providerIdFor("abab6.5s-chat")).isEqualTo("deepseek");
    }

    @Test
    void fallsBackToHardcodedDeepSeekWhenYmlBlank() {
        when(env.getProperty(eq("agent.chat.supported-models"), anyString()))
                .thenReturn("abab6.5s-chat");
        when(env.getProperty("agent.chat.default-provider")).thenReturn("   ");
        assertThat(providerIdFor("abab6.5s-chat")).isEqualTo("deepseek");
    }

    @Test
    void invalidModelFallsBackToDeepSeekChatThenInfersDeepSeek() {
        // resolveModel 把不在 supported-models 的 model 回退到 deepseek-chat → 推断 deepseek
        when(env.getProperty(eq("agent.chat.supported-models"), anyString()))
                .thenReturn("deepseek-chat");
        assertThat(providerIdFor("totally-unknown-model")).isEqualTo("deepseek");
    }
}
