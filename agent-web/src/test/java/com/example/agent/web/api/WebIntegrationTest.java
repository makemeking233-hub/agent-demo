package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.WebApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * T2.5 / T6 集成测试: 通过 {@link WebApplication} 真正启动 Spring WebFlux 内嵌服务器,
 * 用 {@link WebTestClient} 验证 HTTP 契约 (此前根因已修: agent-web 必须有独立
 * {@code @SpringBootApplication} 覆盖 {@code web-application-type=reactive}, 且 SPA
 * fallback RouterFunction 不能抢占 /api/** 的注解控制器映射).
 *
 * <p>随机端口, 避免与真实 8080 冲突; loopback 源 IP 会被 TrustedHostFilter 放行.
 */
@SpringBootTest(classes = WebApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("web")
class WebIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Test
    void healthReturns200() {
        // 不设 DEEPSEEK_API_KEY → status=degraded, 但 HTTP 永远 200 (spec §Health Check)
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
        // 即使 provider 未配置, /api/health 也必须是 200 (spec: 永远 200)
        client.get().uri("/api/health").exchange().expectStatus().isOk();
    }

    @Test
    void spaFallbackServesIndexHtml() {
        // SPA fallback: 客户端路由 (非 /api) 应回 index.html 而非 404
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
        // 根路径 / 由默认 static resource handling 服务 index.html
        client.get()
                .uri("/")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith("text/html");
    }
}
