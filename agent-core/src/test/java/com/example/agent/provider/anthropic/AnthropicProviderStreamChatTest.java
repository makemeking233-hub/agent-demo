package com.example.agent.provider.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.StreamChunk;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * {@link AnthropicProvider#streamChat} 端到端覆盖（fix-jacoco-rule）。
 *
 * <p>该包此前 LINE 0.747：{@code parseSseLine} / {@code buildRequestBody} / {@code validateProvider}
 * 已有单测，但走真实 HTTP 的 {@code streamChat} 从未被跑到（它是该类最大的未覆盖方法块）。
 * 本类用 WireMock 桩 {@code POST /v1/messages} 把整条链路跑起来。
 *
 * <h2>为什么正路径用 {@code text/plain} 而不是 {@code text/event-stream}</h2>
 *
 * <p>写本测试时发现一个**真实缺陷**（见 {@link #knownDefectSseContentTypeYieldsNoChunks()}）：
 * Spring 的 {@code ServerSentEventHttpMessageReader} 对 {@code text/event-stream} 响应会**剥掉
 * {@code data: } 前缀**，而 {@link AnthropicProvider#parseSseLine(String)} 要求行以
 * {@code "data: "} 开头 —— 两者不匹配，导致线上一个 chunk 都不会产出。
 *
 * <p>本 change 的 scope 是覆盖率门禁，不修该缺陷。为了让 {@code streamChat} 的 HTTP 管线（含
 * timeout / handle / 请求体构造）**真的被执行到**，正路径用例用 {@code text/plain} 让底层
 * String 解码器交付含前缀的原始行；缺陷本身由下面那条 characterization 用例固化，修好后应删除它
 * 并把正路径改回 {@code text/event-stream}。
 */
class AnthropicProviderStreamChatTest {

    private WireMockServer wm;
    private AnthropicProvider provider;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        provider = new AnthropicProvider("test-key", "http://localhost:" + wm.port());
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private static ChatRequest request(String model, Map<String, Object> extra) {
        return new ChatRequest(
                model,
                "you are helpful",
                List.of(new Message.User("hi")),
                List.of(),
                1.0,
                4096,
                extra);
    }

    private void stubBody(String contentType, String body) {
        wm.stubFor(
                post(urlEqualTo("/v1/messages"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", contentType)
                                        .withBody(body)));
    }

    // ---------- 正路径（text/plain：让 data: 前缀活到 parseSseLine）----------

    @Test
    void streamChatParsesThinkingTextAndMessageStop() {
        stubBody(
                "text/plain",
                "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"text\":\"我先思考\"}}\n"
                        + "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"答案\"}}\n"
                        + "event: message_stop\n"
                        + "data: {\"type\":\"message_stop\"}\n");

        StepVerifier.create(
                        provider.streamChat(request("claude-opus-4-20250514", Map.of()))
                                .collectList())
                .assertNext(
                        chunks -> {
                            assertThat(chunks)
                                    .anyMatch(
                                            c ->
                                                    c instanceof StreamChunk.ThinkingDelta t
                                                            && "我先思考".equals(t.text()));
                            assertThat(chunks)
                                    .anyMatch(
                                            c ->
                                                    c instanceof StreamChunk.TextDelta t
                                                            && "答案".equals(t.text()));
                            assertThat(chunks).anyMatch(c -> c instanceof StreamChunk.Finished);
                        })
                .verifyComplete();
    }

    @Test
    void streamChatSkipsBlankEventAndCommentLines() {
        stubBody(
                "text/plain",
                "\n"
                        + "event: message_start\n"
                        + "data: {\"type\":\"message_start\"}\n"
                        + ": keep-alive comment\n"
                        + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"X\"}}\n");

        StepVerifier.create(
                        provider.streamChat(request("claude-opus-4-20250514", Map.of()))
                                .collectList())
                .assertNext(
                        chunks -> {
                            // 只有 text_delta 那一行产出 chunk；message_start / 注释行 / 空行被跳过
                            assertThat(chunks).hasSize(1);
                            assertThat(chunks.get(0)).isInstanceOf(StreamChunk.TextDelta.class);
                        })
                .verifyComplete();
    }

    @Test
    void streamChatIgnoresMalformedJsonPayload() {
        stubBody(
                "text/plain",
                "data: {不是合法 JSON\n"
                        + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Y\"}}\n");

        StepVerifier.create(
                        provider.streamChat(request("claude-opus-4-20250514", Map.of()))
                                .collectList())
                .assertNext(
                        chunks -> {
                            // 坏行被 catch 吞掉（WARN），后续好行仍正常产出
                            assertThat(chunks).hasSize(1);
                            assertThat(((StreamChunk.TextDelta) chunks.get(0)).text()).isEqualTo("Y");
                        })
                .verifyComplete();
    }

    @Test
    void streamChatSendsApiKeyAndAnthropicVersionHeaders() {
        stubBody("text/plain", "data: {\"type\":\"message_stop\"}\n");

        provider.streamChat(request("claude-opus-4-20250514", Map.of())).collectList().block();

        wm.verify(
                WireMock.postRequestedFor(urlEqualTo("/v1/messages"))
                        .withHeader("x-api-key", WireMock.equalTo("test-key"))
                        .withHeader("anthropic-version", WireMock.equalTo("2023-06-01")));
    }

    @Test
    void streamChatRejectsMismatchedProviderInExtra() {
        // 前端选的 provider 与实例不一致时 fail-closed，绝不把请求发出去
        ChatRequest req = request("claude-opus-4-20250514", Map.of("provider", "deepseek"));

        assertThatThrownBy(() -> provider.streamChat(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anthropic");
    }

    // ---------- 已知缺陷固化（修复后应删除本用例）----------

    @Test
    void knownDefectSseContentTypeYieldsNoChunks() {
        // 真实 Anthropic API 返回 Content-Type: text/event-stream。
        // Spring 的 ServerSentEventHttpMessageReader 会剥掉 "data: " 前缀（并把多行 data 用 \n 拼接），
        // 于是 parseSseLine 收到的是 {"type":"message_stop"} 而非 "data: {...}"，
        // 卡在 `if (!line.startsWith("data: ")) return null;` → 整条流零产出。
        //
        // 本用例固化「当前确实是坏的」这一事实（fix-jacoco-rule 只做覆盖率，不修该缺陷）。
        // 修好 streamChat（例如改用 ServerSentEvent 元素类型、或让 parseSseLine 兼容已剥前缀的载荷）
        // 之后，本用例会失败 —— 那时应删除它，并把上面的正路径用例改回 text/event-stream。
        stubBody(
                "text/event-stream",
                "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"答案\"}}\n"
                        + "\n");

        List<StreamChunk> chunks =
                provider.streamChat(request("claude-opus-4-20250514", Map.of()))
                        .collectList()
                        .block();

        assertThat(chunks)
                .as("缺陷：text/event-stream 下解析不出任何 chunk（应为 TextDelta(\"答案\"))")
                .isEmpty();
    }

    // ---------- 构造器与元数据 ----------

    @Test
    void twoArgConstructorUsesProvidedBaseUrl() {
        AnthropicProvider custom = new AnthropicProvider("k", "http://localhost:" + wm.port());
        stubBody("text/plain", "data: {\"type\":\"message_stop\"}\n");

        StepVerifier.create(
                        custom.streamChat(request("claude-3-5-sonnet-20241022", Map.of()))
                                .collectList())
                .assertNext(
                        chunks ->
                                assertThat(chunks)
                                        .anyMatch(c -> c instanceof StreamChunk.Finished))
                .verifyComplete();
    }

    @Test
    void fourArgConstructorAcceptsExplicitTimeouts() {
        AnthropicProvider custom =
                new AnthropicProvider(
                        "k",
                        "http://localhost:" + wm.port(),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(2));
        stubBody("text/plain", "data: {\"type\":\"message_stop\"}\n");

        StepVerifier.create(
                        custom.streamChat(request("claude-3-5-sonnet-20241022", Map.of()))
                                .collectList())
                .assertNext(
                        chunks ->
                                assertThat(chunks)
                                        .anyMatch(c -> c instanceof StreamChunk.Finished))
                .verifyComplete();
    }

    @Test
    void nameContextWindowAndMaxOutputAreStable() {
        assertThat(provider.name()).isEqualTo("anthropic");
        assertThat(provider.contextWindow()).isEqualTo(200_000);
        assertThat(provider.maxOutputTokens()).isEqualTo(8_192);
    }
}
