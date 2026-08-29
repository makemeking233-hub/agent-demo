package com.example.agent.cli;

import com.example.agent.AbortSignal;
import com.example.agent.config.AgentConfig;
import com.example.agent.config.ConfigLoader;
import com.example.agent.core.AgentLoop;
import com.example.agent.core.ContextCompressor;
import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogger;
import com.example.agent.log.SessionRecorder;
import com.example.agent.log.SessionId;
import com.example.agent.memory.MemoryDir;
import com.example.agent.memory.MemoryPromptBuilder;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.prompt.SystemPromptBuilder;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.session.SessionStore;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.file.LsTool;
import com.example.agent.tools.shell.BashAdapter;
import com.example.agent.tools.shell.CmdAdapter;
import com.example.agent.tools.shell.ShellAdapter;
import com.example.agent.tools.shell.ShellTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REPL main loop (v0.1 simplified):
 *
 * <ul>
 *   <li>Read stdin input, dispatch to {@link AgentLoop#processTurn(Message.User)}
 *   <li>Slash commands via {@link SlashCommand}: /help /clear /quit /history
 *   <li>Supports --input (one-shot for E2E tests)
 *   <li>Supports --auto-approve-write (skip write permissions for E2E)
 * </ul>
 *
 * <p>v0.2 upgrade: JLine3 raw mode + history completion + Ctrl+C interrupt (see design.md §17).
 */
@Component
@Command(
        name = "chat",
        mixinStandardHelpOptions = true,
        description = "Start interactive REPL with multi-turn dialog")
public class ChatCommand implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ChatCommand.class);

    /**
     * Default max tool iterations per turn (aligns with Claude Code)
     */
    private static final int DEFAULT_MAX_TOOL_ITERATIONS = 25;

    /**
     * --model：覆盖默认模型名
     */
    @Option(
            names = {"--model"},
            description = "Override default model")
    String model;

    /**
     * --api-key：覆盖 API key（本次会话优先）
     */
    @Option(
            names = {"--api-key"},
            description = "Override API key (this session only)")
    String apiKey;

    /**
     * --system-prompt：覆盖默认 system prompt
     */
    @Option(
            names = {"--system-prompt"},
            description = "Override default system prompt")
    String systemPrompt;

    /**
     * --input：E2E 测试用一次性输入（跳过 REPL 循环）
     */
    @Option(names = "--input", description = "TEST: inject one-shot input (skips REPL loop)")
    String injectedInput;

    /**
     * --auto-approve-write：E2E 测试用（跳过写权限确认）
     */
    @Option(
            names = "--auto-approve-write",
            description = "TEST: skip write permission confirmation")
    boolean autoApproveWrite;

    /**
     * Spring profile 注入的 api-key（来自 application-local.yml）
     */
    @Value("${agent.provider.api-key:}")
    String springApiKey;

    /**
     * Spring profile 注入的 model（来自 application-local.yml）
     */
    @Value("${agent.provider.model:}")
    String springModel;

    /**
     * picocli 入口：装配 Provider / Tools / AgentLoop / Permission，启动 REPL。
     */
    @Override
    public void run() {
        AgentConfig cfg = loadConfig();
        // 优先级：CLI flag > env > application-local.yml > ~/.agent-demo/config.yaml
        String resolvedKey =
                pickFirstNonBlank(
                        apiKey,
                        System.getenv("DEEPSEEK_API_KEY"),
                        springApiKey,
                        cfg.provider().apiKey());
        String resolvedModel =
                pickFirstNonBlank(
                        model, System.getenv("AGENT_MODEL"), springModel, cfg.provider().model());
        // baseUrl 由具体 Provider 内部决定（DeepSeek / MiniMax 各自硬编码），此处不再读 cfg
        // 环境变量 base URL 暂时未使用（v0.2 可加 provider-specific 覆盖）

        // 按 provider.type() 路由到具体实现
        LlmProvider provider =
                switch (cfg.provider().type() == null
                        ? "deepseek"
                        : cfg.provider().type().toLowerCase()) {
                    case "deepseek" -> new com.example.agent.provider.deepseek.DeepSeekProvider(
                            resolvedKey);
                    case "minimax" -> new com.example.agent.provider.minimax.MiniMaxProvider(
                            resolvedKey);
                    default -> throw new IllegalArgumentException(
                            "未知 provider 类型: " + cfg.provider().type() + "（支持 deepseek / minimax）");
                };
        TokenEstimator estimator = new TokenEstimator();

        // 组装系统提示词：模型无关默认模板 + provider/model 元数据 + 长期记忆 + 用户 --system-prompt 覆盖
        // （SystemPromptBuilder 内部对占位符做替换；后续新增 provider 无需改此处，除非要注入 provider 特有段）
        String providerName =
                cfg.provider().type() == null ? "deepseek" : cfg.provider().type().toLowerCase();
        String userHome =
                System.getenv("AGENT_DEMO_HOME") != null
                                && !System.getenv("AGENT_DEMO_HOME").isBlank()
                        ? System.getenv("AGENT_DEMO_HOME")
                        : System.getProperty("user.home");
        MemoryDir memoryDir = new MemoryDir(Paths.get(userHome, ".agent-demo", "memory"));
        String memorySection =
                new MemoryPromptBuilder(memoryDir).build(String.join("\n", cfg.memoryInject()));
        String storageSection = buildStorageSection(cfg, userHome);
        String systemPrompt =
                new SystemPromptBuilder()
                        .build(
                                providerName,
                                resolvedModel,
                                memorySection,
                                storageSection,
                                List.of(),
                                this.systemPrompt);

        // AtomicReference: lambda-friendly mutable holder for the active MessageHistory
        // (AtomicReference replaces single-element MessageHistory[] array used in v0.1)
        AtomicReference<MessageHistory> history =
                new AtomicReference<>(new MessageHistory(estimator));
        ToolRegistry tools = new ToolRegistry();
        ToolRegistry.registerMemoryTools(tools);
        registerShellAndLs(tools, cfg);
        StreamingPrinter printer = new StreamingPrinter();

        ContextCompressor compressor =
                new ContextCompressor(
                        provider,
                        cfg.context().compactBuffer(),
                        cfg.context().maxConsecutiveCompactFailures(),
                        resolvedModel);

        Path workingDir = Paths.get(System.getProperty("user.dir"));
        Path agentDataDir = Paths.get(userHome, ".agent-demo");
        // sessions 目录（/resume 读取用；不存在时 SlashCommand 静默返回空 list）
        Path sessionsDir = agentDataDir.resolve("sessions");
        // 会话日志 + 会话落盘（只读配置；失败降级为 no-op，不阻断对话）
        SessionRecorder recorder = buildRecorder(cfg, userHome);
        // stdin reader：REPL 主循环与权限交互确认共用（避免双 reader 缓冲冲突）
        BufferedReader reader = createReader();
        PermissionConfirmer confirmer = buildConfirmer(reader);
        AgentLoop loop =
                new AgentLoop(
                        provider,
                        tools,
                        history.get(),
                        printer,
                        DEFAULT_MAX_TOOL_ITERATIONS,
                        resolvedModel,
                        workingDir,
                        systemPrompt,
                        recorder,
                        agentDataDir,
                        confirmer);

        AtomicBoolean aborted = new AtomicBoolean(false);
        AbortSignal abortSignal = () -> aborted.get();
        SlashCommand slash = new SlashCommand();
        // Token accumulators (single-element arrays; passed by ref to SlashCommand which mutates
        // [0])
        int[] totalPrompt = {0};
        int[] totalCompletion = {0};

        ReplContext ctx =
                new ReplContext(
                        history,
                        estimator,
                        loop,
                        slash,
                        totalPrompt,
                        totalCompletion,
                        resolvedModel,
                        aborted,
                        recorder,
                        sessionsDir);
        try {
            runReplLoop(ctx, reader);
        } finally {
            closeQuietly(recorder);
            closeQuietly(reader);
        }
    }

    /**
     * 组装会话录制器：读配置决定是否启用；任一组件创建失败时降级为 {@code null}（不阻断对话）。
     *
     * @param cfg 当前配置
     * @param userHome 用户主目录
     * @return {@link SessionRecorder}（可能为 null 表示未启用会话日志）
     */
    private SessionRecorder buildRecorder(AgentConfig cfg, String userHome) {
        if (cfg.logging() == null || !cfg.logging().enabled()) return null;
        String sessionId = SessionId.newSessionId();
        SessionLogger logger = null;
        try {
            logger = new SessionLogger(cfg.logging(), sessionId);
        } catch (Exception e) {
            log.warn("初始化会话日志失败，降级为仅 app.log: {}", e.getMessage());
        }
        SessionStore store = null;
        try {
            store =
                    new SessionStore(
                            Paths.get(userHome, ".agent-demo", "sessions", sessionId + ".jsonl"),
                            50,
                            200L);
        } catch (Exception e) {
            log.warn("初始化会话存档失败，降级为不落盘: {}", e.getMessage());
        }
        if (logger == null && store == null) return null;
        return new SessionRecorder(logger, store);
    }

    /**
     * 静默关闭录制器（异常仅 warn，不阻断退出）。
     *
     * @param recorder 可空的录制器
     */
    private static void closeQuietly(SessionRecorder recorder) {
        if (recorder == null) return;
        try {
            recorder.close();
        } catch (Exception e) {
            LoggerFactory.getLogger(ChatCommand.class).warn("关闭会话录制器失败: {}", e.getMessage());
        }
    }

    /**
     * 组装「运行时存储位置」说明段，注入 system prompt。
     *
     * <p>让 Agent 能直接回答「日志 / 会话 / 记忆在哪里」，无需通过文件工具探索——文件工具被沙箱在
     * 工作目录内，够不到 {@code ~/.agent-demo}，探索会得到「路径越界」或 NoSuchFile。
     *
     * @param cfg      当前配置（读 logging.dir）
     * @param userHome agent-demo home（AGENT_DEMO_HOME 或 user.home）
     * @return 存储位置说明文本
     */
    private static String buildStorageSection(AgentConfig cfg, String userHome) {
        String logsDir =
                cfg.logging() != null && cfg.logging().dir() != null
                        ? cfg.logging().dir()
                        : Paths.get(userHome, ".agent-demo", "logs").toString();
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

    /**
     * 注册 shell 与 ls 工具（v0.1 运行时工具集：ReadFile/WriteFile/EditFile/Ls/Shell）。
     *
     * <p>按平台选 adapter（Windows=cmd，其余=bash）；超时/输出上限来自 {@code cfg.shell()}。 提取为静态方法便于单测。
     *
     * @param tools 目标注册表
     * @param cfg   当前配置
     */
    static void registerShellAndLs(ToolRegistry tools, AgentConfig cfg) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        ShellAdapter adapter = windows ? new CmdAdapter() : new BashAdapter();
        int timeoutSec = Math.max(1, cfg.shell().timeoutMs() / 1000);
        tools.register(new ShellTool(adapter, timeoutSec, cfg.shell().maxOutputBytes(), true));
        tools.register(new LsTool());
    }

    /**
     * Load user config (extracted to reduce run() nesting)
     */
    private AgentConfig loadConfig() {
        Path cfgPath = Paths.get(System.getProperty("user.home"), ".agent-demo", "config.yaml");
        return new ConfigLoader().load(cfgPath);
    }

    /**
     * REPL 主循环：读取 stdin → 派发 slash 命令或 AgentLoop。
     *
     * @param ctx    REPL 共享状态
     * @param reader stdin reader（与权限交互确认共用）
     */
    private void runReplLoop(ReplContext ctx, BufferedReader reader) {
        try {
            System.out.println(
                    "agent-demo v0.1 chat (model="
                            + ctx.resolvedModel()
                            + "), /help for commands, /quit to exit");
            String line;
            while ((line = reader.readLine()) != null && !ctx.aborted().get()) {
                if (line.isBlank()) continue;
                if (handleLine(line, ctx)) {
                    continue;
                }
                TurnResult result = null;
                try {
                    result = ctx.loop().processTurn(new Message.User(line)).block();
                } catch (Exception e) {
                    // 不让单次失败退出 REPL：打印错误让用户重试（/clear 清空历史）
                    System.err.println("\n[error] " + friendlyError(e) + "\n");
                    log.debug("REPL turn failed", e);
                }
                if (result != null) {
                    ctx.totalPrompt()[0] += result.totalPromptTokens();
                    ctx.totalCompletion()[0] += result.totalCompletionTokens();
                }
            }
        } catch (IOException e) {
            log.error("[chat] failed to read input", e);
        }
    }

    /**
     * 创建 stdin reader（--input 注入时用字节流，否则 System.in）。
     */
    private BufferedReader createReader() {
        InputStream stdin =
                injectedInput != null
                        ? new ByteArrayInputStream(injectedInput.getBytes(StandardCharsets.UTF_8))
                        : System.in;
        return new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8));
    }

    /**
     * 构建权限确认器：--auto-approve-write 全放行；否则从 stdin 交互确认（y/yes）。
     */
    private PermissionConfirmer buildConfirmer(BufferedReader reader) {
        if (autoApproveWrite) return PermissionConfirmer.allowAll();
        return prompt -> {
            try {
                System.out.print("⚠ 允许执行 " + prompt + " ? [y/N] ");
                System.out.flush();
                String ans = reader.readLine();
                return ans != null && (ans.equalsIgnoreCase("y") || ans.equalsIgnoreCase("yes"));
            } catch (IOException e) {
                return false;
            }
        };
    }

    /**
     * 静默关闭 reader（--input 字节流可关；System.in 关闭无副作用）。
     */
    private static void closeQuietly(BufferedReader reader) {
        if (reader == null) return;
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * 把异常翻译成用户能看懂的提示（401 / 404 / 5xx / 网络等）
     */
    static String friendlyError(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg =
                root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        if (msg != null && msg.contains("401")) {
            return "401 Unauthorized — API key 未设或失效。三种配置方式（任选其一）：\n"
                    + "  1. 环境变量: $env:DEEPSEEK_API_KEY='sk-...'  (PowerShell)\n"
                    + "  2. 项目 yaml: src/main/resources/application-local.yml"
                    + " (agent.provider.api-key)\n"
                    + "  3. HOME yaml: ~/.agent-demo/config.yaml    (provider.apiKey)";
        }
        if (msg != null && msg.contains("404")) {
            return "404 Not Found — baseUrl 或 model 名错（默认 https://api.deepseek.com /"
                    + " deepseek-chat）";
        }
        if (msg != null && (msg.contains("429") || msg.contains("rate limit"))) {
            return "429 限流 — 稍等 30s 再试，或检查账户余额";
        }
        if (msg != null && (msg.contains("connect") || msg.contains("timeout"))) {
            return "网络错误 — 检查 baseUrl / 代理 / 防火墙";
        }
        return msg;
    }

    /**
     * Handle a single line: slash commands processed directly; return true if consumed. Other lines
     * return false so the caller passes them to AgentLoop.
     */
    private boolean handleLine(String line, ReplContext ctx) {
        return ctx.slash()
                .dispatch(
                        line,
                        ctx.history().get(),
                        ctx.totalPrompt(),
                        ctx.totalCompletion(),
                        ctx.resolvedModel(),
                        () -> {
                            if (ctx.recorder() != null) ctx.recorder().flush();
                            MessageHistory fresh = new MessageHistory(ctx.estimator());
                            ctx.history().set(fresh);
                            ctx.loop().setHistory(fresh);
                        },
                        ctx.sessionsDir(),
                        messages -> {
                            // /resume 回调：调 MessageHistory.replaceAll 整体替换
                            MessageHistory fresh = new MessageHistory(ctx.estimator());
                            fresh.replaceAll(messages);
                            ctx.history().set(fresh);
                            ctx.loop().setHistory(fresh);
                            if (ctx.recorder() != null) ctx.recorder().flush();
                            // 累计 token 数组重置（/resume 前的累计不适用于新历史）
                            ctx.totalPrompt()[0] = 0;
                            ctx.totalCompletion()[0] = 0;
                            if (messages.isEmpty()) {
                                System.out.println("[/resume] 当前无可恢复会话");
                            } else {
                                System.out.println("[/resume] 已恢复 " + messages.size() + " 条消息");
                            }
                        });
    }

    /**
     * REPL 循环共享状态 record（聚合 9 个参数，消除 runReplLoop/handleLine 的参数列表膨胀）。
     *
     * @param history         当前消息历史（/clear 时切换）
     * @param estimator       token 估算器
     * @param loop            Agent 主循环
     * @param slash           slash 命令分发器
     * @param totalPrompt     累计 prompt token 累加器
     * @param totalCompletion 累计 completion token 累加器
     * @param resolvedModel   解析后的模型名
     * @param aborted         中断标志（Ctrl+C 置 true）
     * @param recorder        会话录制器（可空；写盘失败时降级为 null）
     * @param sessionsDir     /resume 用的 sessions 目录（{@code ~/.agent-demo/sessions/}）
     */
    private record ReplContext(
            AtomicReference<MessageHistory> history,
            TokenEstimator estimator,
            AgentLoop loop,
            SlashCommand slash,
            int[] totalPrompt,
            int[] totalCompletion,
            String resolvedModel,
            AtomicBoolean aborted,
            SessionRecorder recorder,
            Path sessionsDir) {
    }

    /**
     * 取第一个非空白字符串（用于多源配置优先级：CLI flag &gt; env &gt; config.yaml）。
     *
     * @param candidates 候选项
     * @return 第一个非 null 且非空白的值；全部为空时返回 {@code null}
     */
    private static String pickFirstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }
}
