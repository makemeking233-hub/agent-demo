package com.example.agent.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.llm.StreamChunk;
import com.example.agent.provider.deepseek.DeepSeekProvider;
import com.github.tomakehurst.wiremock.client.WireMock;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

/** 验收 #1：REPL 流式输出（端到端：用 WireMock 模拟 DeepSeek，验证 provider 拿到正确 chunk 序列）。 */
class ReplStreamingE2ETest extends E2ETestBase {

    @Test
    void streamsHelloResponse() throws Exception {
        wireMock.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(resourceBytes("e2e/deepseek-stream-hello.txt"))));

        DeepSeekProvider provider = new DeepSeekProvider("test-key", deepseekBaseUrl());
        StepVerifier.create(
                        provider.streamChat(
                                        new com.example.agent.llm.ChatRequest(
                                                "deepseek-chat",
                                                null,
                                                java.util.List.of(),
                                                java.util.List.of(),
                                                1.0,
                                                1000,
                                                java.util.Map.of()))
                                .collectList())
                .assertNext(
                        chunks -> {
                            boolean hasText =
                                    chunks.stream()
                                            .anyMatch(
                                                    c ->
                                                            c instanceof StreamChunk.TextDelta t
                                                                    && t.text().contains("你好"));
                            boolean hasUsage =
                                    chunks.stream()
                                            .anyMatch(
                                                    c ->
                                                            (c instanceof StreamChunk.Finished f
                                                                            && f.usage() != null
                                                                            && f.usage()
                                                                                            .promptTokens()
                                                                                    == 10)
                                                                    || (c
                                                                                    instanceof
                                                                                    StreamChunk
                                                                                            .Usage
                                                                                    u
                                                                            && u.promptTokens()
                                                                                    == 10));
                            assertTrue(hasText, "应包含 TextDelta「你好」");
                            assertTrue(hasUsage, "应包含 Usage(prompt=10)（顶层或 Finished.usage）");
                        })
                .verifyComplete();

        // 验证请求体含 stream_options.include_usage
        wireMock.verify(
                WireMock.postRequestedFor(urlEqualTo("/v1/chat/completions"))
                        .withRequestBody(
                                WireMock.matchingJsonPath(
                                        "$.stream_options.include_usage",
                                        WireMock.equalTo("true"))));
    }
}
