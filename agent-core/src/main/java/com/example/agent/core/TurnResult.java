package com.example.agent.core;

import com.example.agent.stats.TurnDelta;

/**
 * 单轮对话结果（{@link AgentLoop#processTurn} 返回）。
 *
 * @param finalMessage          拼接后的 Assistant 最终文本（空字符串表示无文本回复）
 * @param totalPromptTokens     累计 prompt token（含工具调用与 tool_result）
 * @param totalCompletionTokens 累计 completion token
 * @param toolCallCount         工具调用总次数
 * @param delta                 本轮统计增量（add-session-stats-bar；耗时 / TTFT / 缓存等）
 */
public record TurnResult(
        String finalMessage,
        int totalPromptTokens,
        int totalCompletionTokens,
        int toolCallCount,
        TurnDelta delta) {

    /** 4 参便捷构造：统计增量为空（向后兼容既有调用方）。 */
    public TurnResult(
            String finalMessage, int totalPromptTokens, int totalCompletionTokens, int toolCallCount) {
        this(finalMessage, totalPromptTokens, totalCompletionTokens, toolCallCount, TurnDelta.empty());
    }
}
