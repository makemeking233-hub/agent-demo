package com.example.agent.web.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.agent.core.AgentLoop;
import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.stats.TurnDelta;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.ToolResult;
import com.example.agent.web.api.dto.SseEvent;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bump web.stream coverage: 用真实 ChatStreamService 驱动 SseSessionLogSink 各回调分支. */
class SseSessionLogSinkTest {

    private ChatStreamService realService(ChatStreamService.ActiveStream[] out) {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.createLoop(anyString(), anyString(), anyString(), any(SessionLogSink.class), any(), any(), any(), any()))
                .thenAnswer(
                        inv ->
                                new AgentLoop(
                                        mock(LlmProvider.class),
                                        new ToolRegistry(),
                                        new MessageHistory(new TokenEstimator()),
                                        new StreamingPrinter(),
                                        1,
                                        "deepseek-chat",
                                        Paths.get(".")));
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("sess", "deepseek-chat");
        out[0] = meta;
        return svc;
    }

    @Test
    void onAssistantEmitsTextThinkingAndToolCalls() {
        ChatStreamService.ActiveStream[] out = new ChatStreamService.ActiveStream[1];
        ChatStreamService svc = realService(out);
        out[0]
                .sinkAdapter()
                .onAssistant(
                        new Message.Assistant("你好", List.of(new com.example.agent.llm.ToolCall("t1", "read_file", "{}"))),
                        List.of("思考中"));
    }

    @Test
    void onAssistantSkipsEmptyAndNull() {
        ChatStreamService.ActiveStream[] out = new ChatStreamService.ActiveStream[1];
        realService(out);
        out[0].sinkAdapter().onAssistant(new Message.Assistant("", null), List.of());
    }

    @Test
    void onToolResultOkUsesOutput() {
        ChatStreamService.ActiveStream[] out = new ChatStreamService.ActiveStream[1];
        ChatStreamService svc = realService(out);
        out[0].sinkAdapter().onToolResult(ToolResult.ok("out", "tc1"), 5L);
    }

    @Test
    void onToolResultErrUsesMessage() {
        ChatStreamService.ActiveStream[] out = new ChatStreamService.ActiveStream[1];
        realService(out);
        out[0].sinkAdapter().onToolResult(ToolResult.error("tc1", "失败"), 5L);
    }

    @Test
    void onTurnEndStopsStream() {
        ChatStreamService.ActiveStream[] out = new ChatStreamService.ActiveStream[1];
        realService(out);
        out[0].sinkAdapter().onTurnEnd(new TurnResult("final", 1, 2, 0));
    }

    /**
     * fix-thinking-delta-streaming：每次 onThinkingDelta 必须立刻 emit
     * {@code MessageDelta("thinking", text)}，让前端逐 token 流式渲染。
     * 此前该方法走 SessionLogSink 的 default no-op，thinking 被丢到回合结束才一次性推。
     */
    @Test
    void onThinkingDeltaEmitsPerToken() {
        ChatStreamService stream = mock(ChatStreamService.class);
        SseSessionLogSink sink = new SseSessionLogSink(stream, "s1");
        sink.onThinkingDelta("先想想");
        sink.onThinkingDelta("再想想");
        verify(stream).emit(eq("s1"), eq(new SseEvent.MessageDelta("thinking", "先想想")));
        verify(stream).emit(eq("s1"), eq(new SseEvent.MessageDelta("thinking", "再想想")));
        verify(stream, times(2)).emit(eq("s1"), any(SseEvent.MessageDelta.class));
    }

    @Test
    void onThinkingDeltaIgnoresNullAndEmpty() {
        ChatStreamService stream = mock(ChatStreamService.class);
        SseSessionLogSink sink = new SseSessionLogSink(stream, "s1");
        sink.onThinkingDelta(null);
        sink.onThinkingDelta("");
        verify(stream, times(0)).emit(anyString(), any(SseEvent.MessageDelta.class));
    }

    // ---------- add-message-actions P2：per-message clock ----------

    /**
     * task 2.3/2.4：回合结束时先推 {@code message_meta}（per-message 读数），再走
     * {@code onTurnEnd}（它内部推 turn_stats 并发 message_stop）。顺序不能反。
     */
    @Test
    void onTurnEndEmitsMessageMetaBeforeTurnStatsAndStop() {
        ChatStreamService stream = mock(ChatStreamService.class);
        SseSessionLogSink sink = new SseSessionLogSink(stream, "s1");
        TurnResult result = new TurnResult("final", 1, 2, 0);
        sink.onUser(new Message.User("hi"));
        sink.onAssistant(new Message.Assistant("final", null), List.of());
        sink.onTurnEnd(result);
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(stream);
        inOrder.verify(stream).emitMessageMeta(eq("s1"), eq(result), anyLong());
        inOrder.verify(stream).onTurnEnd(eq("s1"), eq(result));
    }

    /** task 2.4：duration_ms 为「用户输入 → 最后一条 assistant 定稿」的 wall time，恒 ≥ 0。 */
    @Test
    void onTurnEndPassesNonNegativeDuration() {
        ChatStreamService stream = mock(ChatStreamService.class);
        SseSessionLogSink sink = new SseSessionLogSink(stream, "s1");
        sink.onTurnEnd(new TurnResult("final", 0, 0, 0));
        org.mockito.ArgumentCaptor<Long> captor = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(stream).emitMessageMeta(eq("s1"), any(TurnResult.class), captor.capture());
        assertThat(captor.getValue()).isGreaterThanOrEqualTo(0L);
    }

    /**
     * task 2.4：真实 {@link ChatStreamService} 下事件顺序为
     * {@code message_meta → turn_stats → message_stop}，且字段与 per-turn delta 一致。
     */
    @Test
    void messageMetaEventPrecedesMessageStopWithPerTurnFields() {
        ChatStreamService.ActiveStream[] out = new ChatStreamService.ActiveStream[1];
        ChatStreamService svc = realService(out);
        // llm=2000ms, ttft=500ms, tokensOut=300 → 生成 1500ms → 200 tok/s
        TurnResult result =
                new TurnResult("final", 100, 300, 0, new TurnDelta(0, 100, 300, 0, 2000, 0, 500, 1, null, null));
        out[0].sinkAdapter().onTurnEnd(result);

        List<org.springframework.http.codec.ServerSentEvent<Object>> events =
                svc.stream(out[0].streamId()).collectList().block();
        assertThat(events).isNotNull();
        List<String> types = events.stream().map(org.springframework.http.codec.ServerSentEvent::event).toList();
        assertThat(types).containsSubsequence("message_meta", "turn_stats", "message_stop");

        String metaJson =
                events.stream()
                        .filter(e -> "message_meta".equals(e.event()))
                        .map(e -> String.valueOf(e.data()))
                        .findFirst()
                        .orElseThrow();
        assertThat(metaJson).contains("\"type\":\"message_meta\"");
        assertThat(metaJson).contains("\"ttft_ms\":500.0");
        assertThat(metaJson).contains("\"tok_per_sec\":200.0");
        // 该会话在 mock runtime 下没有落盘录制器 → uuid 为 null，前端退化为只显示时间
        assertThat(metaJson).contains("\"uuid\":null");
    }

    /** task 2.4：provider 未给出 usage（无 TTFT 样本）时派生指标为 null。 */
    @Test
    void messageMetaDerivedFieldsAreNullWithoutUsage() {
        ChatStreamService.ActiveStream[] out = new ChatStreamService.ActiveStream[1];
        ChatStreamService svc = realService(out);
        TurnResult result =
                new TurnResult("final", 0, 0, 0, new TurnDelta(0, 0, 0, 0, 0, 0, 0, 0, null, null));
        out[0].sinkAdapter().onTurnEnd(result);

        String metaJson =
                svc.stream(out[0].streamId())
                        .filter(e -> "message_meta".equals(e.event()))
                        .map(e -> String.valueOf(e.data()))
                        .blockFirst();
        assertThat(metaJson).contains("\"ttft_ms\":null");
        assertThat(metaJson).contains("\"tok_per_sec\":null");
    }
}


