package com.example.agent.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.ToolRegistry;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AgentLoop 可观测性埋点测试：system/error 等事件在错误路径被广播。
 */
class AgentLoopObservabilityTest {

    /** 捕获 system 事件的 fake sink */
    static final class CapturingSink implements SessionLogSink {
        final List<String> systemEvents = new ArrayList<>();

        @Override
        public void onSystemEvent(String type, Map<String, Object> payload) {
            systemEvents.add(type + ":" + payload.get("errorClass"));
        }
    }

    @Test
    void turnErrorBroadcastsSystemErrorEvent() {
        CapturingSink sink = new CapturingSink();
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any())).thenReturn(Flux.error(new RuntimeException("boom")));

        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.list()).thenReturn(List.of());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        3,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."),
                        null,
                        sink,
                        null);

        assertThrows(
                RuntimeException.class, () -> loop.processTurn(new Message.User("hi")).block());
        assertTrue(
                sink.systemEvents.contains("system/error:RuntimeException"),
                "回合异常应广播 system/error 事件，实际: " + sink.systemEvents);
    }

    @Test
    void noopSinkKeepsErrorPathWorking() {
        // 不接 sink（默认 NOOP）时，错误路径不抛额外异常
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any())).thenReturn(Flux.error(new IllegalStateException("x")));

        ToolRegistry tools = mock(ToolRegistry.class);
        when(tools.list()).thenReturn(List.of());

        MessageHistory hist = new MessageHistory(new TokenEstimator());
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        hist,
                        new StreamingPrinter(),
                        3,
                        "deepseek-chat",
                        java.nio.file.Paths.get("."));

        assertThrows(
                IllegalStateException.class, () -> loop.processTurn(new Message.User("hi")).block());
    }
}
