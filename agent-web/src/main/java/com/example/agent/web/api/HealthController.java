package com.example.agent.web.api;

import com.example.agent.web.config.WebProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/health (spec §Requirement: Health Check / §Requirement: Chat Send 同节).
 *
 * <p>永远返 200, 即使 provider 未配置 (只是 status 标 "degraded").
 * 受 TrustedHostFilter 保护, 但 filter 内 /api/health 路径跳过, 所以任何源 IP
 * 都能 ping (用于 K8s liveness/readiness probe / 监控).
 */
@RestController
@RequestMapping("/api")
@Profile("web")
public class HealthController {

    private final Environment env;
    private final WebProperties webProps;

    public HealthController(Environment env, WebProperties webProps) {
        this.env = env;
        this.webProps = webProps;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean providerConfigured = isProviderConfigured();
        body.put("status", providerConfigured ? "ok" : "degraded");
        body.put("version", resolveVersion());
        body.put("uptime_s", resolveUptimeSeconds());
        body.put("host", webProps.host());
        body.put("port", webProps.port());
        if (!providerConfigured) {
            body.put("reason", "provider_not_configured");
        }
        return body;
    }

    private boolean isProviderConfigured() {
        // 与 WebRuntimeConfig 同一 key 优先级：env(DEEPSEEK_API_KEY) > application-local.yml(agent.provider.api-key)
        // 不能只看 env 变量，否则 application-local.yml 填充的 key 会导致 health 误报 degraded/provnot_configured
        String fromEnv = env.getProperty("DEEPSEEK_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) return true;
        String fromConfig = env.getProperty("agent.provider.api-key");
        return fromConfig != null && !fromConfig.isBlank();
    }

    private String resolveVersion() {
        // maven manifest 暴露 Implementation-Version
        String v = HealthController.class.getPackage().getImplementationVersion();
        return v != null ? v : "0.1.0-SNAPSHOT";
    }

    private long resolveUptimeSeconds() {
        long uptimeMs = ManagementFactoryMXBean.uptimeMillis();
        return uptimeMs / 1000;
    }

    /** 单独抽出来避免 import sun.management.* (ManagementFactory 是标准 java.lang.management). */
    private static class ManagementFactoryMXBean {
        static long uptimeMillis() {
            return java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        }
    }
}