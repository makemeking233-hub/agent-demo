package com.example.agent.provider.deepseek;

import com.example.agent.provider.ChatRequest;
import com.example.agent.provider.LlmProvider;
import com.example.agent.provider.StreamChunk;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * DeepSeek Provider 实现（OpenAI 兼容协议；详见 design.md §6.1）。
 *
 * <p>v0.1 简化：拿到完整 SSE body 后按行解析（避免依赖分块传输语义）。
 * v0.2 升级：用 bodyToFlux(DataBuffer) + 流式按行解析，启用真正的流式。
 */
public class DeepSeekProvider implements LlmProvider {
    /** DeepSeek-chat 上下文窗口（128K tokens） */
    private static final int CONTEXT_WINDOW = 128_000;
    /** DeepSeek-chat 最大输出（8192 tokens） */
    private static final int MAX_OUTPUT = 8_192;

    private final WebClient client;
    private final DeepSeekMapper mapper;

    /**
     * @param apiKey DeepSeek API key
     * @param baseUrl DeepSeek API base URL（如 https://api.deepseek.com）
     */
    public DeepSeekProvider(String apiKey, String baseUrl) {
        this.client = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
        this.mapper = new DeepSeekMapper();
    }

    @Override public String name() { return "deepseek"; }
    @Override public int contextWindow() { return CONTEXT_WINDOW; }
    @Override public int maxOutputTokens() { return MAX_OUTPUT; }

    @Override
    public Flux<StreamChunk> streamChat(ChatRequest req) {
        var body = mapper.toRequestBody(req);
        return client.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .flatMapMany(payload -> Flux.fromIterable(
                payload.lines()
                    .map(mapper::parseSseLine)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList()));
    }
}