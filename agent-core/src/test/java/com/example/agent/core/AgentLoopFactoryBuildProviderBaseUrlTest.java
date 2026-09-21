package com.example.agent.core;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.config.AgentConfig;
import com.example.agent.config.AgentConfig.Provider;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.core.Message;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * fix-provider-baseurl 验证：{@link AgentLoopFactory#buildProvider} 在 cfg.provider().baseUrl 非空时
 * 真的把请求打到该 URL（而不只是改字段后被丢弃）。
 *
 * <p>用 WireMock 占一个随机端口，构造一个把 provider.baseUrl 指到该端口的 AgentConfig，
 * 调 buildProvider + streamChat，断言请求落到 WireMock。若 buildProvider 漏掉 baseUrl，
 * WebClient 会用默认 https://api.deepseek.com（请求外发，WireMock 收不到 → 断言失败）。
 */
class AgentLoopFactoryBuildProviderBaseUrlTest {

    private WireMockServer wm;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0); // random port
        wm.start();
        wm.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/event-stream")
                                        .withBody(
                                                "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\n"
                                                        + "data: [DONE]\n\n")));
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void buildProviderSendsRequestToCfgBaseUrl() {
        String customBaseUrl = "http://localhost:" + wm.port();

        // 从 defaults() 复制所有非 provider 字段，只改 provider.baseUrl
        AgentConfig defaults = AgentConfig.defaults();
        AgentConfig customCfg = withProviderBaseUrl(defaults, customBaseUrl);

        LlmProvider provider = AgentLoopFactory.buildProvider(customCfg, "test-key");

        ChatRequest req =
                new ChatRequest(
                        defaults.provider().model(),
                        null,
                        List.of(new Message.User("hello")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());

        StepVerifier.create(provider.streamChat(req).collectList())
                .assertNext(
                        chunks ->
                                assertThat(
                                                chunks.stream()
                                                        .anyMatch(
                                                                c ->
                                                                        c instanceof StreamChunk.TextDelta t
                                                                                && "hi".equals(t.text())))
                                        .as("期望 TextDelta(\"hi\")")
                                        .isTrue())
                .verifyComplete();

        // 决定性断言：WireMock 真的收到了请求 → WebClient 打到了 customBaseUrl
        wm.verify(postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    /** AgentConfig record 没有内建 with——手动重建 Provider 字段，其余沿用 defaults。 */
    private static AgentConfig withProviderBaseUrl(AgentConfig base, String baseUrl) {
        Provider p = new Provider(
                base.provider().type(),
                base.provider().apiKey(),
                baseUrl,
                base.provider().model(),
                base.provider().maxOutputTokens());
        return new AgentConfig(
                p,
                base.permission(),
                base.cost(),
                base.context(),
                base.shell(),
                base.memoryInject(),
                base.logging(),
                base.memory(),
                base.mcp(),
                base.worktree(),
                base.plugins(),
                base.search(),
                base.voice());
    }
}
