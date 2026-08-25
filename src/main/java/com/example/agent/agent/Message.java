package com.example.agent.agent;

import com.example.agent.provider.ToolCall;

import java.util.List;

/**
 * Agent 主循环与 LLM Provider 之间传递的消息类型（sealed interface）。
 *
 * <p>对应 OpenAI/DeepSeek chat completion API 的 role 字段：
 * <ul>
 *   <li>{@link User}       - 用户输入</li>
 *   <li>{@link Assistant}  - 模型回复（含 tool_calls）</li>
 *   <li>{@link ToolResult} - 工具调用结果回流给模型</li>
 *   <li>{@link System}     - system prompt（注入 memory 等）</li>
 * </ul>
 *
 * <p>每个 record 实现 sealed interface 的抽象方法 {@link #role()}（Jackson 反序列化不会注入，详见 design.md §6.4）
 */
public sealed interface Message permits Message.User, Message.Assistant, Message.ToolResult, Message.System {
    String role();
    String content();

    record User(String content) implements Message {
        @Override public String role() { return "user"; }
    }

    record Assistant(String content, List<ToolCall> toolCalls) implements Message {
        @Override public String role() { return "assistant"; }
    }

    record ToolResult(String toolCallId, String content, boolean isError) implements Message {
        @Override public String role() { return "tool"; }
    }

    record System(String content) implements Message {
        @Override public String role() { return "system"; }
    }
}