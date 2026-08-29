package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.web.config.WebProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** T2.4: HealthController (spec §Requirement: Health Check). */
class HealthControllerTest {

    @Test
    void returnsOkWhenProviderConfigured() {
        var env = new MockEnvironment().withProperty("DEEPSEEK_API_KEY", "sk-test-1234");
        var props = new WebProperties("127.0.0.1", 8080, List.of());
        var c = new HealthController(env, props);
        Map<String, Object> body = c.health();
        assertThat(body).containsEntry("status", "ok");
        assertThat(body).containsEntry("host", "127.0.0.1");
        assertThat(body).containsEntry("port", 8080);
        assertThat(body).doesNotContainKey("reason");
    }

    @Test
    void returnsDegradedWhenProviderMissing() {
        var env = new MockEnvironment();
        var props = new WebProperties("127.0.0.1", 8080, List.of());
        var c = new HealthController(env, props);
        Map<String, Object> body = c.health();
        assertThat(body).containsEntry("status", "degraded");
        assertThat(body).containsEntry("reason", "provider_not_configured");
    }

    @Test
    void returnsDegradedWhenApiKeyBlank() {
        var env = new MockEnvironment().withProperty("DEEPSEEK_API_KEY", "   ");
        var props = new WebProperties("127.0.0.1", 8080, List.of());
        var c = new HealthController(env, props);
        assertThat(c.health()).containsEntry("status", "degraded");
    }

    @Test
    void includesUptimeAndVersion() {
        var env = new MockEnvironment().withProperty("DEEPSEEK_API_KEY", "sk-test");
        var props = new WebProperties("127.0.0.1", 8080, List.of());
        var c = new HealthController(env, props);
        Map<String, Object> body = c.health();
        assertThat(body).containsKey("version");
        assertThat(body).containsKey("uptime_s");
        // uptime_s 是 long >= 0
        assertThat((Long) body.get("uptime_s")).isGreaterThanOrEqualTo(0L);
    }
}