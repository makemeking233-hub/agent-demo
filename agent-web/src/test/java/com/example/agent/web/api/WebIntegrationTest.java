package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * T2.5: 集成测试 - @SpringBootTest 跑完整 web profile 上下文,
 * 通过 WebTestClient 验证 /api/health 与 SPA fallback (spec §T2.5).
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        classes = com.example.agent.AgentCli.class)
@ActiveProfiles("web")
@TestPropertySource(properties = {
        "agent.web.host=127.0.0.1",
        "agent.web.port=0",
        "agent.web.trusted-hosts=",
        "DEEPSEEK_API_KEY=sk-test-1234"
})
class WebIntegrationTest {

    @Autowired
    WebTestClient webTestClient;

    @Test
    void healthReturnsOk() {
        webTestClient.get().uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ok")
                .jsonPath("$.host").isEqualTo("127.0.0.1")
                .jsonPath("$.version").exists();
    }

    @Test
    void assetsServedFromStaticDir() {
        webTestClient.get().uri("/assets/index-D1D8ZybT.js")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/javascript");
    }

    @Test
    void rootServesIndexHtml() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("text/html")
                .expectBody(String.class).value(html -> assertThat(html).contains("agent-demo"));
    }

    @Test
    void spaRouteFallsBackToIndex() {
        // 模拟 React Router: /sessions/<uuid> 这种客户端路径应该返 index.html
        webTestClient.get().uri("/sessions/abc-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("text/html");
    }
}