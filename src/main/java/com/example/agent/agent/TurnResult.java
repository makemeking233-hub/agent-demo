package com.example.agent.agent;

/**
 * 单轮对话结果。AgentLoop.processTurn 返回。
 */
public record TurnResult(
    String finalMessage,
    int totalPromptTokens,
    int totalCompletionTokens,
    int toolCallCount
) {}