package com.example.agent.provider.openai;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
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

  private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    /**
     * 响应体最大内存缓冲（字节）。
     *
     * <p>v0.1 用 {@code bodyToMono(String.class)} 一次性缓冲完整 SSE 响应后按行解析；WebClient 默认
     * 256KB 上限会在长响应（长文档 / 大工具参数）时触发 {@code DataBufferLimitException}，这里放大到 16MB。
     */
    private static final int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024;

    /**
     * 默认整体请求-响应超时（首 token TTFT 上限）。reasoner 长思考留余量；可被新重载构造覆盖。
     */
    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(60);

    /**
     * 默认 TCP 连接超时（三次握手）。
     */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * HTTP 客户端（带 Authorization: Bearer header + 显式超时）
     */
    protected final WebClient client;

    /**
     * 共用 OpenAI 协议 mapper（请求体构造 + SSE 解析）
     */
    protected final OpenAiCompatibleMapper mapper;

    /**
     * 构造 OpenAI 兼容 Provider（使用默认超时：responseTimeout=60s, connectTimeout=10s）。
     *
     * @param apiKey  API key（Bearer token）
     * @param baseUrl API base URL
     */
    protected OpenAiCompatibleProvider(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, DEFAULT_RESPONSE_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
    }

    /**
     * 构造 OpenAI 兼容 Provider（显式超时）。
     *
     * @param apiKey          API key（Bearer token）
     * @param baseUrl         API base URL
     * @param responseTimeout 整体请求-响应超时（{@code null} 则用默认 60s）
     * @param connectTimeout  TCP 连接超时（{@code null} 则用默认 10s）
     */
    protected OpenAiCompatibleProvider(
            String apiKey, String baseUrl, Duration responseTimeout, Duration connectTimeout) {
        HttpClient httpClient =
                HttpClient.create()
                        .responseTimeout(
                                responseTimeout != null
                                        ? responseTimeout
                                        : DEFAULT_RESPONSE_TIMEOUT)
                        .option(
                                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                (int) (connectTimeout != null
                                        ? connectTimeout.toMillis()
                                        : DEFAULT_CONNECT_TIMEOUT.toMillis()));
        this.client =
                WebClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .clientConnector(new ReactorClientHttpConnector(httpClient))
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
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
                // 4xx/5xx：把错误体转为 "[HTTP xxx] {body}" 字符串，让后续 parseSseLine 失败但内容可见
                .onErrorResume(
                        org.springframework.web.reactive.function.client.WebClientResponseException.class,
                        ex -> {
                            int status = ex.getStatusCode().value();
                            String errBody =
                                    ex.getResponseBodyAsString() == null
                                            ? ""
                                            : ex.getResponseBodyAsString();
                            log.warn(
                                    "[{} {}] {}",
                                    status,
                                    chatEndpoint(),
                                    errBody.length() > 500
                                            ? errBody.substring(0, 500) + "..."
                                            : errBody);
                            return Mono.error(
                                    new RuntimeException(
                                            "[HTTP " + status + "] " + errBody, ex));
                        })
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
