package com.example.agent.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.llm.StreamChunk;
import com.example.agent.provider.deepseek.DeepSeekProvider;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

/** 验收 #2：工具调用端到端（mock 模型返回 ReadFile 工具调用）。 */
class ToolCallE2ETest extends E2ETestBase {

    @Test
    void parsesToolCallFromStream() throws Exception {
        wireMock.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(
                                                resourceBytes(
                                                        "e2e/deepseek-stream-tool-call.txt"))));

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
                            var calls = StreamChunk.aggregate(chunks);
                            assertEquals(1, calls.size());
                            assertEquals("ReadFile", calls.get(0).name());
                            assertTrue(calls.get(0).argumentsJson().contains("a.txt"));
                        })
                .verifyComplete();
    }
}
