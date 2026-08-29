package com.example.agent.web.config;

import com.example.agent.config.AgentConfig;
import com.example.agent.config.ConfigLoader;
import com.example.agent.core.AgentLoopFactory;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.tools.ToolRegistry;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * web 运行时共享 bean（add-web-ui-v0-1 / D2）。
 *
 * <p>把 {@link LlmProvider} / {@link ToolRegistry} / {@link TokenEstimator} 暴露为 Spring bean，
 * 由 {@link com.example.agent.web.stream.WebAgentRuntime} 注入。集成测试可用 {@code @MockBean}
 * 替换 provider 以注入固定 chunk 序列，无需真实调用外部 LLM。
 *
 * <p>API key 优先级与 CLI 一致：env(DEEPSEEK_API_KEY) &gt; application-local.yml(agent.provider.api-key)
 * &gt; ~/.agent-demo/config.yaml。这保证用户在 git 忽略的 application-local.yml 里填真实 key 后，
 * web 也能直接用（无需改 ~/.agent-demo/config.yaml）。
 */
@Configuration
@Profile("web")
public class WebRuntimeConfig {

    private final AgentConfig cfg;
    private final Environment env;

    public WebRuntimeConfig(Environment env) {
        this.env = env;
        this.cfg =
                new ConfigLoader()
                        .load(
                                Paths.get(
                                        System.getProperty("user.home"),
                                        ".agent-demo",
                                        "config.yaml"));
    }

    @Bean
    public LlmProvider webLlmProvider() {
        // 与 CLI 一致的 key 优先级: env > application-local.yml(agent.provider.api-key) > config.yaml
        String apiKey =
                pickFirstNonBlank(
                        env.getProperty("DEEPSEEK_API_KEY"),
                        env.getProperty("agent.provider.api-key"),
                        cfg.provider().apiKey());
        return AgentLoopFactory.buildProvider(cfg, apiKey);
    }

    @Bean
    public ToolRegistry webToolRegistry() {
        return AgentLoopFactory.buildTools(cfg);
    }

    @Bean
    public TokenEstimator webTokenEstimator() {
        return new TokenEstimator();
    }

    private static String pickFirstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }
}
