package com.example.agent.web.stream;

import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.ToolCall;
import com.example.agent.log.SessionLogSink;
import com.example.agent.tools.ToolResult;
import com.example.agent.web.api.dto.SseEvent;
import java.util.List;

public class SseSessionLogSink implements SessionLogSink {
    private final ChatStreamService stream;
    private final String streamId;

    public SseSessionLogSink(ChatStreamService stream, String streamId) {
        this.stream = stream;
        this.streamId = streamId;
    }

    @Override public void onTurnStart(int turn) {}
    @Override public void onUser(Message.User user) {}

    @Override
    public void onAssistant(Message.Assistant assistant, List<String> thinking) {
        if (assistant.content() != null && !assistant.content().isEmpty()) {
            stream.emit(streamId, new SseEvent.MessageDelta("text", assistant.content()));
        }
        if (thinking != null && !thinking.isEmpty()) {
            stream.emit(streamId, new SseEvent.MessageDelta("thinking", String.join("", thinking)));
        }
        if (assistant.toolCalls() != null) {
            for (ToolCall call : assistant.toolCalls()) {
                stream.emit(streamId, new SseEvent.ToolCallStart(call.id(), call.name(), call.argumentsJson()));
            }
        }
    }

    @Override public void onToolCall(ToolCall call) {}

    @Override
    public void onToolResult(ToolResult<?> result, long elapsedMs) {
        boolean ok = !result.isError();
        Object resultPayload;
        if (ok) {
            resultPayload = result.output();
        } else if (result instanceof ToolResult.Err<?> err) {
            resultPayload = err.message();
        } else {
            resultPayload = "unknown error";
        }
        stream.emit(streamId, new SseEvent.ToolCallEnd(result.toolCallId(), "unknown", ok, resultPayload, elapsedMs));
    }

    @Override
    public void onTurnEnd(TurnResult result) {
        stream.stop(streamId, "stop");
    }
}
