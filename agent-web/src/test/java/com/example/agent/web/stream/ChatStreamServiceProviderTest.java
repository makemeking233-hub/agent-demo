package com.example.agent.web.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.AgentLoop;
import com.example.agent.core.AgentLoopFactory;
import com.example.agent.core.MessageHistory;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.permission.PermissionMode;
import com.example.agent.render.StreamingPrinter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * ChatStreamService 6 参重载 provider / model 透传（add-provider-catalog-abstract task 7.4）。
 *
 * <p>验证：
 *
 * <ul>
 *   <li>6 参重载（providerId 非空）→ AgentLoop.setProviderId + setModel 被调
 *   <li>5 参重载（providerId=null）→ setProviderId 不被调（沿用 AgentLoop 默认）
 *   <li>providerId 为 blank → 同 null 语义
 * </ul>
 */
class ChatStreamServiceProviderTest {

    private static AgentLoop loopWithMockProvider(SessionLogSink sink) {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any(ChatRequest.class)))
                .thenReturn(
                        Flux.just(
                                (StreamChunk) new StreamChunk.TextDelta("ok"),
                                new StreamChunk.Finished(FinishReason.STOP, null)));
        return AgentLoopFactory.buildLoop(
                AgentConfig.defaults(),
                provider,
                AgentLoopFactory.buildTools(AgentConfig.defaults()),
                new MessageHistory(new TokenEstimator()),
                new StreamingPrinter(),
                "deepseek-chat",
                sink,
                null,
                PermissionConfirmer.allowAll());
    }

    private static WebAgentRuntime mockRuntime() {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.sinkFor(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        when(runtime.createLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> loopWithMockProvider(inv.getArgument(3)));
        return runtime;
    }

    @Test
    void sixArgOverloadSetsProviderIdAndModel() {
        ChatStreamService svc = new ChatStreamService(mockRuntime(), new PermissionBridge());
        ChatStreamService.ActiveStream meta =
                svc.create("deepseek", "sess-1", "deepseek-reasoner",
                        PermissionMode.READ_ONLY, null, null);
        assertThat(meta.loop().providerId()).isEqualTo("deepseek");
        assertThat(meta.loop().model()).isEqualTo("deepseek-reasoner");
    }

    @Test
    void sixArgOverloadSetsAnthropicProviderId() {
        ChatStreamService svc = new ChatStreamService(mockRuntime(), new PermissionBridge());
        ChatStreamService.ActiveStream meta =
                svc.create("anthropic", "sess-2", "claude-opus-4-20250514",
                        PermissionMode.READ_ONLY, null, null);
        assertThat(meta.loop().providerId()).isEqualTo("anthropic");
    }

    @Test
    void fiveArgOverloadLeavesProviderIdNull() {
        ChatStreamService svc = new ChatStreamService(mockRuntime(), new PermissionBridge());
        ChatStreamService.ActiveStream meta =
                svc.create("sess-3", "deepseek-chat", PermissionMode.READ_ONLY, null, null);
        assertThat(meta.loop().providerId()).isNull();
        assertThat(meta.loop().model()).isEqualTo("deepseek-chat");
    }

    @Test
    void blankProviderIdIsTreatedAsNull() {
        ChatStreamService svc = new ChatStreamService(mockRuntime(), new PermissionBridge());
        ChatStreamService.ActiveStream meta =
                svc.create("   ", "sess-4", "deepseek-chat", PermissionMode.READ_ONLY, null, null);
        assertThat(meta.loop().providerId()).isNull();
    }

    @Test
    void sixArgOverloadAlsoCarriesReasoningEffort() {
        ChatStreamService svc = new ChatStreamService(mockRuntime(), new PermissionBridge());
        ChatStreamService.ActiveStream meta =
                svc.create("deepseek", "sess-5", "deepseek-reasoner",
                        PermissionMode.READ_ONLY, null, "high");
        assertThat(meta.loop().reasoningEffort()).isEqualTo("high");
    }
}
