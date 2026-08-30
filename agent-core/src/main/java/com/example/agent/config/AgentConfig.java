package com.example.agent.config;

import java.util.List;

/**
 * agent-demo 顶层配置（详见 design.md §9）。
 *
 * <p>三层优先级：环境变量 &gt; {@code ~/.agent-demo/config.yaml} &gt; 内置默认（{@link #defaults()}）。
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
        Mcp mcp) {

    /**
     * LLM provider 配置。
     *
     * @param type provider 类型（v0.1 固定 "deepseek"）
     * @param apiKey API key（优先用环境变量）
     * @param baseUrl API base URL
     * @param model 模型名（deepseek-chat / deepseek-reasoner）
     * @param maxOutputTokens 最大输出 token
     */
    public record Provider(
            String type, String apiKey, String baseUrl, String model, int maxOutputTokens) {}

    /**
     * 权限策略。
     *
     * @param defaultPolicy 全局默认策略名（"ask-write" / "allow-all"）
     * @param shellDenylist shell 全局黑名单（合并 ShellAdapter.defaultDenylist）
     */
    public record Permission(String defaultPolicy, List<String> shellDenylist) {}

    /**
     * 成本控制阈值。
     *
     * @param inputPerMTokens 输入价格（元/M tokens）
     * @param outputPerMTokens 输出价格（元/M tokens）
     * @param warnThreshold 告警阈值（元）
     * @param stopThreshold 停止阈值（元）
     */
    public record Cost(
            double inputPerMTokens,
            double outputPerMTokens,
            double warnThreshold,
            double stopThreshold) {}

    /**
     * 上下文压缩参数。
     *
     * @param compactBuffer 提前压缩 buffer（tokens）
     * @param maxConsecutiveCompactFailures 连续失败熔断阈值
     */
    public record Context(int compactBuffer, int maxConsecutiveCompactFailures) {}

    /**
     * Shell 沙箱参数。
     *
     * @param timeoutMs 单次命令硬超时（毫秒）
     * @param maxOutputBytes 输出上限（字节，stdout+stderr 累计）
     */
    public record Shell(int timeoutMs, int maxOutputBytes) {}

    /**
     * 会话结构化日志配置（详见 logging-design.md）。
     *
     * <p>{@code dir} 是会话结构化日志根目录，独立于 SLF4J 通用日志 {@code app.log}。
     *
     * @param enabled 是否写会话结构化日志；关闭时 {@code SessionLogger} 为 no-op
     * @param dir 会话日志根目录（默认 {@code <cwd>/logs/}，项目内专门目录，不被 git 跟踪）
     * @param resultMaxChars 工具结果在 {@code session.jsonl} / {@code tools.log} 中的截断上限（字符）
     * @param snapshotMaxChars context/snapshot 事件中 systemPrompt 的截断上限（字符，默认 2000）
     * @param retentionMaxAgeDays 会话日志目录保留天数（超过则清理，默认 30）
     * @param retentionKeepSessions 会话日志目录数量上限（超限删最旧，默认 50）
     */
    public record Logging(
            boolean enabled,
            String dir,
            int resultMaxChars,
            int snapshotMaxChars,
            int retentionMaxAgeDays,
            int retentionKeepSessions) {}

    /**
     * Memory 配置。
     *
     * @param sideQuery 语义召回（sideQuery）配置
     */
    public record Memory(SideQuery sideQuery) {}

    /**
     * sideQuery 语义召回配置（见 add-memory-sidequery change）。
     *
     * @param enabled 是否启用 sideQuery 语义补充；关闭时仅字面 token 重叠召回
     * @param maxCandidates 送入 sideQuery 的候选条目数上限（控制 prompt 长度）
     * @param minCandidates 触发 sideQuery 所需的最小候选条目数（低于则直接 l字面结果）
     */
    public record SideQuery(boolean enabled, int maxCandidates, int minCandidates) {}

    /**
     * MCP 客户端配置。
     *
     * @param servers MCP server 列表
     */
    public record Mcp(List<McpServer> servers) {}

    /**
     * 单个 MCP server 配置。
     *
     * @param name server 名（用于日志/标识）
     * @param url  MCP server 的 Streamable HTTP endpoint URL
     */
    public record McpServer(String name, String url) {}

    /**
     * v0.1 内置默认配置。
     *
     * @return 默认 {@link AgentConfig}
     */
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
                new Mcp(List.of()));
    }
}
