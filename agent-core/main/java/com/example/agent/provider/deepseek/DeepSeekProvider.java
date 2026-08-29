package com.example.agent.provider.deepseek;

import com.example.agent.provider.openai.OpenAiCompatibleProvider;

/**
 * DeepSeek Provider 实现（OpenAI 兼容协议；详见 design.md §6.1）。
 *
 * <p>v0.1 简化：拿到完整 SSE body 后按行解析（避免依赖分块传输语义）。 v0.2 升级：用 bodyToFlux(DataBuffer) + 流式按行解析，启用真正的流式。
 *
 * <p>所有协议细节（HTTP client / SSE 解析 / 请求体构造）继承自 {@link OpenAiCompatibleProvider}，本类只需声明 5 个常量。
 */
public class DeepSeekProvider extends OpenAiCompatibleProvider {

    /**
     * DeepSeek API base URL
     */
    private static final String BASE_URL = "https://api.deepseek.com";

    /**
     * DeepSeek-chat 上下文窗口（128K tokens）
     */
    private static final int CONTEXT_WINDOW = 128_000;

    /**
     * DeepSeek-chat 最大输出（8192 tokens）
     */
    private static final int MAX_OUTPUT = 8_192;

    /**
     * 生产构造器：使用 DeepSeek 默认 baseUrl。
     *
     * @param apiKey DeepSeek API key
     */
    public DeepSeekProvider(String apiKey) {
        super(apiKey, BASE_URL);
    }

    /**
     * 自定义 baseUrl 构造器（主要用于 E2E 测试 / 本地代理；生产请用 {@link #DeepSeekProvider(String)}）。
     *
     * @param apiKey  API key
     * @param baseUrl 自定义 base URL
     */
    public DeepSeekProvider(String apiKey, String baseUrl) {
        super(apiKey, baseUrl);
    }

    @Override
    public String name() {
        return "deepseek";
    }

    @Override
    protected String baseUrl() {
        return BASE_URL;
    }

    @Override
    public int contextWindow() {
        return CONTEXT_WINDOW;
    }

    @Override
    public int maxOutputTokens() {
        return MAX_OUTPUT;
    }
}
