package com.example.agent.core;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.Message;
import com.example.agent.signal.AbortSignal;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;
import com.example.agent.memory.MemoryDir;
import com.example.agent.memory.MemoryPromptBuilder;
import com.example.agent.memory.MemoryScope;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.prompt.SystemPromptBuilder;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.file.LsTool;
import com.example.agent.tools.shell.BashAdapter;
import com.example.agent.tools.shell.CmdAdapter;
import com.example.agent.tools.shell.ShellAdapter;
import com.example.agent.tools.shell.ShellTool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * AgentLoop 组装工厂（add-web-ui-v0-1 / D2 复用同一组 bean）。
 *
 * <p>把 {@code ChatCommand.run()} 里「provider 路由 + 工具注册 + system prompt 组装 + AgentLoop
 * 构造」这段装配逻辑抽成可复用方法：CLI 与 web 共用，保证行为一致（尤其 provider 类型路由与
 * tool 沙箱参数），避免 web 层重复一套装配导致差异。
 *
 * <p>调用方仍自行决定：history（每会话独立）、printer、sink、confirmer（CLI 用 stdin 交互、
 * web 用 PermissionBridge），以及 recorder / agentDataDir。
 */
public final class AgentLoopFactory {

    /** 单轮最大工具调用次数（与 CLI 对齐）。 */
    public static final int MAX_TOOL_ITERATIONS = 25;

    private AgentLoopFactory() {}

    /**
     * 按 provider.type() 路由到具体实现（CLI 与 web 共用同一路由）。
     *
     * @param cfg 已加载的配置
     * @param resolvedKey 解析后的 API key（CLI 的优先级链已在调用方完成）
     * @return 对应 provider 实例
     */
    public static LlmProvider buildProvider(AgentConfig cfg, String resolvedKey) {
        return switch (cfg.provider().type() == null
                ? "deepseek"
                : cfg.provider().type().toLowerCase()) {
            case "deepseek" -> new com.example.agent.provider.deepseek.DeepSeekProvider(resolvedKey);
            case "minimax" -> new com.example.agent.provider.minimax.MiniMaxProvider(resolvedKey);
            default ->
                    throw new IllegalArgumentException(
                            "未知 provider 类型: " + cfg.provider().type() + "（支持 deepseek / minimax）");
        };
    }

    /**
     * 注册运行时工具集（内存工具 + shell + ls），并返回注册表。
     *
     * <p>工具沙箱参数来自 {@code cfg.shell()}，跨平台 adapter（Windows=cmd，其余=bash）。
     *
     * @param cfg 已加载的配置
     * @return 组装好的 {@link ToolRegistry}
     */
    public static ToolRegistry buildTools(AgentConfig cfg) {
        ToolRegistry tools = new ToolRegistry();
        ToolRegistry.registerMemoryTools(tools);
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        ShellAdapter adapter = windows ? new CmdAdapter() : new BashAdapter();
        int timeoutSec = Math.max(1, cfg.shell().timeoutMs() / 1000);
        tools.register(new ShellTool(adapter, timeoutSec, cfg.shell().maxOutputBytes(), true));
        tools.register(new LsTool());
        // Skills：发现用户级 + 项目级技能并注册为工具
        String userHome = System.getenv("AGENT_DEMO_HOME") != null
                        && !System.getenv("AGENT_DEMO_HOME").isBlank()
                ? System.getenv("AGENT_DEMO_HOME")
                : System.getProperty("user.home");
        String cwd = System.getProperty("user.dir");
        java.util.List<Path> skillRoots = java.util.List.of(
                Paths.get(userHome, ".agent-demo", "skills"),
                Paths.get(cwd, ".agent-demo", "skills"));
        ToolRegistry.registerSkillTools(
                tools, com.example.agent.skill.SkillCatalog.discover(skillRoots));
        // MCP：从 config 连接 MCP server 并融合其工具
        AgentConfig.Mcp mcp = cfg.mcp();
        if (mcp != null && mcp.servers() != null && !mcp.servers().isEmpty()) {
            java.util.List<com.example.agent.mcp.McpClient> clients = new java.util.ArrayList<>();
            for (AgentConfig.McpServer s : mcp.servers()) {
                clients.add(com.example.agent.mcp.McpClient.create(s.url(), s.name()));
            }
            ToolRegistry.registerMcpTools(tools, clients);
        }
        return tools;
    }

    /**
     * 组装 system prompt（模型无关默认模板 + provider/model 元数据 + 长期记忆 + 存储说明 + 用户覆盖）。
     *
     * @param cfg 已加载的配置
     * @param resolvedModel 解析后的模型名
     * @param override 用户 --system-prompt 覆盖（可 null）
     * @return 完整 system prompt 文本
     */
    public static String buildSystemPrompt(
            AgentConfig cfg, String resolvedModel, String override) {
        return buildSystemPrompt(cfg, resolvedModel, override, null);
    }

    /**
     * 组装 system prompt（含 sideQuery 语义召回）。
     *
     * @param provider LLM provider（可空；null 时 memory 注入走纯字面召回，不调 sideQuery）
     */
    public static String buildSystemPrompt(
            AgentConfig cfg, String resolvedModel, String override, LlmProvider provider) {
        String providerName = cfg.provider().type() == null
                ? "deepseek"
                : cfg.provider().type().toLowerCase();
        String userHome = System.getenv("AGENT_DEMO_HOME") != null
                        && !System.getenv("AGENT_DEMO_HOME").isBlank()
                ? System.getenv("AGENT_DEMO_HOME")
                : System.getProperty("user.home");
        // 三 scope 记忆：USER（跨项目）+ PROJECT（随项目仓库）+ LOCAL（本次会话）
        String cwd = System.getProperty("user.dir");
        java.util.List<MemoryDir> memoryDirs = java.util.List.of(
                MemoryDir.forScope(MemoryScope.USER, userHome, cwd),
                MemoryDir.forScope(MemoryScope.PROJECT, userHome, cwd),
                MemoryDir.forScope(MemoryScope.LOCAL, userHome, cwd));
        MemoryPromptBuilder builder = new MemoryPromptBuilder(memoryDirs.get(0));
        String memorySection;
        AgentConfig.SideQuery sideQuery = cfg.memory() != null ? cfg.memory().sideQuery() : null;
        if (provider != null && sideQuery != null) {
            com.example.agent.memory.MemoryRetriever retriever =
                    new com.example.agent.memory.MemoryRetriever(
                            provider, resolvedModel, new com.example.agent.memory.MemoryRecall(), sideQuery);
            memorySection = builder.build("", memoryDirs, retriever,
                    String.join("\n", cfg.memoryInject()), 5);
        } else {
            memorySection = builder.build(memoryDirs, String.join("\n", cfg.memoryInject()));
        }
        String storageSection = buildStorageSection(cfg, userHome);
        return new SystemPromptBuilder()
                .build(
                        providerName,
                        resolvedModel,
                        memorySection,
                        storageSection,
                        List.of(),
                        override);
    }

    /**
     * 装配 {@link AgentLoop}（用户可插拔 history / printer / sink / confirmer / agentDataDir）。
     *
     * @param cfg 已加载的配置
     * @param provider provider（{@link #buildProvider}）
     * @param tools 工具注册表（{@link #buildTools}）
     * @param history 初始消息历史（每会话独立）
     * @param printer 流式打印机（CLI=stdout；web 可传 no-op，因 web 用 {@code SessionLogSink} 粗粒度通知）
     * @param model 模型名
     * @param sink 会话日志观察者（CLI=recorder；web=SseSessionLogSink；null=no-op）
     * @param agentDataDir agent 数据目录（可选）
     * @param confirmer 权限确认器（CLI=stdin；web=PermissionBridge；null=fail-closed 拒绝）
     * @return 组装好的 {@link AgentLoop}
     */
    public static AgentLoop buildLoop(
            AgentConfig cfg,
            LlmProvider provider,
            ToolRegistry tools,
            MessageHistory history,
            StreamingPrinter printer,
            String model,
            SessionLogSink sink,
            Path agentDataDir,
            PermissionConfirmer confirmer) {
        return buildLoop(cfg, provider, tools, history, printer, model, sink, agentDataDir, confirmer, null);
    }

    /**
     * 装配 {@link AgentLoop}（带中断信号，web abort 用）。
     *
     * @param abortSignal 中断信号（可 null = 永不中断）
     */
    public static AgentLoop buildLoop(
            AgentConfig cfg,
            LlmProvider provider,
            ToolRegistry tools,
            MessageHistory history,
            StreamingPrinter printer,
            String model,
            SessionLogSink sink,
            Path agentDataDir,
            PermissionConfirmer confirmer,
            AbortSignal abortSignal) {
        Path workingDir = resolveWorkingDir(cfg);
        return new AgentLoop(
                provider,
                tools,
                history,
                printer,
                MAX_TOOL_ITERATIONS,
                model,
                workingDir,
                buildSystemPrompt(cfg, model, null, provider),
                sink,
                agentDataDir,
                confirmer,
                abortSignal);
    }

    /**
     * 解析 agent 工作目录：worktree 模式开启时创建独立 worktree 并返回其路径；否则返回项目根。
     *
     * @param cfg 已加载的配置
     * @return workingDir（worktree 路径或项目根）
     */
    private static Path resolveWorkingDir(AgentConfig cfg) {
        AgentConfig.Worktree wt = cfg.worktree();
        if (wt != null && wt.enabled()) {
            com.example.agent.worktree.WorktreeManager mgr =
                    new com.example.agent.worktree.WorktreeManager(
                            Paths.get(System.getProperty("user.dir")), Paths.get(wt.baseDir()));
            String name = "sess-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            Path path = mgr.create(name, null);
            if (path != null) return path;
            // worktree 创建失败 → 回退项目根
        }
        return Paths.get(System.getProperty("user.dir"));
    }

    /**
     * 组装「运行时存储位置」说明段（从 ChatCommand 抽取，保证 CLI/web 提示一致）。
     */
    public static String buildStorageSection(AgentConfig cfg, String userHome) {
        String logsDir = cfg.logging() != null && cfg.logging().dir() != null
                ? cfg.logging().dir()
                : Paths.get(System.getProperty("user.dir"), "logs").toString();
        String sessionsDir = Paths.get(userHome, ".agent-demo", "sessions").toString();
        return "- 工作目录（文件工具的相对路径均相对此解析）: `"
                + System.getProperty("user.dir")
                + "`\n"
                + "- 日志目录: `"
                + logsDir
                + "`（`app.log` 通用日志；每个会话的结构化日志在 `sessions/<会话ID>/` 下："
                + "`session.jsonl` / `chat.log` / `thinking.log` / `tools.log`）\n"
                + "- 会话存档目录: `"
                + sessionsDir
                + "`（`<会话ID>.jsonl`）";
    }
}
