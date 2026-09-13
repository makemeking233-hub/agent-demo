package com.example.agent.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 工具调用收口（improve-failure-observability）。
 *
 * <p>验证「有 {@code TOOL>} 必须有 {@code TOOL<}」：某轮在工具执行途中被打断（取消订阅 / 连接被撕 /
 * Error 逃出响应式链）时，turn 边界必须补出结果，使
 *
 * <ul>
 *   <li>{@code tools.log} 闭合（{@code TOOL<} 由 {@code sink.onToolResult} 产生）；
 *   <li>history 不留「有 tool_calls 无 tool_result」的悬挂状态（那会让该会话此后每轮被上游 400）。
 * </ul>
 */
class ToolCallClosureTest {

    /** 记录 sink 回调的测试 sink。 */
    private static final class RecordingSink implements SessionLogSink {
        final List<String> events = new CopyOnWriteArrayList<>();
        final AtomicLong toolResults = new AtomicLong();

        @Override
        public void onToolResult(ToolResult<?> result, long elapsedMs) {
            toolResults.incrementAndGet();
            events.add("onToolResult:" + result.toolCallId());
        }

        @Override
        public void onSystemEvent(String type, Map<String, Object> payload) {
            events.add("onSystemEvent:" + type);
        }

        @Override
        public void onAssistant(Message.Assistant assistant, List<String> thinking) {
            events.add("onAssistant");
        }
    }

    /** 工具永不返回结果（模拟执行途中被打断），被调用时放行 latch。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Tool neverReturningTool(CountDownLatch invoked) {
        return new Tool() {
            @Override
            public String name() {
                return "fake";
            }

            @Override
            public String description() {
                return "never returns";
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of();
            }

            @Override
            public Object parseArguments(String argumentsJson) {
                return "{}";
            }

            @Override
            public Mono<ToolResult<Object>> execute(Object input, ToolContext ctx) {
                invoked.countDown();
                return Mono.never();
            }

            @Override
            public String renderUse(Object input) {
                return "fake()";
            }

            @Override
            public String renderResult(Object output) {
                return String.valueOf(output);
            }
        };
    }

    private static LlmProvider providerAskingForFakeTool() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any(ChatRequest.class)))
                .thenReturn(
                        Flux.just(
                                (StreamChunk)
                                        new StreamChunk.ToolCallStart("c1", "fake", "{\"path\":\"x\"}"),
                                new StreamChunk.Finished(FinishReason.TOOL_CALLS, null)));
        return provider;
    }

    /** 装配 AgentLoop：用指定 sink 与永不返回的假工具（工具被调用时放行 {@code invoked}）。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AgentLoop loopWithSink(
            SessionLogSink sink, MessageHistory history, CountDownLatch invoked) {
        ToolRegistry tools = mock(ToolRegistry.class);
        doReturn(neverReturningTool(invoked)).when(tools).getRaw("fake");
        when(tools.list()).thenReturn(List.of());
        // 用 buildLoop 以便注入 allowAll confirmer：裸构造器没有 confirmer，ASK 会 fail-closed
        // 直接拒掉，工具根本不会被执行
        return AgentLoopFactory.buildLoop(
                com.example.agent.config.AgentConfig.defaults(),
                providerAskingForFakeTool(),
                tools,
                history,
                new StreamingPrinter(),
                "deepseek-chat",
                sink,
                null,
                com.example.agent.permission.PermissionConfirmer.allowAll());
    }

    @Test
    void cancellingMidToolCallClosesTheCallAndHistory() throws Exception {
        CountDownLatch invoked = new CountDownLatch(1);
        RecordingSink sink = new RecordingSink();
        MessageHistory history = new MessageHistory(new TokenEstimator());
        AgentLoop loop = loopWithSink(sink, history, invoked);

        Disposable sub = loop.processTurn(new Message.User("hi")).subscribe();
        try {
            assertThat(invoked.await(10, TimeUnit.SECONDS)).as("工具应已开始执行").isTrue();

            // 中途取消（等价于客户端断开 / 连接被撕）
            sub.dispose();

            boolean hasErrorResult =
                    history.all().stream()
                            .anyMatch(
                                    m ->
                                            m instanceof Message.ToolResult t
                                                    && "c1".equals(t.toolCallId())
                                                    && t.isError());
            assertThat(hasErrorResult).as("中断后必须补出该 tool_call 的错误结果").isTrue();
            assertThat(ToolCallPairing.danglingCallIds(history.all())).isEmpty();
            assertThat(sink.toolResults.get())
                    .as("sink 必须收到 onToolResult（tools.log 闭合）")
                    .isEqualTo(1);
        } finally {
            if (!sub.isDisposed()) sub.dispose();
        }
    }

    @Test
    void closePendingToolCallsIsIdempotent() throws Exception {
        CountDownLatch invoked = new CountDownLatch(1);
        RecordingSink sink = new RecordingSink();
        MessageHistory history = new MessageHistory(new TokenEstimator());
        AgentLoop loop = loopWithSink(sink, history, invoked);

        Disposable sub = loop.processTurn(new Message.User("hi")).subscribe();
        try {
            assertThat(invoked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(loop.closePendingToolCalls("test")).isEqualTo(1);
            assertThat(loop.closePendingToolCalls("test")).as("幂等").isZero();
            assertThat(sink.toolResults.get()).isEqualTo(1);
        } finally {
            sub.dispose();
        }
    }
}
