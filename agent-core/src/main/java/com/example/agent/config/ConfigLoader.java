package com.example.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                    mergeLogging(base.logging(), map),
                    mergeMemory(base.memory(), map),
                    mergeMcp(base.mcp(), map),
                    mergeWorktree(base.worktree(), map),
                    mergePlugins(base.plugins(), map),
                    mergeSearch(base.search(), map));
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
                base.logging(),
                base.memory(),
                base.mcp(),
                base.worktree(),
                base.plugins(),
                base.search());
    }

    /**
     * 取第一个非空白的字符串。
     *
     * @param a 候选 1
     * @param b 候选 2
     * @return 第一个非 null 且非空白；都为空返回 {@code b}（可能为 {@code null}）
     */


    @SuppressWarnings("unchecked")
    private List<AgentConfig.PluginConfig> mergePlugins(List<AgentConfig.PluginConfig> base, Map<String, Object> map) {
        if (!map.containsKey("plugins")) return base;
        Object raw = map.get("plugins");
        if (!(raw instanceof List)) return base;
        List<Map<String, Object>> entries = (List<Map<String, Object>>) raw;
        List<AgentConfig.PluginConfig> out = new java.util.ArrayList<>(base);
        for (Map<String, Object> entry : entries) {
            String className = (String) entry.get("className");
            Object configObj = entry.get("config");
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = configObj instanceof Map
                    ? (Map<String, Object>) configObj
                    : Map.of();
            if (className == null || className.isBlank()) continue;
            out.add(new AgentConfig.PluginConfig(className.trim(), configMap));
        }
        return out;
    }
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
     * 合并 user yaml 的 {@code memory.sideQuery} 段到 base（缺失段保持 base 值）。
     *
     * @param base 当前 memory 配置
     * @param map user yaml 顶层字典
     * @return 合并后的 {@link AgentConfig.Memory}
     */
    @SuppressWarnings("unchecked")
    private AgentConfig.Memory mergeMemory(AgentConfig.Memory base, Map<String, Object> map) {
        Object seg = map.get("memory");
        if (!(seg instanceof Map<?, ?> mm)) return base;
        Map<String, Object> m = (Map<String, Object>) mm;
        AgentConfig.SideQuery baseSql = base.sideQuery();
        Object sq = m.get("sideQuery");
        AgentConfig.SideQuery sql;
        if (sq instanceof Map<?, ?> sqm) {
            Map<String, Object> s = (Map<String, Object>) sqm;
            boolean enabled = s.containsKey("enabled") ? BoolVal(s.get("enabled"), baseSql.enabled()) : baseSql.enabled();
            int maxCandidates = s.containsKey("maxCandidates") ? intVal(s, "maxCandidates", baseSql.maxCandidates()) : baseSql.maxCandidates();
            int minCandidates = s.containsKey("minCandidates") ? intVal(s, "minCandidates", baseSql.minCandidates()) : baseSql.minCandidates();
            sql = new AgentConfig.SideQuery(enabled, maxCandidates, minCandidates);
        } else {
            sql = baseSql;
        }
        return new AgentConfig.Memory(sql);
    }

    /**
     * 合并 user yaml 的 {@code mcp.servers} 段到 base（缺失段保持 base 值）。
     *
     * @param base 当前 mcp 配置
     * @param map user yaml 顶层字典
     * @return 合并后的 {@link AgentConfig.Mcp}
     */
    @SuppressWarnings("unchecked")
    private AgentConfig.Mcp mergeMcp(AgentConfig.Mcp base, Map<String, Object> map) {
        Object seg = map.get("mcp");
        if (!(seg instanceof Map<?, ?> mm)) return base;
        Object servers = ((Map<String, Object>) mm).get("servers");
        if (!(servers instanceof List<?> list)) return base;
        java.util.List<AgentConfig.McpServer> result = new java.util.ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> sm = (Map<String, Object>) m;
            String name = str(sm, "name", null);
            String url = str(sm, "url", null);
            if (name == null || name.isBlank() || url == null || url.isBlank()) continue;
            result.add(new AgentConfig.McpServer(name.trim(), url.trim()));
        }
        return new AgentConfig.Mcp(result);
    }

    /**
     * 合并 user yaml 的 {@code worktree} 段到 base（缺失段保持 base 值）。
     *
     * @param base 当前 worktree 配置
     * @param map user yaml 顶层字典
     * @return 合并后的 {@link AgentConfig.Worktree}
     */
    @SuppressWarnings("unchecked")
    private AgentConfig.Worktree mergeWorktree(AgentConfig.Worktree base, Map<String, Object> map) {
        Object seg = map.get("worktree");
        if (!(seg instanceof Map<?, ?> mm)) return base;
        Map<String, Object> m = (Map<String, Object>) mm;
        boolean enabled =
                m.containsKey("enabled") ? BoolVal(m.get("enabled"), base.enabled()) : base.enabled();
        String baseDir = str(m, "baseDir", base.baseDir());
        return new AgentConfig.Worktree(enabled, baseDir);
    }

    /**
     * 合并 user yaml 的 {@code search:} 段到 base（缺失段保持 base 值）。
     *
     * @param base 当前 search 配置
     * @param map  user yaml 顶层字典
     * @return 合并后的 {@link AgentConfig.Search}
     */
    @SuppressWarnings("unchecked")
    private AgentConfig.Search mergeSearch(AgentConfig.Search base, Map<String, Object> map) {
        Object seg = map.get("search");
        if (!(seg instanceof Map<?, ?> sm)) return base;
        Map<String, Object> m = (Map<String, Object>) sm;
        return new AgentConfig.Search(
                str(m, "provider", base.provider()),
                intVal(m, "maxResults", base.maxResults()),
                intVal(m, "timeoutMs", base.timeoutMs()));
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



