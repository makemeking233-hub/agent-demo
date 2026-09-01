package com.example.agent.log;

import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.ToolCall;
import com.example.agent.tools.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * 复合会话日志观察者（v0.3 web 会话重进恢复用）。
 *
 * <p>把 {@code AgentLoop} 广播的同一组事件按序转发给一组 {@link SessionLogSink}。典型用途：
 * web 场景下把一个会话同时（1）经 {@link com.example.agent.web.stream.SseSessionLogSink} 转 SSE，又
 * （2）经 {@link SessionRecorder} 落盘到 {@code ~/.agent-demo/sessions/<id>.jsonl}。
 *
 * <p>所有方法必须快速、不抛异常（日志故障不得打断对话）；子 sink 自身负责吞掉内部异常。
 *
 * <p>空列表等价于 {@link SessionLogSink#NOOP}。
 */
public final class CompositeSessionLogSink implements SessionLogSink {

    /** 有序子 sink 列表（不可变）。 */
    private final List<SessionLogSink> delegates;

    /**
     * 构造复合 sink。
     *
     * @param delegates 有序子 sink 列表（可空，视为空列表）
     */
    public CompositeSessionLogSink(List<SessionLogSink> delegates) {
        this.delegates = delegates == null ? List.of() : List.copyOf(delegates);
    }

    /**
     * 构造复合 sink（便捷重载：按顺序拼接）。
     *
     * @param first 第一个子 sink（可空）
     * @param rest  其余子 sink（可为空）
     */
    public CompositeSessionLogSink(SessionLogSink first, SessionLogSink... rest) {
        List<SessionLogSink> list = new java.util.ArrayList<>();
        if (first != null) list.add(first);
        if (rest != null) {
            for (SessionLogSink s : rest) {
                if (s != null) list.add(s);
            }
        }
        this.delegates = List.copyOf(list);
    }

    @Override
    public void onTurnStart(int turn) {
        fold(s -> s.onTurnStart(turn));
    }

    @Override
    public void onUser(Message.User user) {
        fold(s -> s.onUser(user));
    }

    @Override
    public void onAssistant(Message.Assistant assistant, List<String> thinking) {
        fold(s -> s.onAssistant(assistant, thinking));
    }

    @Override
    public void onToolCall(ToolCall call) {
        fold(s -> s.onToolCall(call));
    }

    @Override
    public void onToolResult(ToolResult<?> result, long elapsedMs) {
        fold(s -> s.onToolResult(result, elapsedMs));
    }

    @Override
    public void onTurnEnd(TurnResult result) {
        fold(s -> s.onTurnEnd(result));
    }

    @Override
    public void onContextSnapshot(ContextSnapshot snapshot) {
        fold(s -> s.onContextSnapshot(snapshot));
    }

    @Override
    public void onSystemEvent(String type, Map<String, Object> payload) {
        fold(s -> s.onSystemEvent(type, payload));
    }

    @Override
    public void onPermissionDecision(Map<String, Object> payload) {
        fold(s -> s.onPermissionDecision(payload));
    }

    /** 把一次回调派发给所有非空子 sink；单个子 sink 抛异常不影响其余。 */
    private void fold(java.util.function.Consumer<SessionLogSink> op) {
        for (SessionLogSink sink : delegates) {
            try {
                op.accept(sink);
            } catch (Exception ignored) {
                // 子 sink 故障不打断对话；忽略
            }
        }
    }
}
