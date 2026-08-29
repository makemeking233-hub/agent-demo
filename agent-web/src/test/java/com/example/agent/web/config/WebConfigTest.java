package com.example.agent.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** T2.1: WebConfig 启动时校验 host != 0.0.0.0 (spec §Trusted Host Auth / Scenario: Bind to 0.0.0.0 rejected). */
class WebConfigTest {

    @Test
    void rejectsBindingTo0000() {
        var cfg = new WebConfig(new WebProperties("0.0.0.0", 8080, List.of("192.168.1.0/24")));
        assertThatThrownBy(cfg::validateHost)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0.0.0.0")
                .hasMessageContaining("not supported");
    }

    @Test
    void acceptsLoopbackHost() {
        var cfg = new WebConfig(new WebProperties("127.0.0.1", 8080, List.of()));
        cfg.validateHost(); // 不抛
        assertThat(cfg.props().host()).isEqualTo("127.0.0.1");
    }

    @Test
    void acceptsLanHost() {
        var cfg = new WebConfig(new WebProperties("192.168.1.42", 8080, List.of("192.168.1.0/24")));
        cfg.validateHost(); // 不抛
        assertThat(cfg.props().host()).isEqualTo("192.168.1.42");
    }
}