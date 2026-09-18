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
        Path home = SettingsFile.resolveDefaultHome(cliHome, System.getenv("AGENT_DEMO_HOME"));
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
        return Paths.get(System.getProperty("user.home"), ".agent-demo");
    }
}
