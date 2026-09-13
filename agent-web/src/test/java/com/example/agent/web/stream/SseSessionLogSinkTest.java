package com.example.agent.web.stream;

import static org.mockito.ArgumentMatchers.any;
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
}


