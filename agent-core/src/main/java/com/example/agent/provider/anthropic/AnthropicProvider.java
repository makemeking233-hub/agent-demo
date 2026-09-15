package com.example.agent.provider.anthropic;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.provider.openai.OpenAiCompatibleProvider;
import com.example.agent.provider.openai.OpenAiCompatibleMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.agent.llm.ChatRequest;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

/**
 * Anthropic Messages API Provider（add-reasoning-thinking-streaming）。
 *
 * <p>支持 {@code extended thinking} 模式（Claude 3.7+ Sonnet / Opus 等）：
 *
 * <ul>
 *   <li>请求体加 {@code thinking: {type: "enabled", budget_tokens: N}} → 开启 thinking
 *   <li>SSE 事件流按 content block type 解析：
 *     <ul>
 *       <li>{@code type: "thinking"} → emit {@link StreamChunk.ThinkingDelta}
 *       <li>{@code type: "text"} → emit {@link StreamChunk.TextDelta}
 *     </ul>
 * </ul>
 *
 * <p>工具调用（{@code type: "tool_use"}）v0.x 不实现（v0.2+ 适配）。
 */
public class AnthropicProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);

    private static final int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024;
    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(180);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** add-provider-catalog-abstract：本 provider 对应的 providerId（Anthropic） */
    private static final String PROVIDER_ID = "anthropic";

    private final String apiKey;
    private final String baseUrl;
    private final Duration responseTimeout;
    private final Duration connectTimeout;
    private final WebClient http;
    private final ObjectMapper json = new ObjectMapper();

    public AnthropicProvider(String apiKey) {
        this(apiKey, "https://api.anthropic.com", DEFAULT_RESPONSE_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
    }

    public AnthropicProvider(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, DEFAULT_RESPONSE_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
    }

    public AnthropicProvider(
            String apiKey, String baseUrl, Duration responseTimeout, Duration connectTimeout) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.responseTimeout = responseTimeout;
        this.connectTimeout = connectTimeout;
        this.http =
                WebClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("x-api-key", apiKey)
                        .defaultHeader("anthropic-version", "2023-06-01")
                        .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                        .clientConnector(
                                new ReactorClientHttpConnector(
                                        HttpClient.create()
                                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())))
                        .build();
    }

    /**
     * add-provider-catalog-abstract：校验 {@code req.extra().get("provider")} 等于本 provider id。
     *
     * <p>多 provider 共存时,前端 ModelSelect 选择的 provider 必须与 AgentLoop 实际路由的 provider
     * 一致,避免误把 Anthropic 请求发到 DeepSeek provider。若 {@code req.extra} 为空或不含
     * "provider" 字段,跳过校验(向后兼容 v0.1 调用方)。
     */
    static void validateProvider(ChatRequest req) {
        if (req.extra() == null) return;
        Object v = req.extra().get("provider");
        if (v == null) return;
        if (!PROVIDER_ID.equals(v.toString())) {
            throw new IllegalArgumentException(
                    "AnthropicProvider expected provider=\"" + PROVIDER_ID
                            + "\" but req.extra.provider=\"" + v + "\"");
        }
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public int contextWindow() {
        return 200_000;
    }

    @Override
    public int maxOutputTokens() {
        return 8_192;
    }

    @Override
    public Flux<StreamChunk> streamChat(ChatRequest req) {
        // add-provider-catalog-abstract: 校验 req.extra 中的 provider 与本实例一致
        validateProvider(req);
        String body = buildRequestBody(req);
        return http
                .post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .<StreamChunk>handle((line, sink) -> {
                    StreamChunk chunk = parseSseLine(line);
                    if (chunk != null) sink.next(chunk);
                })
                .timeout(responseTimeout);
    }

    /**
     * 构造 Anthropic Messages API 请求体。add-reasoning-thinking-streaming:
     * Claude extended thinking 模型自动加 {@code thinking: {type: "enabled", budget_tokens: 4096}}。
     */
    String buildRequestBody(ChatRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.model());
        body.put("max_tokens", req.maxTokens() != null ? req.maxTokens() : 4096);
        body.put("stream", true);
        // add-reasoning-thinking-streaming + add-models-dropdown-v0：
        //   - 推理模型自动开 thinking
        //   - reasoningEffort 透传：extra.reasoning_effort（"low"/"medium"/"high"）→ budget_tokens 折算
        //     low→1024 / medium→4096 / high→16384（无 extra 时 fallback 4096，即原 medium 行为，向后兼容）
        if (isThinkingModel(req.model())) {
            int budget = resolveBudgetTokens(req);
            body.put("thinking", Map.of("type", "enabled", "budget_tokens", budget));
        }
        if (req.systemPrompt() != null && !req.systemPrompt().isEmpty()) {
            body.put("system", req.systemPrompt());
        }
        // Anthropic messages: [{role, content}] (content 是字符串)
        var messages = new java.util.ArrayList<Map<String, Object>>();
        for (var m : req.messages()) {
            if ("user".equals(m.role())) {
                messages.add(Map.of("role", "user", "content", m.content()));
            } else if ("assistant".equals(m.role())) {
                // add-reasoning-thinking-streaming: assistant 消息还原 content 数组（thinking + text）
                // v0.1 简化：仅放 content 文本
                messages.add(Map.of("role", "assistant", "content", m.content()));
            }
            // tool / system 不进 Anthropic body
        }
        body.put("messages", messages);
        return json.valueToTree(body).toString();
    }

    static boolean isThinkingModel(String model) {
        if (model == null) return false;
        String m = model.toLowerCase();
        return m.contains("opus-4") || m.contains("sonnet-4") || m.contains("3-7-sonnet")
                || m.contains("claude-3-7") || m.contains("claude-opus-4")
                || m.contains("claude-sonnet-4");
    }

    /**
     * reasoningEffort → budget_tokens 折算表（add-models-dropdown-v0）。
     *
     * <p>Anthropic 不接受 reasoning_effort 字符串，需折算为 thinking.budget_tokens 整数。
     * low→1024(轻量思考)/ medium→4096(中等深度)/ high→16384(深度推理)。
     */
    private static final Map<String, Integer> EFFORT_BUDGET_TOKENS = Map.of(
            "low", 1024,
            "medium", 4096,
            "high", 16384);

    /** 默认 budget_tokens（add-reasoning-thinking-streaming 老行为；向后兼容） */
    private static final int DEFAULT_BUDGET_TOKENS = 4096;

    /**
     * 从 req.extra() 读 reasoning_effort，折算 budget_tokens；未传时回退 DEFAULT_BUDGET_TOKENS。
     */
    static int resolveBudgetTokens(ChatRequest req) {
        if (req.extra() == null) return DEFAULT_BUDGET_TOKENS;
        Object v = req.extra().get("reasoning_effort");
        if (v instanceof String s) {
            Integer budget = EFFORT_BUDGET_TOKENS.get(s.toLowerCase());
            if (budget != null) return budget;
        }
        return DEFAULT_BUDGET_TOKENS;
    }

    /**
     * 解析 Anthropic SSE 单行。
     *
     * <p>Anthropic SSE 事件类型（add-reasoning-thinking-streaming 关心）：
     *
     * <ul>
     *   <li>{@code content_block_start} + type=thinking → 占位（不 emit chunk）
     *   <li>{@code content_block_delta} + delta.type=thinking_delta → {@link StreamChunk.ThinkingDelta}
     *   <li>{@code content_block_delta} + delta.type=text_delta → {@link StreamChunk.TextDelta}
     *   <li>{@code message_delta} + delta.stop_reason → 暂不 emit Finished（等 message_stop）
     *   <li>{@code message_stop} → {@link StreamChunk.Finished}(STOP, null)
     * </ul>
     */
    StreamChunk parseSseLine(String line) {
        if (line == null || line.isEmpty()) return null;
        if (line.startsWith("event: ")) return null; // event: 行只标记事件类型，跳过
        if (!line.startsWith("data: ")) return null;
        String payload = line.substring(6).trim();
        if (payload.isEmpty()) return null;
        try {
            JsonNode root = json.readTree(payload);
            String type = root.path("type").asText("");
            switch (type) {
                case "content_block_delta":
                    return parseContentBlockDelta(root);
                case "message_stop":
                    return new StreamChunk.Finished(FinishReason.STOP, null);
                default:
                    return null; // message_start / content_block_start 等暂不 emit
            }
        } catch (Exception e) {
            log.warn("anthropic sse parse failed: {}", e.getMessage());
            return null;
        }
    }

    private StreamChunk parseContentBlockDelta(JsonNode root) {
        JsonNode delta = root.path("delta");
        String deltaType = delta.path("type").asText("");
        String text = delta.path("text").asText("");
        if (text.isEmpty()) return null;
        // add-reasoning-thinking-streaming: thinking_delta → ThinkingDelta；text_delta → TextDelta
        return switch (deltaType) {
            case "thinking_delta" -> new StreamChunk.ThinkingDelta(text);
            case "text_delta" -> new StreamChunk.TextDelta(text);
            default -> null;
        };
    }
}