package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.WebApplication;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 验证: 用户只在 git 忽略的 application-local.yml 里配 agent.provider.api-key (不设
 * DEEPSEEK_API_KEY 环境变量) 时, /api/chat/send 应返回 200 而非 503 provider_not_configured。
 * 这保证 web 与 CLI 走同一 key 来源链。
 */
@SpringBootTest(
        classes = WebApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "agent.provider.api-key=sk-real-from-local-yml",
            "agent.web.trusted-hosts=0.0.0.0,::0,127.0.0.1,::1"
        })
@AutoConfigureWebTestClient
@ActiveProfiles("web")
class LocalKeySendTest {

    @Autowired
    private WebTestClient client;

    @Test
    void sendOkWhenKeyFromLocalYml() {
        // DEEPSEEK_API_KEY 环境变量未设, 仅 agent.provider.api-key (local.yml 来源) 存在
        client.post()
                .uri("/api/chat/send")
                .bodyValue(Map.of("content", "你是谁"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.stream_id")
                .isNotEmpty();
    }
}
