package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.WebApplication;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 模型选择真源的 HTTP 级验证（fix-stale-model-fallback HT 组）。
 *
 * <p>为什么单有单元测试不够：
 *
 * <ol>
 *   <li>本次给 {@code ChatController} / {@code ModelsController} / {@code WecomMessageDispatcher}
 *       都加了构造器依赖，单元测试手工 {@code new} 这些对象，**证明不了 Spring 能装配它们**；
 *   <li>400 / 200 的语义差异只有走真实 HTTP 才看得准。
 * </ol>
 *
 * <p>数据隔离（全局规则 §10）：把数据目录指向 {@code target/}，并关闭自动归档，
 * 避免启动完整上下文时改动 {@code <user.home>/.agent-demo/sessions} 下的真实会话存档。
 * 做法沿用既有的 {@link LocalKeySendTest}。
 *
 * <p>用例编号对应 {@code docs/test-agent-demo/2026-09-18-fix-stale-model-fallback/test-cases.md} 的 HT 组。
 */
@SpringBootTest(
        classes = WebApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            // 仅用于通过 key 存在性检查；本测试的 400 路径在触达上游之前就返回了
            "agent.provider.api-key=sk-fake-model-validation-test",
            "agent.web.trusted-hosts=0.0.0.0,::0,127.0.0.1,::1",
            "agent.session.auto-archive.enabled=false"
        })
@AutoConfigureWebTestClient
@ActiveProfiles("web")
class ChatSendModelHttpTest {

    /** application-web.yml 的 agent.chat.default-model */
    private static final String CONFIGURED_DEFAULT = "deepseek-v4-flash";
    private static final String RETIRED_MODEL = "deepseek-chat";

    static {
        System.setProperty(
                com.example.agent.web.stream.WebAgentRuntime.AGENT_DEMO_HOME_PROPERTY,
                "target/test-data-model-http");
    }

    @Autowired
    private WebTestClient client;

    @SuppressWarnings("unchecked")
    private Map<String, Object> models() {
        return client.get()
                .uri("/api/chat/models")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(Map<String, Object> body, int expectedStatus) {
        return client.post()
                .uri("/api/chat/send")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isEqualTo(expectedStatus)
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    // ----- HT-01 -----

    @Test
    void modelsEndpointCarriesDefaultModelThatExistsInCatalog() {
        Map<String, Object> resp = models();
        assertThat(resp).isNotNull();
        assertThat(resp.get("defaultProvider")).isEqualTo("deepseek");
        assertThat(resp.get("defaultModel")).isEqualTo(CONFIGURED_DEFAULT);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> models = (List<Map<String, Object>>) resp.get("models");
        assertThat(models).extracting(m -> m.get("id")).contains(CONFIGURED_DEFAULT);
        // 本缺陷的前提：那个曾被硬编码的 id 确实不在服务端目录里
        assertThat(models).extracting(m -> m.get("id")).doesNotContain(RETIRED_MODEL);
    }

    // ----- HT-02 -----

    @Test
    void sendWithoutModelFallsBackToConfiguredDefault() {
        Map<String, Object> resp = send(Map.of("content", "模型解析测试"), 200);
        assertThat(resp).isNotNull();
        assertThat(resp.get("model")).isEqualTo(CONFIGURED_DEFAULT);
        assertThat(resp.get("stream_id")).isNotNull();
    }

    // ----- HT-03 -----

    @Test
    void sendWithRetiredModelIsRejectedAndCreatesNoStream() {
        Map<String, Object> resp = send(Map.of("content", "hi", "model", RETIRED_MODEL), 400);
        assertThat(resp).isNotNull();
        assertThat(resp).containsEntry("error", "invalid_model");
        assertThat(resp).containsEntry("requested", RETIRED_MODEL);
        assertThat(resp).doesNotContainKey("stream_id");

        @SuppressWarnings("unchecked")
        List<String> supported = (List<String>) resp.get("supported");
        assertThat(supported).contains(CONFIGURED_DEFAULT);
        assertThat(supported).doesNotContain(RETIRED_MODEL);
    }

    // ----- HT-04 -----

    @Test
    void sendWithLegalModelEchoesIt() {
        Map<String, Object> resp = send(Map.of("content", "hi", "model", "deepseek-reasoner"), 200);
        assertThat(resp).isNotNull();
        assertThat(resp.get("model")).isEqualTo("deepseek-reasoner");
    }
}
