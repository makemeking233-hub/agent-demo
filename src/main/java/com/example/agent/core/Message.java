package com.example.agent.core;

import com.example.agent.llm.ToolCall;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 主循环与 LLM Provider 之间传递的消息类型（sealed interface）。
 *
 * <p>对应 OpenAI/DeepSeek chat completion API 的 role 字段：
 *
 * <ul>
 *   <li>{@link User} - 用户输入
 *   <li>{@link Assistant} - 模型回复（含 tool_calls）
 *   <li>{@link ToolResult} - 工具调用结果回流给模型
 *   <li>{@link System} - system prompt（注入 memory 等）
 * </ul>
 *
 * <p>每个 record 实现 sealed interface 的抽象方法 {@link #role()} + {@link #toMap()}（Jackson 反序列化不会注入，详见 design.md §6.4）。
 *
 * <p>{@link #toMap()} 用于 DeepSeek wire format 序列化（OpenAI 兼容）；消除了 DeepSeekRequestMapper 的 instanceof 链。
 */
public sealed interface Message
    permits Message.User, Message.Assistant, Message.ToolResult, Message.System {
  /** 角色名（user / assistant / tool / system） */
  String role();

  /** 消息主内容 */
  String content();

  /**
   * 转 OpenAI 格式 Map（含 role + content + 特有字段如 tool_calls / tool_call_id）。
   *
   * @return DeepSeek wire format 字段映射
   */
  Map<String, Object> toMap();

  /** 用户输入 */
  record User(String content) implements Message {
    @Override
    public String role() {
      return "user";
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("role", role());
      m.put("content", content);
      return m;
    }
  }

  /** 模型回复（含可选 tool_calls） */
  record Assistant(String content, List<ToolCall> toolCalls) implements Message {
    @Override
    public String role() {
      return "assistant";
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("role", role());
      m.put("content", content);
      if (toolCalls != null && !toolCalls.isEmpty()) {
        java.util.ArrayList<Map<String, Object>> tcs = new java.util.ArrayList<>();
        for (ToolCall tc : toolCalls) {
          tcs.add(
              Map.of(
                  "id", tc.id(),
                  "type", "function",
                  "function",
                      Map.of("name", tc.name(), "arguments", tc.argumentsJson())));
        }
        m.put("tool_calls", tcs);
      }
      return m;
    }
  }

  /** 工具调用结果回流给模型（关联 toolCallId） */
  record ToolResult(String toolCallId, String content, boolean isError) implements Message {
    @Override
    public String role() {
      return "tool";
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("role", role());
      m.put("content", content);
      m.put("tool_call_id", toolCallId);
      return m;
    }
  }

  /** system prompt（注入 memory、行为约束等） */
  record System(String content) implements Message {
    @Override
    public String role() {
      return "system";
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("role", role());
      m.put("content", content);
      return m;
    }
  }
}
