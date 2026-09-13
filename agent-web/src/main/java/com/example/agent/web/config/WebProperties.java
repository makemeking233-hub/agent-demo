package com.example.agent.web.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * agent.web.* 配置 (application-web.yml 加载, web profile 激活).
 *
 * @param host         绑定的网卡 IP, 例 127.0.0.1 / 192.168.1.42. 0.0.0.0 启动时拒绝.
 * @param port         HTTP 端口, 默认 8080
 * @param trustedHosts 允许的远端 IP 列表 (CIDR 或单 IP), 空 = 仅 loopback.
 * @param https        HTTPS profile 子配置 (add-pwa-support).
 */
@ConfigurationProperties(prefix = "agent.web")
public record WebProperties(String host, int port, List<String> trustedHosts, Https https) {
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
        if (https == null) {
            https = new Https(false, "${java.io.tmpdir}/agent-demo-cert", "changeit", "agent-demo");
        }
    }

    /**
     * HTTPS 子配置（add-pwa-support）。
     *
     * @param enabled          是否启用 HTTPS profile（自签证书生成 + Spring Boot SSL 监听）
     * @param certDir          自签证书生成目录
     * @param keystorePassword PKCS12 keystore 密码（自签证书场景默认 changeit）
     * @param keystoreAlias    keystore alias（默认 agent-demo）
     */
    public record Https(
            boolean enabled, String certDir, String keystorePassword, String keystoreAlias) {
        public Https {
            if (certDir == null || certDir.isBlank()) {
                certDir = "${java.io.tmpdir}/agent-demo-cert";
            }
            if (keystorePassword == null || keystorePassword.isBlank()) {
                keystorePassword = "changeit";
            }
            if (keystoreAlias == null || keystoreAlias.isBlank()) {
                keystoreAlias = "agent-demo";
            }
        }
    }
}