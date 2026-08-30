package com.example.agent.provider.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.provider.deepseek.DeepSeekProvider;
import com.github.tomakehurst.wiremock.WireMockServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * WebClient 显式超时专项测试（openspec webclient-explicit-timeouts T1）。
 *
 * <p>验证 responseTimeout 在 upstream 挂起时按预期触发（spec scenario: response timeout fires）。
 * 用很小的超时值（200ms）+ WireMock 延迟响应，断言调用在超时内失败。
 */
class OpenAiCompatibleTimeoutTest {

    private WireMockServer wm;
    private DeepSeekProvider provider;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void explicitResponseTimeoutFiresWhenUpstreamHangs() {
        // upstream 延迟 2s 才返回；客户端 responseTimeout=200ms → 应在 200ms 左右失败
        wm.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withFixedDelay(2_000)
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody("data: [DONE]\n\n")));

        provider =
                new DeepSeekProvider(
                        "test-key",
                        "http://localhost:" + wm.port(),
                        Duration.ofMillis(200),
                        Duration.ofMillis(1000));

        ChatRequest req =
                new ChatRequest(
                        "deepseek-chat",
                        null,
                        List.of(new Message.User("hello")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());

        assertThrows(
                WebClientRequestException.class,
                () -> provider.streamChat(req).collectList().block(),
                "预期响应超时触发（Reactor Netty 底层抛 ReadTimeoutException → WebClientRequestException）");
    }

    @Test
    void defaultConstructorsBuildClientWithoutError() {
        // 默认超时构造（apiKey / apiKey+baseUrl）不应抛异常并能完成请求
        wm.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody("data: [DONE]\n\n")));

        DeepSeekProvider twoArg = new DeepSeekProvider("test-key", "http://localhost:" + wm.port());
        ChatRequest req =
                new ChatRequest(
                        "deepseek-chat",
                        null,
                        List.of(new Message.User("hello")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());
        twoArg.streamChat(req).collectList().block();
    }
}
