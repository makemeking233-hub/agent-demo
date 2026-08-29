package com.example.agent.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.ContextSnapshot;
import com.example.agent.log.SessionLogSink;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.ToolRegistry;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AgentLoop 可观测性埋点测试：system/error、context/snapshot 等事件在对应路径被广播。
 */
class AgentLoopObservabilityTest {

    /** 捕获 system 事件与 context 快照的 fake sink */
    static final class CapturingSink implements SessionLogSink {
        final List<String> systemEvents = new ArrayList<>();
        final List<ContextSnapshot> snapshots = new ArrayList<>();

        @Override
        public void onSystemEvent(String type, Map<String, Object> payload) {
            systemEvents.add(type + ":" + payload.get("errorClass"));
        }

        @Override
        public void onContextSnapshot(ContextSnapshot snapshot) {
            snapshots.add(snapshot);
        }
    }

    @Test
    void turnBroadcastsContextSnapshot() {
        CapturingSink sink = new CapturingSink();
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any()))
                .thenReturn(
                        Flux.just(
                                new StreamChunk.TextDelta("你好"),
                                new StreamChunk.Finished(
                                        FinishReason.STOP, new StreamChunk.Usage(5, 8))));

        ToolRegistry tools = new ToolRegistry();
        tools.register(new com.example.agent.tools.file.ReadFileTool());

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
                        "system prompt",
                        sink,
                        null);

        loop.processTurn(new Message.User("hi")).block();

        assertEquals(1, sink.snapshots.size(), "每轮应广播一次 context/snapshot");
        ContextSnapshot s = sink.snapshots.get(0);
        assertEquals(1, s.messageCount(), "快照时 history 只有刚追加的 user 消息");
        assertEquals("system prompt", s.systemPrompt());
        assertTrue(s.toolNames().contains("ReadFile"), "toolNames 应含已注册工具，实际: " + s.toolNames());
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
