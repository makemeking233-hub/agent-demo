package com.example.agent.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.ToolRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** AgentLoop 工厂（add-web-ui-v0-1 / D2）：CLI 与 web 共用同一装配逻辑。 */
class AgentLoopFactoryTest {

    @Test
    void buildProviderRoutesDeepseek() {
        AgentConfig cfg = AgentConfig.defaults(); // type=deepseek
        assertThat(AgentLoopFactory.buildProvider(cfg, "sk-test"))
                .isInstanceOf(com.example.agent.provider.deepseek.DeepSeekProvider.class);
    }

    @Test
    void buildProviderRejectsUnknown() {
        AgentConfig cfg =
                new AgentConfig(
                        new AgentConfig.Provider("unknown", "", "", "", 8192),
                        AgentConfig.defaults().permission(),
                        AgentConfig.defaults().cost(),
                        AgentConfig.defaults().context(),
                        AgentConfig.defaults().shell(),
                        List.of(),
                        AgentConfig.defaults().logging());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> AgentLoopFactory.buildProvider(cfg, "sk-test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知 provider");
    }

    @Test
    void buildToolsRegistersExpectedTools() {
        ToolRegistry tools = AgentLoopFactory.buildTools(AgentConfig.defaults());
        assertThat(tools.getRaw("Shell")).isNotNull();
        assertThat(tools.getRaw("Ls")).isNotNull();
        assertThat(tools.list().size()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void buildLoopRunsTurnAndNotifiesSink() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any(ChatRequest.class)))
                .thenReturn(
                        Flux.just(
                                (StreamChunk) new StreamChunk.TextDelta("你好"),
                                new StreamChunk.TextDelta("，世界"),
                                new StreamChunk.Finished(FinishReason.STOP, null)));

        AtomicReference<String> captured = new AtomicReference<>();
        SessionLogSink sink =
                new SessionLogSink() {
                    @Override
                    public void onAssistant(Message.Assistant assistant, List<String> thinking) {
                        captured.set(assistant.content());
                    }
                };

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                AgentLoopFactory.buildLoop(
                        AgentConfig.defaults(),
                        provider,
                        AgentLoopFactory.buildTools(AgentConfig.defaults()),
                        hist,
                        new StreamingPrinter(),
                        "deepseek-chat",
                        sink,
                        null,
                        PermissionConfirmer.allowAll());

        StepVerifier.create(loop.processTurn(new Message.User("hi")))
                .expectNextMatches(r -> "你好，世界".equals(r.finalMessage()))
                .verifyComplete();

        assertThat(captured.get()).isEqualTo("你好，世界");
    }
}
