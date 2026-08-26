package com.example.agent.provider;

/** 流结束原因（OpenAI / DeepSeek 兼容枚举）。 */
public enum FinishReason {
  /** 正常完成 */
  STOP,
  /** 模型决定调用工具 */
  TOOL_CALLS,
  /** 达到 max_tokens 上限被截断 */
  LENGTH,
  /** 未知 / 错误 */
  ERROR
}
