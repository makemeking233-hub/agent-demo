package com.example.agent.log;

import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.ToolCall;
import com.example.agent.tools.ToolResult;

import java.util.List;

/**
 * 会话日志事件观察者（详见 logging-design.md §4.2）。
 *
 * <p>由 {@code AgentLoop} 在主循环关键节点触发广播；日志实现类（{@link SessionLogger}）按事件类型写往
 * 对应文件。默认 no-op，保证不接日志时主循环零副作用。
 *
 * <p>所有方法必须**快速、不抛异常**（日志故障不得打断对话）；实现内部自行 try/catch。
 */
public interface SessionLogSink {

    /** 什么都不做的默认 sink（{@code AgentLoop} 未接入日志时使用） */
    SessionLogSink NOOP = new SessionLogSink() {};

    /**
     * 单轮对话开始。
     *
     * @param turn 轮次序号（0 起）
     */
    default void onTurnStart(int turn) {}

    /**
     * 用户输入。
     *
     * @param user 用户消息
     */
    default void onUser(Message.User user) {}

    /**
     * 模型回复（含工具调用骨架）。
     *
     * @param assistant 模型消息
     * @param thinking 思考增量（v0.1 恒为空；v0.2 deepseek-reasoner 接入后非空）
     */
    default void onAssistant(Message.Assistant assistant, List<String> thinking) {}

    /**
     * 单个工具调用（拿到完整入参后）。
     *
     * @param call 工具调用描述
     */
    default void onToolCall(ToolCall call) {}

    /**
     * 工具执行结果。
     *
     * @param result 工具结果
     * @param elapsedMs 执行耗时（毫秒）
     */
    default void onToolResult(ToolResult<?> result, long elapsedMs) {}

    /**
     * 单轮对话结束。
     *
     * @param result 该轮结果（含 token 累计）
     */
    default void onTurnEnd(TurnResult result) {}
}
