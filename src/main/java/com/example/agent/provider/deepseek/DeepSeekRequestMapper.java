package com.example.agent.provider.deepseek;

import com.example.agent.provider.ChatRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 请求体构造（详见 design.md §7.1 + §6.1）。
 *
 * <p>职责单一：把 {@link ChatRequest} 序列化为 DeepSeek HTTP body。 解析响应（SSE → StreamChunk）由 {@link
 * DeepSeekResponseParser} 负责。
 *
 * <p>强制规则（design.md §7.1）：每个请求体必须带 {@code stream_options.include_usage=true}， 否则
 * usage.prompt_tokens 永远为 null，压缩触发器失效。
 */
public class DeepSeekRequestMapper {

  /** 强制字段：开启 usage 透传 */
  private static final String STREAM_OPTIONS_KEY = "stream_options";

  /** {@code stream_options.include_usage} 子键名 */
  private static final String INCLUDE_USAGE_KEY = "include_usage";

  /** 构造 DeepSeek chat completion 请求体（OpenAI 兼容协议 + stream_options） */
  public Map<String, Object> toRequestBody(ChatRequest req) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", req.model());
    body.put("stream", true);
    body.put(STREAM_OPTIONS_KEY, Map.of(INCLUDE_USAGE_KEY, true));
    if (req.temperature() != null) body.put("temperature", req.temperature());
    if (req.maxTokens() != null) body.put("max_tokens", req.maxTokens());
    if (req.systemPrompt() != null && !req.systemPrompt().isEmpty()) {
      body.put("messages", mergeSystemPrompt(req));
    } else {
      body.put("messages", toMessageArray(req.messages()));
    }
    if (req.tools() != null && !req.tools().isEmpty()) {
      body.put("tools", req.tools());
      body.put("tool_choice", "auto");
    }
    if (req.extra() != null) body.putAll(req.extra());
    return body;
  }

  /**
   * 合并 system prompt 到 messages 数组头部。
   *
   * @param req 聊天请求（含 systemPrompt + messages）
   * @return OpenAI 格式 messages 数组
   */
  private List<Map<String, Object>> mergeSystemPrompt(ChatRequest req) {
    List<Map<String, Object>> arr = new ArrayList<>();
    arr.add(Map.of("role", "system", "content", req.systemPrompt()));
    arr.addAll(toMessageArray(req.messages()));
    return arr;
  }

  /**
   * 把内部 {@link com.example.agent.agent.Message} 列表转为 OpenAI 格式 Map 数组（含 tool_calls / tool_call_id）。
   *
   * @param messages 内部消息列表
   * @return OpenAI 格式 messages 数组
   */
  private List<Map<String, Object>> toMessageArray(List<com.example.agent.agent.Message> messages) {
    List<Map<String, Object>> arr = new ArrayList<>();
    for (var m : messages) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("role", m.role());
      entry.put("content", m.content());
      if (m instanceof com.example.agent.agent.Message.Assistant a
          && a.toolCalls() != null
          && !a.toolCalls().isEmpty()) {
        List<Map<String, Object>> tcs = new ArrayList<>();
        for (var tc : a.toolCalls()) {
          tcs.add(
              Map.of(
                  "id", tc.id(),
                  "type", "function",
                  "function", Map.of("name", tc.name(), "arguments", tc.argumentsJson())));
        }
        entry.put("tool_calls", tcs);
      }
      if (m instanceof com.example.agent.agent.Message.ToolResult tr) {
        entry.put("tool_call_id", tr.toolCallId());
      }
      arr.add(entry);
    }
    return arr;
  }
}
