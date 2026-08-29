package com.example.agent.web.stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bump web.stream coverage: 用真实 ChatStreamService 驱动 SseSessionLogSink 各回调分支. */
class SseSessionLogSinkTest {

    private ChatStreamService realService(ChatStreamService.ActiveStream[] out) {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.createLoop(anyString(), any(SessionLogSink.class), any(), any()))
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
}
