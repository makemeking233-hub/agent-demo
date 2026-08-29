package com.example.agent.web.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * agent.web.* 配置 (application-web.yml 加载, web profile 激活).
 *
 * @param host         绑定的网卡 IP, 例 127.0.0.1 / 192.168.1.42. 0.0.0.0 启动时拒绝.
 * @param port         HTTP 端口, 默认 8080
 * @param trustedHosts 允许的远端 IP 列表 (CIDR 或单 IP), 空 = 仅 loopback.
 */
@ConfigurationProperties(prefix = "agent.web")
public record WebProperties(String host, int port, List<String> trustedHosts) {
    public WebProperties {
        if (host == null || host.isBlank()) {
            host = "127.0.0.1";
        }
        if (port <= 0 || port > 65535) {
            port = 8080;
        }
        if (trustedHosts == null) {
            trustedHosts = List.of();
        }
    }
}