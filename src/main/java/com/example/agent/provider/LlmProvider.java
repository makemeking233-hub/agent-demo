package com.example.agent.provider;

import reactor.core.publisher.Flux;

/**
 * LLM Provider 抽象接口（详见 design.md §6.1）。
 *
 * <p>v0.1 唯一实现：DeepSeekProvider（OpenAI 兼容协议）。 v0.2+ 增加 OpenAIProvider / AnthropicProvider。
 */
public interface LlmProvider {
  /** Provider 名称（如 "deepseek" / "openai"） */
  String name();

  /**
   * 流式对话：返回 chunk 序列直到流结束。
   *
   * @param request 完整的对话请求
   * @return SSE 解析后的 chunk Flux
   */
  Flux<StreamChunk> streamChat(ChatRequest request);

  /** 上下文窗口 token 数（用于压缩阈值计算） */
  int contextWindow();

  /** 最大输出 token 数（用于压缩预算） */
  int maxOutputTokens();
}
