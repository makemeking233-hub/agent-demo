package com.example.agent.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Bump web.config coverage: WebRuntimeConfig bean 装配 + WebProperties 归一化 + pickFirstNonBlank 分支. */
class WebRuntimeConfigTest {

    @Test
    void webRuntimeConfigBuildsBeans() {
        WebRuntimeConfig cfg = new WebRuntimeConfig();
        assertThat(cfg.webLlmProvider()).isNotNull();
        assertThat(cfg.webToolRegistry()).isNotNull();
        assertThat(cfg.webToolRegistry().list()).isNotEmpty();
        assertThat(cfg.webTokenEstimator()).isNotNull();
    }

    @Test
    void webPropertiesNormalizesDefaults() {
        // host/trustedHosts null -> 归一化
        var p = new WebProperties(null, 0, null);
        assertThat(p.host()).isEqualTo("127.0.0.1");
        assertThat(p.port()).isEqualTo(8080);
        assertThat(p.trustedHosts()).isEmpty();
    }

    @Test
    void webPropertiesKeepsProvidedValues() {
        var p = new WebProperties("192.168.1.42", 9090, List.of("192.168.1.0/24"));
        assertThat(p.host()).isEqualTo("192.168.1.42");
        assertThat(p.port()).isEqualTo(9090);
        assertThat(p.trustedHosts()).containsExactly("192.168.1.0/24");
    }

    @Test
    void webPropertiesRejectsOutOfRangePort() {
        // port 越界 -> 回落 8080
        var p = new WebProperties("127.0.0.1", 70000, List.of());
        assertThat(p.port()).isEqualTo(8080);
    }

    @Test
    void pickFirstNonBlankFallsThroughToNull() throws Exception {
        // 反射调用私有 static pickFirstNonBlank, 覆盖空值返回 null 分支
        var method = WebRuntimeConfig.class.getDeclaredMethod("pickFirstNonBlank", String[].class);
        method.setAccessible(true);
        // 全部空 -> null
        Object r = method.invoke(null, (Object) new String[] {"", "  "});
        assertThat(r).isNull();
        // 第一个非空 -> 返回它
        Object r2 = method.invoke(null, (Object) new String[] {"  ", "abc", "def"});
        assertThat(r2).isEqualTo("abc");
    }
}
