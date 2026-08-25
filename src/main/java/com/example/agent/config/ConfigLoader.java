package com.example.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 三层优先级加载：env > user config > defaults（详见 design.md §9）。
 */
public class ConfigLoader {
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public AgentConfig load(Path userConfigPath) {
        AgentConfig base = AgentConfig.defaults();
        if (userConfigPath != null && Files.exists(userConfigPath)) {
            base = mergeYaml(base, userConfigPath);
        }
        return applyEnv(base);
    }

    private AgentConfig mergeYaml(AgentConfig base, Path yamlPath) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = yaml.readValue(yamlPath.toFile(), Map.class);
            AgentConfig.Provider p = base.provider();
            if (map.containsKey("provider")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pm = (Map<String, Object>) map.get("provider");
                p = new AgentConfig.Provider(
                        str(pm, "type", p.type()),
                        str(pm, "apiKey", p.apiKey()),
                        str(pm, "baseUrl", p.baseUrl()),
                        str(pm, "model", p.model()),
                        intVal(pm, "maxOutputTokens", p.maxOutputTokens()));
            }
            return new AgentConfig(p, base.permission(), base.cost(), base.context(), base.shell(), base.memoryInject());
        } catch (IOException e) {
            throw new RuntimeException("加载配置失败: " + yamlPath, e);
        }
    }

    private AgentConfig applyEnv(AgentConfig base) {
        AgentConfig.Provider p = base.provider();
        String apiKey = firstNonBlank(System.getenv(EnvKeys.DEEPSEEK_API_KEY), p.apiKey());
        String baseUrl = firstNonBlank(System.getenv(EnvKeys.DEEPSEEK_BASE_URL), p.baseUrl());
        String model = firstNonBlank(System.getenv(EnvKeys.AGENT_MODEL), p.model());
        int maxOut = p.maxOutputTokens();
        String m = System.getenv(EnvKeys.AGENT_MAX_OUTPUT_TOKENS);
        if (m != null && !m.isBlank()) {
            try { maxOut = Integer.parseInt(m); } catch (NumberFormatException ignored) {}
        }
        return new AgentConfig(
                new AgentConfig.Provider(p.type(), apiKey, baseUrl, model, maxOut),
                base.permission(), base.cost(), base.context(), base.shell(), base.memoryInject());
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static String str(Map<String, Object> m, String k, String d) {
        Object v = m.get(k);
        return v == null ? d : v.toString();
    }

    private static int intVal(Map<String, Object> m, String k, int d) {
        Object v = m.get(k);
        return v == null ? d : ((Number) v).intValue();
    }
}