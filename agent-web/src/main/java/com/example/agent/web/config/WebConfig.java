package com.example.agent.web.config;

import com.example.agent.config.AgentConfig;
import com.example.agent.config.ConfigLoader;
import jakarta.annotation.PostConstruct;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Web 启动配置 (@Profile("web") 激活).
 *
 * - 读 agent.web.* via {@link WebProperties}.
 * - 启动时校验 host != 0.0.0.0 (spec §Trusted Host Auth / Scenario: Bind to 0.0.0.0 rejected):
 *   v0.1 显式拒绝 wildcard bind, 避免误暴露到公网.
 * - 启动时校验 voice.postProcess.enabled 一致性 (improve-voice-accuracy T8.4):
 *   enabled=true 但 DeepSeek key 缺失 → 启动失败.
 */
@Configuration
@Profile("web")
@EnableConfigurationProperties(WebProperties.class)
public class WebConfig {

    private final WebProperties props;
    private final Environment env;
    private final AgentConfig cfg;

    /** Spring 注入入口: 从 <agent 数据目录>/config.yaml + env 加载 AgentConfig. */
    @Autowired
    public WebConfig(WebProperties props, Environment env) {
        this(props, env, new ConfigLoader()
                .load(com.example.agent.config.AgentPaths.agentHome().resolve("config.yaml")));
    }

    /** 测试入口: 直接注入 AgentConfig. */
    public WebConfig(WebProperties props, Environment env, AgentConfig cfg) {
        this.props = props;
        this.env = env;
        this.cfg = cfg;
    }

    /** 暴露给测试 / 健康检查用. */
    public WebProperties props() {
        return props;
    }

    /** 暴露给测试用: 加载的 AgentConfig. */
    public AgentConfig config() {
        return cfg;
    }

    @PostConstruct
    void validateAll() {
        validateHost();
        validateVoiceConfig();
    }

    /** 拒绝绑定 0.0.0.0 (v0.1 不支持 wildcard bind). */
    void validateHost() {
        if ("0.0.0.0".equals(props.host())) {
            throw new IllegalStateException(
                    "binding 0.0.0.0 is not supported; specify a concrete LAN IP (e.g. 127.0.0.1 or 192.168.x.x)");
        }
    }

    /**
     * 启动门禁 (improve-voice-accuracy T8.4): voice.postProcess.enabled=true 时必须配置 DeepSeek key。
     *
     * <p>key 优先级与 CLI 一致：env(DEEPSEEK_API_KEY) > application-local.yml(agent.provider.api-key)
     * > ~/.agent-demo/config.yaml(provider.apiKey)。
     *
     * <p>enabled=false 时跳过本校验（前端不会调用 voice-correction 端点）。
     */
    void validateVoiceConfig() {
        if (!cfg.voice().postProcess().enabled()) {
            return;
        }
        String apiKey = pickFirstNonBlank(
                env.getProperty("DEEPSEEK_API_KEY"),
                env.getProperty("agent.provider.api-key"),
                cfg.provider().apiKey());
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "voice.postProcess.enabled=true 但未配置 DeepSeek API key。"
                            + "请设置环境变量 DEEPSEEK_API_KEY，或在 application-local.yml 加 agent.provider.api-key，"
                            + "或在 ~/.agent-demo/config.yaml 加 provider.apiKey。"
                            + "若不需要 ASR 后处理，将 voice.postProcess.enabled 设为 false 即可跳过本端点。");
        }
    }

    private static String pickFirstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }
}