package com.example.agent.web.config;

import com.example.agent.settings.SettingsChangeBroadcaster;
import com.example.agent.settings.SettingsFile;
import com.example.agent.settings.SettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * settings 包 Bean 装配（add-settings-foundation M1）。
 *
 * <p>解析 home 目录（CLI flag &gt; env &gt; {@code user.home}），注册 settings 包核心组件。
 */
@Configuration
@Profile("web")
public class SettingsConfig {

    @Value("${agent.home:#{null}}")
    private String cliHome;

    @Bean
    public SettingsFile settingsFile() {
        // fix-agent-home-isolation：非 cliHome 分支改走 AgentPaths。
        // 原先把 AGENT_DEMO_HOME 直接当作「完整 home 路径」传给 resolveDefaultHome，
        // 而全仓其它位置都把它当「基目录」（其下再拼 .agent-demo）——同一个变量两种语义，
        // 会导致 settings.yaml 落在 <env>/ 而 sessions/logs 落在 <env>/.agent-demo/。
        // 现统一为基目录语义（SettingsFile.resolveDefaultHome 自身契约不变，仍接受完整路径）。
        Path home = cliHome != null && !cliHome.isBlank()
                ? Paths.get(cliHome)
                : com.example.agent.config.AgentPaths.agentHome();
        return new SettingsFile(home);
    }

    @Bean
    public SettingsChangeBroadcaster settingsChangeBroadcaster() {
        return new SettingsChangeBroadcaster();
    }

    @Bean
    public SettingsService settingsService(SettingsFile file, SettingsChangeBroadcaster broadcaster) {
        return new SettingsService(file, broadcaster);
    }

    /** 备用：直接用 system property + 默认 home，便于测试覆盖 */
    static Path fallbackHome() {
        // fix-agent-home-isolation：走单一入口，使测试隔离（agent.demo.home）也能盖住 settings
        return com.example.agent.config.AgentPaths.agentHome();
    }
}
