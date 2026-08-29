package com.example.agent.core;

/**
 * 单轮对话结果（{@link AgentLoop#processTurn} 返回）。
 *
 * @param finalMessage          拼接后的 Assistant 最终文本（空字符串表示无文本回复）
 * @param totalPromptTokens     累计 prompt token（含工具调用与 tool_result）
 * @param totalCompletionTokens 累计 completion token
 * @param toolCallCount         工具调用总次数
 */
public record TurnResult(
        String finalMessage, int totalPromptTokens, int totalCompletionTokens, int toolCallCount) {
}
