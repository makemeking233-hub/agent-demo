package com.example.agent.config;

/**
 * 环境变量名常量（详见 design.md §9 API key 优先级）。
 *
 * <p>优先级：环境变量 &gt; {@code ~/.agent-demo/config.yaml} &gt; 内置默认。
 */
public final class EnvKeys {
  /** DeepSeek API key */
  public static final String DEEPSEEK_API_KEY = "DEEPSEEK_API_KEY";

  /** DeepSeek API base URL（自部署时覆盖） */
  public static final String DEEPSEEK_BASE_URL = "DEEPSEEK_BASE_URL";

  /** 覆盖 agent-demo home 目录（E2E 测试用） */
  public static final String AGENT_DEMO_HOME = "AGENT_DEMO_HOME";

  /** 覆盖默认模型（如 deepseek-reasoner） */
  public static final String AGENT_MODEL = "AGENT_MODEL";

  /** 覆盖单轮工具调用上限 */
  public static final String AGENT_MAX_TOOL_ITERATIONS = "AGENT_MAX_TOOL_ITERATIONS";

  /** 覆盖 max output tokens */
  public static final String AGENT_MAX_OUTPUT_TOKENS = "AGENT_MAX_OUTPUT_TOKENS";

  private EnvKeys() {}
}
