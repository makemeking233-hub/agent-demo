package com.example.agent.provider;

/**
 * 模型返回的工具调用（详见 design.md §6.1）。
 *
 * @param id 工具调用 ID（用于关联 tool_result）
 * @param name 工具名（与 {@link ToolRegistry} 注册的 name 对应）
 * @param argumentsJson 工具参数 JSON 字符串（v0.1 由 Tool 自己解析；v0.2 增加通用 JSON → Input 转换）
 */
public record ToolCall(String id, String name, String argumentsJson) {}
