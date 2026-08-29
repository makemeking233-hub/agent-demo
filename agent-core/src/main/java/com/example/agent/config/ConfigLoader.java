package com.example.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** 三层优先级配置加载：env &gt; user config &gt; defaults（详见 design.md §9）。 */
public class ConfigLoader {
    /** YAML 反序列化器 */
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    /**
     * 加载配置（合并 user yaml 后再应用 env override）。
     *
     * @param userConfigPath 用户配置文件路径（{@code ~/.agent-demo/config.yaml}），可空
     * @return 合并后的 {@link AgentConfig}
     */
    public AgentConfig load(Path userConfigPath) {
        AgentConfig base = AgentConfig.defaults();
        if (userConfigPath != null && Files.exists(userConfigPath)) {
            base = mergeYaml(base, userConfigPath);
        }
        return applyEnv(base);
    }

    /**
     * 把 user yaml 的 provider 段覆盖到 base。
     *
     * @param base 当前配置
     * @param yamlPath yaml 文件路径
     * @return 合并后的 {@link AgentConfig}
     */
    private AgentConfig mergeYaml(AgentConfig base, Path yamlPath) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = yaml.readValue(yamlPath.toFile(), Map.class);
            AgentConfig.Provider p = base.provider();
            if (map.containsKey("provider")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pm = (Map<String, Object>) map.get("provider");
                p =
                        new AgentConfig.Provider(
                                str(pm, "type", p.type()),
                                str(pm, "apiKey", p.apiKey()),
                                str(pm, "baseUrl", p.baseUrl()),
                                str(pm, "model", p.model()),
                                intVal(pm, "maxOutputTokens", p.maxOutputTokens()));
            }
            return new AgentConfig(
                    p,
                    base.permission(),
                    base.cost(),
                    base.context(),
                    base.shell(),
                    base.memoryInject(),
                    mergeLogging(base.logging(), map));
        } catch (IOException e) {
            throw new RuntimeException("加载配置失败: " + yamlPath, e);
        }
    }

    /**
     * 把 env 变量覆盖到 base（仅 provider 段）。
     *
     * @param base 当前配置
     * @return 应用 env 后的 {@link AgentConfig}
     */
    private AgentConfig applyEnv(AgentConfig base) {
        AgentConfig.Provider p = base.provider();
        String apiKey = firstNonBlank(System.getenv(EnvKeys.DEEPSEEK_API_KEY), p.apiKey());
        String baseUrl = firstNonBlank(System.getenv(EnvKeys.DEEPSEEK_BASE_URL), p.baseUrl());
        String model = firstNonBlank(System.getenv(EnvKeys.AGENT_MODEL), p.model());
        int maxOut = p.maxOutputTokens();
        String m = System.getenv(EnvKeys.AGENT_MAX_OUTPUT_TOKENS);
        if (m != null && !m.isBlank()) {
            try {
                maxOut = Integer.parseInt(m);
            } catch (NumberFormatException ignored) {
            }
        }
        return new AgentConfig(
                new AgentConfig.Provider(p.type(), apiKey, baseUrl, model, maxOut),
                base.permission(),
                base.cost(),
                base.context(),
                base.shell(),
                base.memoryInject(),
                base.logging());
    }

    /**
     * 取第一个非空白的字符串。
     *
     * @param a 候选 1
     * @param b 候选 2
     * @return 第一个非 null 且非空白；都为空返回 {@code b}（可能为 {@code null}）
     */
    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    /**
     * 合并 user yaml 的 {@code logging:} 段到 base（缺失段保持 base 值）。
     *
     * @param base 当前 logging 配置
     * @param map user yaml 顶层字典
     * @return 合并后的 {@link AgentConfig.Logging}
     */
    @SuppressWarnings("unchecked")
    private AgentConfig.Logging mergeLogging(AgentConfig.Logging base, Map<String, Object> map) {
        Object seg = map.get("logging");
        if (!(seg instanceof Map<?, ?> lm)) return base;
        Map<String, Object> m = (Map<String, Object>) lm;
        boolean enabled = m.containsKey("enabled") ? BoolVal(m.get("enabled"), base.enabled()) : base.enabled();
        String dir = str(m, "dir", base.dir());
        int resultMaxChars = m.containsKey("resultMaxChars")
                ? intVal(m, "resultMaxChars", base.resultMaxChars())
                : base.resultMaxChars();
        int snapshotMaxChars = m.containsKey("snapshotMaxChars")
                ? intVal(m, "snapshotMaxChars", base.snapshotMaxChars())
                : base.snapshotMaxChars();
        int retentionMaxAgeDays = m.containsKey("retentionMaxAgeDays")
                ? intVal(m, "retentionMaxAgeDays", base.retentionMaxAgeDays())
                : base.retentionMaxAgeDays();
        int retentionKeepSessions = m.containsKey("retentionKeepSessions")
                ? intVal(m, "retentionKeepSessions", base.retentionKeepSessions())
                : base.retentionKeepSessions();
        return new AgentConfig.Logging(
                enabled, dir, resultMaxChars, snapshotMaxChars, retentionMaxAgeDays, retentionKeepSessions);
    }

    /**
     * 兼容布尔值（yaml 可能是 Boolean 或字符串），缺失返回默认。
     *
     * @param v 值
     * @param d 默认值
     * @return 布尔值
     */
    private static boolean BoolVal(Object v, boolean d) {
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(v.toString());
        return d;
    }

    /**
     * Map 读 string，缺失返回默认值。
     *
     * @param m 字典
     * @param k 键
     * @param d 默认值
     * @return 值字符串；缺失时返回 {@code d}
     */
    private static String str(Map<String, Object> m, String k, String d) {
        Object v = m.get(k);
        return v == null ? d : v.toString();
    }

    /**
     * Map 读 int，缺失返回默认值。
     *
     * @param m 字典
     * @param k 键
     * @param d 默认值
     * @return 整数值；缺失时返回 {@code d}
     */
    private static int intVal(Map<String, Object> m, String k, int d) {
        Object v = m.get(k);
        return v == null ? d : ((Number) v).intValue();
    }
}
