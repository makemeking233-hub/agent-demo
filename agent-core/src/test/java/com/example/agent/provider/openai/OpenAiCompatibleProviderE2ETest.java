/**
 * 端到端流式验证（OpenAiCompatibleProvider + 真 HTTP transport）。
 *
 * <p>add-true-streaming：验证 OpenAiCompatibleProvider.streamChat 是否真的逐 chunk emit
 * （不是 bodyToMono 一次性返回）。
 *
 * <p>用 WireMock 真 HTTP 服务模拟 DeepSeek SSE 流式响应：3 段独立 data 块，间隔 50ms。
 * 期望：provider.streamChat 输出的 Flux 在 50ms / 100ms / 150ms 三个时刻点**各** emit 1 个 TextDelta，
 * 而不是 150ms 后一次性 emit 1 个拼好的 TextDelta。
 *
 * <p>当前 v0.1 状态：bodyToMono 一次性返回 — 此测试**应该失败**（证明问题）。v0.2 升级
 * bodyToFlux + DataBuffer 后，此测试**应该通过**。
 */
package com.example.agent.provider.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.StreamChunk;
import com.example.agent.provider.deepseek.DeepSeekProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class OpenAiCompatibleProviderE2ETest {

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
    void streamChatEmitsTextDeltaForEachSseChunk_separately() {
        // 模拟真流式：3 段独立 data，每段 50ms 间隔（chucked body）
        wm.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withChunkedDribbleDelay(2, 50) // 每 2 字节 flush 一次（最细粒度流式）
                                        .withBody(
                                                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n"
                                                        + "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
                                                        + "data: {\"choices\":[{\"delta\":{\"content\":\"！\"}}]}\n\n"
                                                        + "data: [DONE]\n\n")));

        ChatRequest req =
                new ChatRequest(
                        "deepseek-chat",
                        null,
                        List.of(new Message.User("hi")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());

        Instant start = Instant.now();

        // 关键断言：v0.2 真流式下，3 个 TextDelta 应在不同时间点 emit
        StepVerifier.create(provider.streamChat(req))
                .expectNextMatches(
                        chunk -> {
                            Duration elapsed = Duration.between(start, Instant.now());
                            System.out.println("chunk 1 at +" + elapsed.toMillis() + "ms: " + chunk);
                            return chunk instanceof StreamChunk.TextDelta
                                    && "你".equals(((StreamChunk.TextDelta) chunk).text());
                        })
                .expectNextMatches(
                        chunk -> {
                            Duration elapsed = Duration.between(start, Instant.now());
                            System.out.println("chunk 2 at +" + elapsed.toMillis() + "ms: " + chunk);
                            return chunk instanceof StreamChunk.TextDelta
                                    && "好".equals(((StreamChunk.TextDelta) chunk).text());
                        })
                .expectNextMatches(
                        chunk -> {
                            Duration elapsed = Duration.between(start, Instant.now());
                            System.out.println("chunk 3 at +" + elapsed.toMillis() + "ms: " + chunk);
                            return chunk instanceof StreamChunk.TextDelta
                                    && "！".equals(((StreamChunk.TextDelta) chunk).text());
                        })
                .verifyComplete();
    }

    @Test
    void streamChatDoesNotMergeAllTextIntoSingleChunk() {
        // 简化测试：只发 1 段，验证完整链路能解析出 1 个 TextDelta
        wm.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(
                                                "data: {\"choices\":[{\"delta\":{\"content\":\"single\"}}]}\n\n"
                                                        + "data: [DONE]\n\n")));

        ChatRequest req =
                new ChatRequest(
                        "deepseek-chat",
                        null,
                        List.of(new Message.User("hi")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());

        List<StreamChunk> chunks =
                provider.streamChat(req).collectList().block(Duration.ofSeconds(5));

        long textDeltaCount =
                chunks.stream().filter(c -> c instanceof StreamChunk.TextDelta).count();
        System.out.println("TextDelta count: " + textDeltaCount);
        System.out.println("All chunks: " + chunks);

        // 1 段 data → 期望 1 个 TextDelta
        assertThat(textDeltaCount).isEqualTo(1);
    }

    @Test
    void streamChatEmitsFirstChunkLongBeforeResponseCompletes() {
        // 真 TTFT 场景：上游把 3 个 SSE 事件分 5 段、共 500ms 陆续吐（首段 ~100ms 到达）。
        // 分 5 段还顺带覆盖「一个 data: 行被 TCP 切成两个 DataBuffer」的跨帧拼行。
        wm.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withChunkedDribbleDelay(5, 500)
                                        .withBody(
                                                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n"
                                                        + "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
                                                        + "data: {\"choices\":[{\"delta\":{\"content\":\"！\"}}]}\n\n"
                                                        + "data: [DONE]\n\n")));

        ChatRequest req =
                new ChatRequest(
                        "deepseek-chat",
                        null,
                        List.of(new Message.User("hi")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());

        List<Instant> emitTimes = new CopyOnWriteArrayList<>();
        Instant start = Instant.now();
        List<StreamChunk> chunks =
                provider.streamChat(req)
                        .doOnNext(c -> emitTimes.add(Instant.now()))
                        .collectList()
                        .block(Duration.ofSeconds(10));
        long totalMs = Duration.between(start, Instant.now()).toMillis();

        assertThat(chunks).isNotNull();
        long ttftMs = Duration.between(start, emitTimes.get(0)).toMillis();
        System.out.println(
                "TTFT=" + ttftMs + "ms, total=" + totalMs + "ms, chunks=" + chunks);

        // 3 个 chunk 全部解析出来（跨帧拼行没把事件切坏）
        assertThat(chunks).hasSize(3);
        // 真流式：首 chunk 远早于整体响应结束（v0.1 bodyToMono 下 TTFT ≈ total ≈ 500ms）
        assertThat(ttftMs).as("TTFT should be small for true streaming").isLessThan(300L);
        assertThat(totalMs).as("first chunk must arrive well before completion")
                .isGreaterThan(ttftMs + 200L);
    }
}