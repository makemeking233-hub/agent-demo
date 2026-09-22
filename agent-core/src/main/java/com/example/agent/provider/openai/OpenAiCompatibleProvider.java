package com.example.agent.provider.openai;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
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
     * <p>正常响应走 {@code bodyToFlux(DataBuffer.class)} 逐段消费，不受此上限约束；此值只兜住
     * 4xx/5xx 错误体（{@link org.springframework.web.reactive.function.client.WebClientResponseException}
     * 需要把错误体读成字符串）。WebClient 默认 256KB 在错误体很长时会抛
     * {@code DataBufferLimitException}，这里放大到 16MB。
     */
    private static final int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024;

    /**
     * 默认「读空闲」超时：两次网络读之间允许的最大间隔（Reactor Netty {@code responseTimeout} 语义）。
     *
     * <p>真流式下它不是整体响应时长上限——只要上游还在持续吐 chunk，长回答不会被误杀；
     * 反过来上游静默超过此值即判定连接僵死。可被新重载构造覆盖。
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
        this.baseUrl = baseUrl; // fix-provider-baseurl：baseUrl() 默认实现返回该字段
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
    public Flux<StreamChunk> streamChat(ChatRequest req) {
        // add-provider-catalog-abstract: 子类可选覆盖此钩子做 provider 校验（默认放过，兼容 v0.1 调用方）
        validateProviderHook(req);
        var body = mapper.toRequestBody(req);
        return client.post()
                .uri(chatEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                // 真流式：bodyToFlux 每收到一段 TCP 数据就下发，不等整个响应结束。
                // 行边界由 SseLineBuffer 跨 buffer 累积（SSE 事件可能被切成两个 buffer）。
                .bodyToFlux(DataBuffer.class)
                .transform(SseLineBuffer::lines)
                // 整行（含 "data: " 前缀）交给 parseSseLine：它自己会剥前缀并处理 [DONE] / 空行
                .filter(line -> line.startsWith("data: "))
                .map(mapper::parseSseLine)
                .filter(Optional::isPresent)
                .map(Optional::get)
                // 4xx/5xx：把错误体包成 "[HTTP xxx] {body}"，让调用方看到可读原因
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
                            return Flux.error(
                                    new RuntimeException(
                                            "[HTTP " + status + "] " + errBody, ex));
                        });
    }

    /**
     * 构造器传入的 API base URL（fix-provider-baseurl）。
     *
     * <p>{@link #baseUrl()} 默认返回此值；子类可选择 override（如需注入路径前缀）。
     */
    protected final String baseUrl;

    /** 返回构造器传入的 base URL。 */
    protected String baseUrl() {
        return baseUrl;
    }

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

    /**
     * add-provider-catalog-abstract：provider 校验钩子（默认放过，v0.1 兼容）。
     *
     * <p>子类可覆盖做严格校验（如 {@link com.example.agent.provider.deepseek.DeepSeekProvider} /
     * {@link com.example.agent.provider.anthropic.AnthropicProvider}）：从 {@code req.extra().get("provider")}
     * 读期望 provider id，不匹配抛 {@link IllegalArgumentException}。
     */
    protected void validateProviderHook(ChatRequest req) {
        // 默认放过：v0.1 调用方不写 req.extra.provider
    }
}
