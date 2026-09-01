package com.example.agent.config;

import java.util.List;
import java.util.Map;

/**
 * agent-demo 顶层配置（详见 design.md §9）。
 *
 * <p>三层优先级：环境变量 > {@code ~/.agent-demo/config.yaml} > 内置默认（{@link #defaults()}）。
 *
 * @param provider LLM provider 配置（DeepSeek / OpenAI / 自定义）
 * @param permission 权限策略
 * @param cost 成本控制阈值（按 model 分桶，v0.1 简化）
 * @param context 上下文压缩参数
 * @param shell ShellTool 沙箱参数
 * @param memoryInject 注入到 system prompt 的额外 memory 指引
 * @param logging 会话结构化日志配置
 * @param memory Memory 相关配置（含 sideQuery 语义召回）
 * @param mcp MCP 客户端配置
 * @param worktree Worktree 隔离工作区配置
 * @param plugins 插件列表（add-plugin-system v1.0）。每个 Plugin 启动时由 PluginManager.init() 串行 init, 关闭时反序 close.
 * @param search 网络搜索配置（add-web-search-tool）：provider 选择 + 结果数 + 超时
 */
public record AgentConfig(
        Provider provider,
        Permission permission,
        Cost cost,
        Context context,
        Shell shell,
        List<String> memoryInject,
        Logging logging,
        Memory memory,
        Mcp mcp,
        Worktree worktree,
        List<PluginConfig> plugins,
        Search search) {

    public record PluginConfig(String className, Map<String, Object> config) {
        public PluginConfig {
            if (className == null || className.isBlank()) {
                throw new IllegalArgumentException("PluginConfig.className 不能为空");
            }
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }

    public record Provider(
            String type, String apiKey, String baseUrl, String model, int maxOutputTokens) {}

    public record Permission(String defaultPolicy, List<String> shellDenylist) {}

    public record Cost(
            double inputPerMTokens,
            double outputPerMTokens,
            double warnThreshold,
            double stopThreshold) {}

    public record Context(int compactBuffer, int maxConsecutiveCompactFailures) {}

    public record Shell(int timeoutMs, int maxOutputBytes) {}

    public record Logging(
            boolean enabled,
            String dir,
            int resultMaxChars,
            int snapshotMaxChars,
            int retentionMaxAgeDays,
            int retentionKeepSessions) {}

    public record Memory(SideQuery sideQuery) {}

    public record SideQuery(boolean enabled, int maxCandidates, int minCandidates) {}

    public record Mcp(List<McpServer> servers) {
        public Mcp {
            servers = servers == null ? List.of() : List.copyOf(servers);
        }
    }

    public record McpServer(String name, String url) {}

    public record Worktree(boolean enabled, String baseDir) {}

    /**
     * 网络搜索配置（add-web-search-tool）。
     *
     * @param provider   provider 名（{@code deepseek} / {@code tavily}）；空字符串表示按模型自动推断
     * @param maxResults 默认最大结果数
     * @param timeoutMs  搜索超时（毫秒）
     */
    public record Search(String provider, int maxResults, int timeoutMs) {}

    public static AgentConfig defaults() {
        return new AgentConfig(
                new Provider("deepseek", "", "https://api.deepseek.com", "deepseek-chat", 8192),
                new Permission(
                        "ask-write",
                        List.of(
                                "rm -rf /",
                                "mkfs",
                                "dd if=.*of=/dev/.*",
                                "chmod -R 777 /",
                                "shutdown",
                                "reboot",
                                "format",
                                "rd /s /q C:\\",
                                "del /f /s /q C:\\*",
                                "diskpart",
                                "bcdedit",
                                "reg delete HKLM")),
                new Cost(2.0, 8.0, 4.0, 5.0),
                new Context(8000, 3),
                new Shell(120_000, 1_000_000),
                List.of(),
                new Logging(
                        true,
                        System.getProperty("user.dir") + "/logs/",
                        30_000,
                        2_000,
                        30,
                        50),
                new Memory(new SideQuery(true, 8, 3)),
                new Mcp(List.of()),
                new Worktree(
                        false,
                        System.getProperty("user.home") + "/.agent-demo/worktrees"),
                List.of(),
                new Search("", 5, 60_000));
    }
}
