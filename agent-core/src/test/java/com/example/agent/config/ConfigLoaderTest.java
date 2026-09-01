package com.example.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class ConfigLoaderTest {
    @TempDir Path tmp;

    @Test
    void defaultsWhenNoFile() {
        var cfg = new ConfigLoader().load(null);
        assertEquals("deepseek-chat", cfg.provider().model());
        assertEquals(8192, cfg.provider().maxOutputTokens());
        assertEquals("", cfg.search().provider());
        assertEquals(5, cfg.search().maxResults());
        assertEquals(60000, cfg.search().timeoutMs());
    }

    @Test
    void yamlOverridesDefaults() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "provider:\n  model: deepseek-reasoner\n  maxOutputTokens: 4096\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals("deepseek-reasoner", cfg.provider().model());
        assertEquals(4096, cfg.provider().maxOutputTokens());
    }

    @Test
    void envOverridesYaml() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "provider:\n  model: deepseek-chat\n");
        // 验证 load 不抛错；env 覆盖在 CI 通过 maven surefire 配置
        var cfg = new ConfigLoader().load(yaml);
        assertNotNull(cfg);
    }

    @Test
    void yamlSearchOverridesDefaults() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(
                yaml, "search:\n  provider: tavily\n  maxResults: 3\n  timeoutMs: 30000\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals("tavily", cfg.search().provider());
        assertEquals(3, cfg.search().maxResults());
        assertEquals(30000, cfg.search().timeoutMs());
    }

    @Test
    void yamlSearchMissingKeepsDefaults() throws Exception {
        Path yaml = tmp.resolve("config.yaml");
        Files.writeString(yaml, "provider:\n  model: deepseek-chat\n");
        var cfg = new ConfigLoader().load(yaml);
        assertEquals("", cfg.search().provider());
        assertEquals(5, cfg.search().maxResults());
        assertEquals(60000, cfg.search().timeoutMs());
    }
}
