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
            "agent.web.trusted-hosts=0.0.0.0,::0,127.0.0.1,::1",
            // 关掉自动归档：本测试会启动完整上下文，否则 ApplicationReadyEvent 会去改动
            // <user.home>/.agent-demo/sessions 下的**真实**会话存档
            "agent.session.auto-archive.enabled=false"
        })
@AutoConfigureWebTestClient
@ActiveProfiles("web")
class LocalKeySendTest {

    /**
     * 隔离数据目录（全局规则 §10：测试不得污染真实数据）。
     *
     * <p>本测试启动完整 WebApplication 并调 {@code /api/chat/send}，会写入**真实会话存档**。
     * 必须在类初始化时设置（早于 Spring 上下文创建）；`@SpringBootTest` 设不了环境变量，
     * 所以走系统属性。
     */
    static {
        System.setProperty(
                com.example.agent.web.stream.WebAgentRuntime.AGENT_DEMO_HOME_PROPERTY,
                "target/test-data");
    }

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

