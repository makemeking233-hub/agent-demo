package com.example.agent.web.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Web 启动配置 (@Profile("web") 激活).
 *
 * - 读 agent.web.* via {@link WebProperties}.
 * - 启动时校验 host != 0.0.0.0 (spec §Trusted Host Auth / Scenario: Bind to 0.0.0.0 rejected):
 *   v0.1 显式拒绝 wildcard bind, 避免误暴露到公网.
 */
@Configuration
@Profile("web")
@EnableConfigurationProperties(WebProperties.class)
public class WebConfig {

    private final WebProperties props;

    public WebConfig(WebProperties props) {
        this.props = props;
    }

    /** 暴露给测试 / 健康检查用. */
    public WebProperties props() {
        return props;
    }

    @PostConstruct
    void validateHost() {
        if ("0.0.0.0".equals(props.host())) {
            throw new IllegalStateException(
                    "binding 0.0.0.0 is not supported; specify a concrete LAN IP (e.g. 127.0.0.1 or 192.168.x.x)");
        }
    }
}