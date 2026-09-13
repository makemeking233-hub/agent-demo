package com.example.agent.web.stream;

import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.ToolCall;
import com.example.agent.log.SessionLogSink;
import com.example.agent.tools.ToolResult;
import com.example.agent.web.api.dto.SseEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SseSessionLogSink implements SessionLogSink {
    private final ChatStreamService stream;
    private final String streamId;
    // 记录 toolCallId → 工具名：onToolCall 记下 name，onToolResult 回流 ToolCallEnd 时用真实 name（而非硬编码 "unknown"）
    private final Map<String, String> toolNames = new ConcurrentHashMap<>();
    // 本轮模型输出是否已逐 token 推过正文（add-true-streaming）：供 onAssistant 兜底判重
    private final AtomicBoolean textStreamed = new AtomicBoolean(false);

    public SseSessionLogSink(ChatStreamService stream, String streamId) {
        this.stream = stream;
        this.streamId = streamId;
    }

    @Override public void onTurnStart(int turn) {}
    @Override public void onUser(Message.User user) {}

    @Override
    public void onAssistant(Message.Assistant assistant, List<String> thinking) {
        // 工具调用先于文本推送（因果顺序：先调工具，再基于结果说话）
        if (assistant.toolCalls() != null) {
            for (ToolCall call : assistant.toolCalls()) {
                stream.emit(streamId, new SseEvent.ToolCallStart(call.id(), call.name(), call.argumentsJson()));
            }
        }
        // 正文：add-true-streaming 后已由 onTextDelta 逐 token 推过，这里不再整段重发
        // （否则前端会把同一段文本渲染两遍）。只有整轮都没收到增量（非流式 provider / 旧路径）
        // 才在这里兜底发全文。
        boolean alreadyStreamed = textStreamed.getAndSet(false);
        if (!alreadyStreamed && assistant.content() != null && !assistant.content().isEmpty()) {
            stream.emit(streamId, new SseEvent.MessageDelta("text", assistant.content()));
        }
        if (thinking != null && !thinking.isEmpty()) {
            stream.emit(streamId, new SseEvent.MessageDelta("thinking", String.join("", thinking)));
        }
    }

    /**
     * 正文增量逐 token 转发（add-true-streaming）。
     *
     * <p>{@code AgentLoop} 在 {@code collectList()} **之前**按到达顺序回调本方法，因此每个
     * {@code TextDelta} 都会立刻变成一个 SSE {@code message_delta(text)}，前端可逐 token 渲染。
     */
    @Override
    public void onTextDelta(String text) {
        if (text == null || text.isEmpty()) return;
        textStreamed.set(true);
        stream.emit(streamId, new SseEvent.MessageDelta("text", text));
    }

    @Override
    public void onToolCall(ToolCall call) {
        // 记录 toolCallId → name，供 onToolResult 回流 ToolCallEnd 时用真实工具名
        toolNames.put(call.id(), call.name());
    }

    @Override
    public void onToolResult(ToolResult<?> result, long elapsedMs) {
        boolean ok = !result.isError();
        Object resultPayload = result.output();
        if (result instanceof ToolResult.Err<?> err) {
            resultPayload = err.message();
        }
        // 用真实工具名（onToolCall 记录）；找不到再落到占位
        String name = toolNames.getOrDefault(result.toolCallId(), "unknown");
        stream.emit(streamId, new SseEvent.ToolCallEnd(result.toolCallId(), name, ok, resultPayload, elapsedMs));
    }

    @Override
    public void onTurnEnd(TurnResult result) {
        // add-session-stats-bar：先累加并推送 turn_stats，再发 message_stop 关流。
        stream.onTurnEnd(streamId, result);
    }
}
