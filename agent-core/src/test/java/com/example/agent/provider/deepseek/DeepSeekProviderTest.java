package com.example.agent.provider.deepseek;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.StreamChunk;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

class DeepSeekProviderTest {
    private WireMockServer wm;
    private DeepSeekProvider provider;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        provider = new DeepSeekProvider("test-key", "http://localhost:" + wm.port());
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void streamsTextAndUsage() {
        wm.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(
                                                "data:"
                                                    + " {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n"
                                                    + "data:"
                                                    + " {\"choices\":[{\"delta\":{}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}\n\n"
                                                    + "data: [DONE]\n\n")));

        ChatRequest req =
                new ChatRequest(
                        "deepseek-chat",
                        null,
                        List.of(new Message.User("hello")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());

        StepVerifier.create(provider.streamChat(req).collectList())
                .assertNext(
                        chunks -> {
                            assertTrue(
                                    chunks.stream()
                                            .anyMatch(
                                                    c ->
                                                            c instanceof StreamChunk.TextDelta t
                                                                    && "Hi".equals(t.text())),
                                    "期望 TextDelta(\"Hi\")；实际 " + chunks);
                            assertTrue(
                                    chunks.stream()
                                            .anyMatch(
                                                    c ->
                                                            c instanceof StreamChunk.Usage u
                                                                    && u.promptTokens() == 10),
                                    "期望 Usage(prompt=10)；实际 " + chunks);
                        })
                .verifyComplete();
    }

    @Test
    void contextWindowReturns128k() {
        assertEquals(128_000, provider.contextWindow());
    }

    @Test
    void maxOutputTokensReturns8192() {
        assertEquals(8_192, provider.maxOutputTokens());
    }

    @Test
    void requestBodyIncludesStreamOptions() {
        wm.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody("data: [DONE]\n\n")));

        ChatRequest req =
                new ChatRequest(
                        "deepseek-chat",
                        null,
                        List.of(new Message.User("hello")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());
        provider.streamChat(req).collectList().block();

        wm.verify(
                WireMock.postRequestedFor(urlEqualTo("/v1/chat/completions"))
                        .withRequestBody(
                                WireMock.matchingJsonPath(
                                        "$.stream_options.include_usage",
                                        WireMock.equalTo("true"))));
    }

    @Test
    void buffersResponseLargerThanDefault256kLimit() {
        // 300KB 的 content 超过 WebClient 默认 256KB 内存上限，验证已放大 maxInMemorySize 不会截断
        String content = "x".repeat(300_000);
        wm.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(
                                                "data: {\"choices\":[{\"delta\":{\"content\":\""
                                                        + content
                                                        + "\"}}]}\n\n"
                                                        + "data: [DONE]\n\n")));

        ChatRequest req =
                new ChatRequest(
                        "deepseek-chat",
                        null,
                        List.of(new Message.User("hello")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());

        StepVerifier.create(provider.streamChat(req).collectList())
                .assertNext(
                        chunks ->
                                assertTrue(
                                        chunks.stream()
                                                .anyMatch(
                                                        c ->
                                                                c instanceof StreamChunk.TextDelta t
                                                                        && t.text().length()
                                                                                == 300_000),
                                        "期望 TextDelta 长度 300000；实际 " + chunks))
                .verifyComplete();
    }
}
