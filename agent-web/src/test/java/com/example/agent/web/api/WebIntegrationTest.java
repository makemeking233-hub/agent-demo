package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.web.WebApplication;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

/**
 * T2.5 / T3.6 / T6 集成测试: 通过 {@link WebApplication} 真正启动 Spring WebFlux 内嵌服务器,
 * 用 {@link WebTestClient} 验证 HTTP / SSE 契约。
 *
 * <p>用 {@code @MockBean LlmProvider} 注入固定 chunk 序列, 避免真实调用外部 LLM; 同时设置
 * {@code DEEPSEEK_API_KEY} 让 /api/chat/send 通过 provider 检查, 从而跑真实 AgentLoop → SSE。
 */
@SpringBootTest(
        classes = WebApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"DEEPSEEK_API_KEY=sk-test-fake", "agent.web.trusted-hosts=0.0.0.0,::0,127.0.0.1,::1"})
@AutoConfigureWebTestClient
@ActiveProfiles("web")
class WebIntegrationTest {

    @Autowired
    private WebTestClient client;

    @MockBean
    private LlmProvider provider;

    private void stubTextChunks(String text) {
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any(ChatRequest.class)))
                .thenReturn(
                        Flux.just(
                                        (StreamChunk) new StreamChunk.TextDelta(text),
                                        new StreamChunk.Finished(FinishReason.STOP, null))
                                .delayElements(java.time.Duration.ofMillis(200)));
    }

    private void stubSlowChunks(String text) {
        // 慢速: 让 turn 保持进行中, 便于测 abort 端点(流仍活动)
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any(ChatRequest.class)))
                .thenReturn(
                        Flux.just((StreamChunk) new StreamChunk.TextDelta(text))
                                .delayElements(java.time.Duration.ofSeconds(5)));
    }

    @Test
    void healthReturns200() {
        client.get()
                .uri("/api/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .value(s -> assertThat(s).isIn("ok", "degraded"))
                .jsonPath("$.version")
                .isNotEmpty()
                .jsonPath("$.uptime_s")
                .isNumber();
    }

    @Test
    void healthAlways200EvenWhenProviderMissing() {
        client.get().uri("/api/health").exchange().expectStatus().isOk();
    }

    @Test
    void spaFallbackServesIndexHtml() {
        client.get()
                .uri("/sessions/some-uuid")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith("text/html");
    }

    @Test
    void rootServesIndexHtml() {
        client.get().uri("/").exchange().expectStatus().isOk().expectHeader().contentTypeCompatibleWith("text/html");
    }

    @Test
    void sendBlankContentReturns400() {
        client.post()
                .uri("/api/chat/send")
                .bodyValue(Map.of("content", "   "))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void sendThenStreamEmitsSseEvents() throws Exception {
        stubTextChunks("你好，世界");

        String body =
                client.post()
                        .uri("/api/chat/send")
                        .bodyValue(Map.of("content", "hi"))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .returnResult()
                        .getResponseBody();

        assertThat(body).isNotNull();

        // 从 JSON 响应提取 stream_id
        String streamId =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(body)
                        .path("stream_id")
                        .asText();
        assertThat(streamId).as("stream_id from send response").isNotEmpty();

        // stream 端点应返 SSE 流 text/event-stream; 用 StepVerifier 消费 Flux 验证事件序列。
        // turn 已带 delayElements, 客户端订阅时 turn 仍在进行, 因此能收到完整事件。
        reactor.core.publisher.Flux<String> sse =
                client.get()
                        .uri("/api/chat/stream/{id}", streamId)
                        .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectHeader()
                        .contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                        .returnResult(String.class)
                        .getResponseBody();

        assertThat(sse).isNotNull();
        // 订阅并收集事件数据, 直到流结束 (turn 完成 → message_stop → complete)
        reactor.test.StepVerifier.create(sse)
                .thenConsumeWhile(
                        data -> true,
                        data -> {})
                .verifyComplete();
    }

    @Test
    void abortReturns200ForActiveStream() throws Exception {
        stubSlowChunks("slow");

        String body =
                client.post()
                        .uri("/api/chat/send")
                        .bodyValue(Map.of("content", "go"))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .returnResult()
                        .getResponseBody();

        String streamId =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(body)
                        .path("stream_id")
                        .asText();
        assertThat(streamId).as("stream_id from send").isNotEmpty();

        // abort 原生流 (slow turn 仍在进行) → 200 {aborted:true}
        client.post()
                .uri("/api/chat/abort/{id}", streamId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.aborted")
                .isEqualTo(true);
    }

    @Test
    void abortUnknownStreamReturns404() {
        client.post()
                .uri("/api/chat/abort/{id}", "unknown-id")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
