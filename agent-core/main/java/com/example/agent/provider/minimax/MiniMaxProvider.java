package com.example.agent.provider.minimax;

import com.example.agent.provider.openai.OpenAiCompatibleProvider;

/**
 * MiniMax Provider 实现（OpenAI 兼容协议）。
 *
 * <p>使用中国版 endpoint（{@code https://api.minimaxi.com}）；chat endpoint 为 {@code
 * /v1/text/chatcompletion_v2}（与 OpenAI 标准 {@code /v1/chat/completions} 不同的路径）。
 *
 * <p>所有协议细节（HTTP client / SSE 解析 / 请求体构造）继承自 {@link OpenAiCompatibleProvider}。
 *
 * <p>详见 https://platform.minimaxi.com/docs/api-reference/text-chat-openai
 */
public class MiniMaxProvider extends OpenAiCompatibleProvider {

    /**
     * MiniMax 中国版 API base URL
     */
    private static final String BASE_URL = "https://api.minimaxi.com";

    /**
     * MiniMax-Text-01 上下文窗口（128K tokens；官方文档）
     */
    private static final int CONTEXT_WINDOW = 128_000;

    /**
     * MiniMax-Text-01 最大输出（8192 tokens；官方文档）
     */
    private static final int MAX_OUTPUT = 8_192;

    /**
     * MiniMax 特有 chat endpoint（与 OpenAI 标准路径不同）
     */
    private static final String CHAT_ENDPOINT = "/v1/text/chatcompletion_v2";

    /**
     * 生产构造器：使用 MiniMax 中国版默认 baseUrl。
     *
     * @param apiKey MiniMax API key（JWT 格式，从 https://platform.minimaxi.com 获取）
     */
    public MiniMaxProvider(String apiKey) {
        super(apiKey, BASE_URL);
    }

    /**
     * 自定义 baseUrl 构造器（主要用于 E2E 测试 / 本地代理；生产请用 {@link #MiniMaxProvider(String)}）。
     *
     * @param apiKey  API key
     * @param baseUrl 自定义 base URL
     */
    public MiniMaxProvider(String apiKey, String baseUrl) {
        super(apiKey, baseUrl);
    }

    @Override
    public String name() {
        return "minimax";
    }

    @Override
    protected String baseUrl() {
        return BASE_URL;
    }

    @Override
    protected String chatEndpoint() {
        return CHAT_ENDPOINT;
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
