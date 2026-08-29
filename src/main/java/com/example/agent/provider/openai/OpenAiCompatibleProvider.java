package com.example.agent.provider.openai;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * OpenAI 兼容 LLM Provider 抽象基类（DeepSeek / MiniMax / OpenAI / 任何遵循 OpenAI chat-completion 协议的服务）。
 *
 * <p>子类只需实现 5 个 provider-specific 属性：
 *
 * <ul>
 *   <li>{@link #name()}：provider 名（用于日志 / 配置识别）
 *   <li>{@link #baseUrl()}：API base URL
 *   <li>{@link #chatEndpoint()}：chat completion 路径（默认 {@code /v1/chat/completions}）
 *   <li>{@link #contextWindow()}：上下文窗口 token 数
 *   <li>{@link #maxOutputTokens()}：最大输出 token 数
 * </ul>
 *
 * <p>子类通过构造器注入 API key；HTTP 客户端（WebClient）由基类统一构建（自动加 {@code Authorization: Bearer} header）。
 */
public abstract class OpenAiCompatibleProvider implements LlmProvider {

    /**
     * HTTP 客户端（带 Authorization: Bearer header）
     */
    protected final WebClient client;

    /**
     * 共用 OpenAI 协议 mapper（请求体构造 + SSE 解析）
     */
    protected final OpenAiCompatibleMapper mapper;

    /**
     * 构造 OpenAI 兼容 Provider。
     *
     * @param apiKey  API key（Bearer token）
     * @param baseUrl API base URL
     */
    protected OpenAiCompatibleProvider(String apiKey, String baseUrl) {
        this.client =
                WebClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .build();
        this.mapper = new OpenAiCompatibleMapper();
    }

    /**
     * Chat completion endpoint 路径。
     *
     * @return 默认 {@code /v1/chat/completions}（部分 provider 如 MiniMax 覆盖为 {@code
     * /v1/text/chatcompletion_v2}）
     */
    protected String chatEndpoint() {
        return "/v1/chat/completions";
    }

    @Override
    public final Flux<StreamChunk> streamChat(ChatRequest req) {
        var body = mapper.toRequestBody(req);
        return client.post()
                .uri(chatEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(
                        payload ->
                                Flux.fromIterable(
                                        payload.lines()
                                                .map(mapper::parseSseLine)
                                                .filter(Optional::isPresent)
                                                .map(Optional::get)
                                                .toList()));
    }

    /**
     * 抽象：API base URL（子类必填）
     */
    protected abstract String baseUrl();

    /**
     * 抽象：上下文窗口 token 数
     */
    @Override
    public abstract int contextWindow();

    /**
     * 抽象：最大输出 token 数
     */
    @Override
    public abstract int maxOutputTokens();

    /**
     * 抽象：provider 名称
     */
    @Override
    public abstract String name();
}
