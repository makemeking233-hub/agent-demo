package com.example.agent.provider;

import com.example.agent.agent.Message;

import java.util.List;
import java.util.Map;

/**
 * LLM 聊天请求 DTO（详见 design.md §6.1）。
 *
 * @param model 模型名（如 "deepseek-chat" / "deepseek-reasoner"）
 * @param systemPrompt system prompt 文本；null 表示无 system
 * @param messages 对话历史（user / assistant / tool / system 混合）
 * @param tools 工具 schema 列表（LLM 看到的 JSON Schema）
 * @param temperature 采样温度；null 表示使用 provider 默认
 * @param maxTokens 最大输出 token；null 表示不限制
 * @param extra 扩展字段（透传给 provider body，例如 stream_options）
 */
public record ChatRequest(
    String model,
    String systemPrompt,
    List<Message> messages,
    List<ToolSpec> tools,
    Double temperature,
    Integer maxTokens,
    Map<String, Object> extra
) {}