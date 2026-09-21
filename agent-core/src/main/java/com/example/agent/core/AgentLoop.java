package com.example.agent.core;

import com.example.agent.core.exception.MaxIterationsExceededException;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.ToolCall;
import com.example.agent.llm.ToolSpec;
import com.example.agent.log.SessionLogSink;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.permission.PermissionDecision;
import com.example.agent.permission.PermissionManager;
import com.example.agent.permission.PermissionMode;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.signal.AbortSignal;
import com.example.agent.stats.TurnDelta;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.ToolResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 主循环：单轮对话 → 流式响应 → 工具调度 → 续推（详见 design.md §7）。
 *
 * <p>关键约束：
 *
 * <ul>
 *   <li>{@code maxToolIterations} 强制熔断，防止模型/工具诱导无限循环（§7）
 *   <li>assistant(tool_calls) 必须先入 history，再 append tool_results（§7 消息顺序约束）
 *   <li>流式打印统一在 {@link #printChunk} 内部，processTurn 外层不再打印（避免双打）
 *   <li>history 字段 mutable，{@link #setHistory} 支持 /clear 切换（详见 ChatCommand）
 *   <li>所有工具调用共享 {@link #toolContext}（含 workingDirectory + PermissionManager + AbortSignal）
 * </ul>
 */
public class AgentLoop {
    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    /**
     * 默认模型（v0.1 单 provider；从 cfg 传入覆盖）
     */
    private static final String DEFAULT_MODEL = "deepseek-chat";

    /**
     * 默认 temperature（DeepSeek 推荐 1.0）
     */
    private static final double DEFAULT_TEMPERATURE = 1.0;

    /**
     * 默认 max_tokens（DeepSeek-chat 上限 8192）
     */
    private static final int DEFAULT_MAX_TOKENS = 8192;

    /**
     * LLM Provider 实例（注入）
     */
    private final LlmProvider provider;

    /**
     * 工具注册表（按 name 索引）
     */
    private final ToolRegistry tools;

    /**
     * 工具执行上下文（含 workingDirectory + PermissionManager + AbortSignal）
     */
    private final Tool.ToolContext toolContext;

    /**
     * 当前消息历史（{@code volatile}：/clear 时由 {@link #setHistory} 切换）
     */
    private volatile MessageHistory history;

    /**
     * 流式打印机（stdout 输出）
     */
    private final StreamingPrinter printer;

    /**
     * 单轮最大工具调用次数（超过则抛 {@link MaxIterationsExceededException}）
     */
    private final int maxToolIterations;

    /**
     * 模型名（{@code null} 时回落到 {@link #DEFAULT_MODEL}）
     */
    private volatile String model;

    /**
     * 思考强度（add-models-dropdown-v0；{@code null} = 不传，由 Provider 内部 fallback）。
     *
     * <p>运行时由 {@link #setReasoningEffort(String)} 切换；多线程可见（{@code volatile}）。
     * 每次构造 {@code ChatRequest} 时通过 {@code extra} map 透传给 Provider。
     */
    private volatile String reasoningEffort;

    /**
     * 系统提示词（{@code null} 表示不注入 system 消息；由 SystemPromptBuilder 组装或用户 --system-prompt 覆盖）
     */
    private final String systemPrompt;

    /**
     * 会话日志观察者（可空；默认 no-op，见 {@link SessionLogSink#NOOP}）
     */
    private final SessionLogSink sink;

    /**
     * 权限交互确认器（ASK 时调用；{@code null} = fail-closed 拒绝）
     */
    private final PermissionConfirmer confirmer;

    /**
     * 当前权限模式（add-permission-mode-dropdown）。{@code volatile}：运行时 {@link #setPermissionMode} 可切换。
     */
    private volatile PermissionMode mode = PermissionMode.DEFAULT;

    /**
     * 当前轮次序号（context/snapshot 与 turn 事件用；每轮成功后自增）
     */
    private int currentTurn = 0;

    // ---- add-session-stats-bar：本轮统计采集累加器（processTurn 开始时重置） ----

    /** 本轮累计 LLM 耗时（毫秒，每次 streamChat 区间之和） */
    private long accLlmMillis = 0;
    /** 本轮累计工具耗时（毫秒） */
    private long accToolMillis = 0;
    /** 本轮工具调用次数（含失败） */
    private long accSteps = 0;
    /** 本轮累计 prompt token */
    private long accTokensIn = 0;
    /** 本轮累计 completion token */
    private long accTokensOut = 0;
    /** 本轮累计 reasoning token */
    private long accReasoning = 0;
    /** 本轮首 token 延迟累计（毫秒） */
    private long accTtftMillis = 0;
    /** 本轮首 token 延迟样本数 */
    private long accTtftSamples = 0;
    /** 本轮缓存命中 token 累计 */
    private long accCacheHit = 0;
    /** 本轮缓存未命中 token 累计 */
    private long accCacheMiss = 0;
    /** 本轮是否收到过缓存字段（决定缓存命中率是否 N/A） */
    private boolean accCacheSeen = false;

    /**
     * 构造 Agent 主循环（无系统提示词；等价于 {@code systemPrompt = null}）。
     *
     * @param provider          LLM provider
     * @param tools             工具注册表
     * @param history           初始消息历史
     * @param printer           流式打印机
     * @param maxToolIterations 单轮最大工具调用次数（超过熔断）
     * @param model             模型名（{@code null} 用默认 deepseek-chat）
     * @param workingDir        工作目录（所有相对路径的基准）
     */
    public AgentLoop(
            LlmProvider provider,
            ToolRegistry tools,
            MessageHistory history,
            StreamingPrinter printer,
            int maxToolIterations,
            String model,
            Path workingDir) {
        this(provider, tools, history, printer, maxToolIterations, model, workingDir, null, SessionLogSink.NOOP);
    }

    /**
     * 构造 Agent 主循环。
     *
     * @param provider          LLM provider
     * @param tools             工具注册表
     * @param history           初始消息历史
     * @param printer           流式打印机
     * @param maxToolIterations 单轮最大工具调用次数（超过熔断）
     * @param model             模型名（{@code null} 用默认 deepseek-chat）
     * @param workingDir        工作目录（所有相对路径的基准）
     * @param systemPrompt      系统提示词（{@code null} 不注入；OpenAiCompatibleMapper 合并到 messages 头部）
     */
    public AgentLoop(
            LlmProvider provider,
            ToolRegistry tools,
            MessageHistory history,
            StreamingPrinter printer,
            int maxToolIterations,
            String model,
            Path workingDir,
            String systemPrompt) {
        this(provider, tools, history, printer, maxToolIterations, model, workingDir, systemPrompt, SessionLogSink.NOOP);
    }

    /**
     * 构造 Agent 主循环（带会话日志观察者）。
     *
     * @param provider          LLM provider
     * @param tools             工具注册表
     * @param history           初始消息历史
     * @param printer           流式打印机
     * @param maxToolIterations 单轮最大工具调用次数（超过熔断）
     * @param model             模型名（{@code null} 用默认 deepseek-chat）
     * @param workingDir        工作目录（所有相对路径的基准）
     * @param systemPrompt      系统提示词（{@code null} 不注入）
     * @param sink              会话日志观察者（{@code null} 用 no-op）
     */
    public AgentLoop(
            LlmProvider provider,
            ToolRegistry tools,
            MessageHistory history,
            StreamingPrinter printer,
            int maxToolIterations,
            String model,
            Path workingDir,
            String systemPrompt,
            SessionLogSink sink) {
        this(provider, tools, history, printer, maxToolIterations, model, workingDir, systemPrompt, sink, null);
    }

    /**
     * 构造 Agent 主循环（带会话日志观察者 + agent 数据目录）。
     *
     * @param agentDataDir agent 数据目录（{@code ~/.agent-demo}，memory/logs/sessions 所在；文件工具额外放行，可空）
     */
    public AgentLoop(
            LlmProvider provider,
            ToolRegistry tools,
            MessageHistory history,
            StreamingPrinter printer,
            int maxToolIterations,
            String model,
            Path workingDir,
            String systemPrompt,
            SessionLogSink sink,
            Path agentDataDir) {
        this(provider, tools, history, printer, maxToolIterations, model, workingDir, systemPrompt, sink, agentDataDir, null);
    }

    /**
     * 构造 Agent 主循环（带会话日志观察者 + agent 数据目录 + 权限确认器）。
     *
     * @param agentDataDir agent 数据目录（{@code ~/.agent-demo}，memory/logs/sessions 所在；文件工具额外放行，可空）
     * @param confirmer    权限交互确认器（ASK 时调用；{@code null} = fail-closed 拒绝）
     */
    public AgentLoop(
            LlmProvider provider,
            ToolRegistry tools,
            MessageHistory history,
            StreamingPrinter printer,
            int maxToolIterations,
            String model,
            Path workingDir,
            String systemPrompt,
            SessionLogSink sink,
            Path agentDataDir,
            PermissionConfirmer confirmer) {
        this(provider, tools, history, printer, maxToolIterations, model, workingDir, systemPrompt, sink,
                agentDataDir, confirmer, null);
    }

    /**
     * 构造 Agent 主循环（带会话日志观察者 + agent 数据目录 + 权限确认器 + 中断信号）。
     *
     * @param agentDataDir agent 数据目录（{@code ~/.agent-demo}，memory/logs/sessions 所在；文件工具额外放行，可空）
     * @param confirmer    权限交互确认器（ASK 时调用；{@code null} = fail-closed 拒绝）
     * @param abortSignal  中断信号（{@code null} = 永不中断；CLI 用 Ctrl+C 的 AtomicBoolean，web 用 abort 请求）
     */
    public AgentLoop(
            LlmProvider provider,
            ToolRegistry tools,
            MessageHistory history,
            StreamingPrinter printer,
            int maxToolIterations,
            String model,
            Path workingDir,
            String systemPrompt,
            SessionLogSink sink,
            Path agentDataDir,
            PermissionConfirmer confirmer,
            AbortSignal abortSignal) {
        this.provider = provider;
        this.tools = tools;
        this.history = history;
        this.printer = printer;
        this.maxToolIterations = maxToolIterations;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.sink = sink != null ? sink : SessionLogSink.NOOP;
        this.confirmer = confirmer;
        AbortSignal signal = abortSignal != null ? abortSignal : () -> false;
        PermissionManager perms = new PermissionManager();
        perms.setSink(sink != null ? sink : SessionLogSink.NOOP);
        // 权限模式 + 工作区边界（add-permission-mode-dropdown）：构造时按当前 mode 装配，
        // 运行期经 setPermissionMode 切换。
        perms.setMode(mode);
        perms.setWorkingDirectory(workingDir);
        this.toolContext =
                new Tool.ToolContext(workingDir, perms, signal, agentDataDir);
    }

    /**
     * 切换历史容器（/clear 时调用，详见 ChatCommand）。
     *
     * @param history 新的消息历史
     */
    public void setHistory(MessageHistory history) {
        this.history = history;
    }

    /**
     * 运行时切换 model（{@code /model} slash 命令用）。volatile 保证多线程可见。
     *
     * @param newModel 新 model 名（{@code null} 视为不切换；实际应被 SlashCommand 白名单拦截）
     */
    public void setModel(String newModel) {
        this.model = newModel;
    }

    /**
     * 运行时切换思考强度（add-models-dropdown-v0；{@code /effort} slash 命令 + 前端下拉用）。volatile 保证多线程可见。
     *
     * <p>仅影响切换之后的新 ChatRequest，正在进行的 turn 不受影响（流中不切）。
     *
     * @param newEffort 新思考强度（{@code null} = 不切换；{@code low/medium/high} 之一）
     */
    public void setReasoningEffort(String newEffort) {
        this.reasoningEffort = newEffort;
    }

    /** 当前思考强度（供 UI / 测试读取）。 */
    public String reasoningEffort() {
        return reasoningEffort;
    }

    /**
     * 运行时切换权限模式（add-permission-mode-dropdown；对齐 {@link #setModel} 的 volatile 范式）。
     *
     * <p>仅影响切换之后的新 {@link PermissionManager#decide}，正在执行的工具不受影响。
     *
     * @param mode 新模式（{@code null} 视为 {@link PermissionMode#DEFAULT}）
     */
    public void setPermissionMode(PermissionMode mode) {
        this.mode = mode != null ? mode : PermissionMode.DEFAULT;
        this.toolContext.permissions().setMode(this.mode);
    }

    /** 当前权限模式（供 UI / 测试读取）。 */
    public PermissionMode permissionMode() {
        return mode;
    }

    /** 包内可见：工具执行上下文（测试/装配用）。 */
    Tool.ToolContext toolContext() {
        return toolContext;
    }

    /** 包内可见：权限管理器（测试/装配用）。 */
    PermissionManager permissions() {
        return toolContext.permissions();
    }

    /**
     * 处理单轮用户输入：追加 user 消息 → 调 LLM → 工具调度 → 返回拼接结果。
     *
     * @param userMsg 用户消息
     * @return 该轮拼接后的 {@link TurnResult}（finalMessage + token 累计）
     */
    public Mono<TurnResult> processTurn(Message.User userMsg) {
        resetTurnAccumulators();
        // 安全网：若上一轮被 Error 打断且外部边界没来得及收口，这里先补掉，避免带着悬挂
        // tool_calls 去发请求（那会被上游 400）。正常情况下为 0。
        closePendingToolCalls("上一轮遗留");
        sink.onTurnStart(currentTurn);
        sink.onUser(userMsg);
        history.append(userMsg);
        return streamUntilToolsSettled(0)
                .next()
                .map(this::buildTurnResult)
                .doOnSuccess(
                        r -> {
                            sink.onTurnEnd(r);
                            currentTurn++;
                        })
                .doOnError(
                        e -> {
                            // 先收口在途工具调用（补 TOOL< 与 history），再上报与结束回合
                            closePendingToolCalls("回合执行异常: " + e.getClass().getSimpleName());
                            // 回合级异常广播 system/error（message 截断 500 字符）
                            sink.onSystemEvent(
                                    "system/error",
                                    Map.of(
                                            "errorClass",
                                            e.getClass().getSimpleName(),
                                            "message",
                                            truncate(e.getMessage())));
                            sink.onTurnEnd(new TurnResult("", 0, 0, 0, currentTurnDelta()));
                            currentTurn++;
                        })
                // 取消 / 正常结束也兜一次（幂等）；Error 逃逸时本算子不会执行，由订阅侧边界负责
                .doFinally(signal -> closePendingToolCalls("回合结束(" + signal + ")"));
    }

    /** 重置本轮统计累加器（add-session-stats-bar）。 */
    private void resetTurnAccumulators() {
        accLlmMillis = 0;
        accToolMillis = 0;
        accSteps = 0;
        accTokensIn = 0;
        accTokensOut = 0;
        accReasoning = 0;
        accTtftMillis = 0;
        accTtftSamples = 0;
        accCacheHit = 0;
        accCacheMiss = 0;
        accCacheSeen = false;
    }

    /** 组装本轮统计增量（缓存未出现时两字段传 null → 上层按 N/A 处理）。 */
    private TurnDelta currentTurnDelta() {
        return new TurnDelta(
                accSteps,
                accTokensIn,
                accTokensOut,
                accReasoning,
                accLlmMillis,
                accToolMillis,
                accTtftMillis,
                accTtftSamples,
                accCacheSeen ? (int) accCacheHit : null,
                accCacheSeen ? (int) accCacheMiss : null);
    }

    /** 累加单个 chunk 携带的 usage（Usage chunk 或 Finished.usage）。 */
    private void accumulateUsage(StreamChunk c) {
        StreamChunk.Usage u;
        if (c instanceof StreamChunk.Usage uu) {
            u = uu;
        } else if (c instanceof StreamChunk.Finished f && f.usage() != null) {
            u = f.usage();
        } else {
            return;
        }
        accTokensIn += u.promptTokens();
        accTokensOut += u.completionTokens();
        accReasoning += u.reasoningTokens();
        if (u.cacheHitTokens() != null) {
            accCacheHit += u.cacheHitTokens();
            accCacheSeen = true;
        }
        if (u.cacheMissTokens() != null) {
            accCacheMiss += u.cacheMissTokens();
            accCacheSeen = true;
        }
    }

    /** 截断长文本（可观测性事件用，避免错误信息撑爆日志） */
    private static String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= 500) return s;
        return s.substring(0, 500) + "...[truncated]";
    }

    /**
     * 流式对话循环：直到模型不再产生 tool_calls 或达到 {@link #maxToolIterations}。
     *
     * @param iteration 当前递归深度（首次为 0，每次工具调用后 +1）
     * @return 该次完整流的 chunk 列表
     */
    private Flux<List<StreamChunk>> streamUntilToolsSettled(int iteration) {
        if (iteration >= maxToolIterations) {
            log.warn("hit maxToolIterations={}, stopping turn", iteration);
            return Flux.error(new MaxIterationsExceededException(iteration));
        }
        // add-session-stats-bar：每次 streamChat 记一段 LLM 耗时；首个文本 chunk 记一次 TTFT 样本。
        long startNs = System.nanoTime();
        java.util.concurrent.atomic.AtomicBoolean firstTextSeen =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        return provider.streamChat(toRequest())
                .doOnNext(
                        c -> {
                            if (c instanceof StreamChunk.TextDelta
                                    && firstTextSeen.compareAndSet(false, true)) {
                                accTtftMillis += (System.nanoTime() - startNs) / 1_000_000L;
                                accTtftSamples++;
                            }
                            accumulateUsage(c);
                            printChunk(c);
                        })
                .doFinally(sig -> accLlmMillis += (System.nanoTime() - startNs) / 1_000_000L)
                .collectList()
                .flatMapMany(
                        chunks -> {
                            Message.Assistant assistant = extractAssistant(chunks);
                            sink.onAssistant(assistant, assistant.reasoning());
                            history.append(assistant);
                            if (assistant.toolCalls() == null || assistant.toolCalls().isEmpty()) {
                                printer.onFinished();
                                return Flux.just(chunks);
                            }
                            // collectList 先收集全部工具调用结果，再一次性回流+递归，
                            // 避免 flatMap 流式处理下某工具调用(c2)的 emit 在递归切换时被丢弃
                            // → 曾导致失败工具结果未回流入 history → assistant.tool_calls 缺 tool 消息 → DeepSeek 400
                            return executeTools(assistant.toolCalls())
                                    .collectList()
                                    .flatMapMany(
                                            allResults -> {
                                                java.util.List<ToolResult<Object>> flat =
                                                        allResults.stream()
                                                                .flatMap(java.util.List::stream)
                                                                .collect(java.util.stream.Collectors.toList());
                                                history.appendToolResults(toEnvelopes(flat));
                                                return streamUntilToolsSettled(iteration + 1);
                                            });
                        });
    }

    /**
     * 组装当前 {@link ChatRequest}：工具 schema + 历史消息 + 默认采样参数 + 运行时配置（model / reasoningEffort）。
     *
     * @return 当前轮的聊天请求
     */
    private ChatRequest toRequest() {
        List<ToolSpec> specs = new ArrayList<>();
        for (var t : tools.list()) {
            specs.add(new ToolSpec(t.name(), t.description(), t.inputSchema()));
        }
        // 配对修复（repair-dangling-tool-calls）：某一轮若在「assistant(tool_calls) 已入 history、
        // tool_result 尚未回流」之间被打断，内存历史就带着空洞；直接发出去会被上游 400
        // （assistant 的 tool_calls 缺少对应 tool 消息）。这里对**将要发送的消息列表**补齐，
        // 使同一进程内不必等重启也能继续对话（幂等，已配对时无改动）。
        List<com.example.agent.core.Message> msgs =
                ToolCallPairing.repair(history.all());
        sink.onContextSnapshot(buildSnapshot(specs));
        // add-models-dropdown-v0：把 volatile reasoningEffort 透传到 ChatRequest.extra，
        // 让 OpenAI/Anthropic mapper 按各自规则写入请求 body（DeepSeek 忽略）。
        // 用 LinkedHashMap 保留插入顺序，便于上游 mapper 调试日志可读。
        Map<String, Object> extra = null;
        if (reasoningEffort != null) {
            extra = new LinkedHashMap<>();
            extra.put("reasoning_effort", reasoningEffort);
        }
        return new ChatRequest(
                model != null ? model : DEFAULT_MODEL,
                systemPrompt,
                msgs,
                specs,
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_TOKENS,
                extra);
    }

    /**
     * 组装每轮上下文快照（observability 事件 {@code context/snapshot}）。
     *
     * <p>只记元数据 + system prompt 原文（截断在 SessionLogger 侧按 {@code snapshotMaxChars} 处理）；
     * 消息正文由 user/message 与 assistant/message 事件覆盖，不在此重复。
     *
     * @param specs 本轮暴露的工具 schema 列表
     * @return 上下文快照
     */
    private com.example.agent.log.ContextSnapshot buildSnapshot(List<ToolSpec> specs) {
        List<String> toolNames = specs.stream().map(ToolSpec::name).toList();
        boolean memoryInjected =
                systemPrompt != null && systemPrompt.contains("Persistent Agent Memory");
        boolean compacted =
                history.all().stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.System s
                                                && s.content().startsWith("[COMPACTED]"));
        return new com.example.agent.log.ContextSnapshot(
                currentTurn,
                systemPrompt,
                memoryInjected,
                compacted,
                history.recentFilePaths(),
                toolNames,
                history.size(),
                history.estimateTokens());
    }

    /**
     * 单 chunk 路由：根据 chunk 类型分发到 {@link StreamingPrinter} 与 {@link SessionLogSink}。
     * Finished / Usage 不打印。
     *
     * @param chunk 流式 chunk
     */
    private void printChunk(StreamChunk chunk) {
        if (chunk instanceof StreamChunk.TextDelta t) {
            printer.onTextDelta(t.text());
            // add-true-streaming: 正文也逐 token 转发到 sink。此前只在整轮 collectList() 完成后
            // 由 sink.onAssistant 一次性推出全文，前端看起来仍是"一坨出来"。
            sink.onTextDelta(t.text());
        } else if (chunk instanceof StreamChunk.ThinkingDelta tk) {
            // add-reasoning-thinking-streaming: thinking 实时转发到 sink（落 thinking.log）
            sink.onThinkingDelta(tk.text());
        } else if (chunk instanceof StreamChunk.ToolCallStart s) {
            printer.onToolCallStart(s.id(), s.name());
        } else if (chunk instanceof StreamChunk.ToolCallDelta d) {
            printer.onToolCallArgs(d.id(), d.argumentsDelta());
        } else if (chunk instanceof StreamChunk.ToolCallEnd e) {
            printer.onToolCallEnd(e.id(), e.name(), e.arguments());
        } else if (chunk instanceof StreamChunk.Error err) {
            printer.onError(err.message());
        }
        // Finished / Usage 不打印
    }

    /**
     * 从完整 chunk 序列提取 {@link Message.Assistant}：拼接所有 {@link StreamChunk.TextDelta} 内容 + 累积工具调用。
     *
     * @param chunks 完整 chunk 序列
     * @return 提取出的 assistant 消息
     */
    private Message.Assistant extractAssistant(List<StreamChunk> chunks) {
        StringBuilder content = new StringBuilder();
        List<String> reasoning = new ArrayList<>();
        int reasoningTokens = 0;
        for (StreamChunk c : chunks) {
            if (c instanceof StreamChunk.TextDelta t) content.append(t.text());
            else if (c instanceof StreamChunk.ThinkingDelta tk) reasoning.add(tk.text());
            else if (c instanceof StreamChunk.Usage u) reasoningTokens = u.reasoningTokens();
        }
        List<ToolCall> calls = StreamChunk.aggregate(chunks);
        return new Message.Assistant(content.toString(), calls, reasoning, reasoningTokens);
    }

    /**
     * 并行执行模型产生的所有工具调用。
     *
     * <p>执行前先把 {@code argumentsJson} 反序列化为类型化输入（{@link Tool#parseArguments}）；
     * 解析或执行失败时返回错误 {@link ToolResult}，不让单次失败打断整轮。
     *
     * @param calls 模型返回的工具调用列表
     * @return 每个工具的执行结果（错误时返回 error 结果）
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Flux<List<ToolResult<Object>>> executeTools(List<ToolCall> calls) {
        return Flux.fromIterable(calls).flatMap(call -> executeOne(call, tools.getRaw(call.name())));
    }

    /**
     * 执行单个工具调用：反序列化参数 → 执行 → 错误兜底（单次失败不打断整轮）。
     *
     * @param call 工具调用（id + name + argumentsJson）
     * @param tool 已查到的工具（{@code null} 时返回"工具不存在"）
     * @return 单条工具结果（错误时返回 error 结果）
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Mono<List<ToolResult<Object>>> executeOne(ToolCall call, Tool tool) {
        if (tool == null) {
            // add-session-stats-bar：工具不存在也是一次失败的调用 → 计入步数（耗时为 0）。
            accSteps++;
            return Mono.just(
                    List.of(ToolResult.<Object>error(call.id(), "工具不存在: " + call.name())));
        }
        sink.onToolCall(call);
        // improve-failure-observability：登记在途调用。若本轮在此后被打断（含 Error 逃出
        // 响应式链），turn 边界会统一收口，保证 tools.log 有 TOOL<、history 不留悬挂 tool_calls。
        pendingToolCalls.add(call.id());
        long startNs = System.nanoTime();
        return Mono.fromCallable(
                        () ->
                                (Object)
                                        invokeTool(
                                                tool.name(),
                                                STAGE_PARSE,
                                                () -> tool.parseArguments(call.argumentsJson())))
                // raw cast 隔离到 executeTool，主链保持强类型，onErrorResume 的 e 才能正确推断为 Throwable
                .flatMap(input -> authorize(tool, call, input))
                .map(r -> {
                    // 强制用本次调用的真实 id 覆盖工具结果里的 toolCallId（工具常返回 null 或 "<auto>" 占位），
                    // 保证回流给模型的 tool_call_id 与 assistant tool_calls[].id 一致（否则 DeepSeek 400）
                    ToolResult<Object> typed = stampCallId(r, call.id());
                    long elapsed = (System.nanoTime() - startNs) / 1_000_000L;
                    pendingToolCalls.remove(call.id());
                    sink.onToolResult(typed, elapsed);
                    recordToolExecution(elapsed);
                    return List.of(typed);
                })
                .onErrorResume(
                        e -> {
                            log.warn("工具执行失败 [{}]: {}", call.name(), e.getMessage());
                            ToolResult<Object> err =
                                    ToolResult.error(call.id(), "工具执行失败: " + e.getMessage());
                            long elapsed = (System.nanoTime() - startNs) / 1_000_000L;
                            pendingToolCalls.remove(call.id());
                            sink.onToolResult(err, elapsed);
                            recordToolExecution(elapsed);
                            return Mono.just(List.of(err));
                        });
    }

    // ---- 工具调用收口（improve-failure-observability）----

    /**
     * 本轮在途工具调用 id（已开始、尚未产生结果）。
     *
     * <p>用 {@link java.util.LinkedHashSet} 保持登记顺序，收口时按原顺序补结果。<b>只在 Reactor
     * 事件线程与 turn 边界线程访问</b>，跨线程场景由 {@link #closePendingToolCalls} 的同步块保护。
     */
    private final java.util.LinkedHashSet<String> pendingToolCalls = new java.util.LinkedHashSet<>();

    /**
     * 收口本轮所有未完成的工具调用：打 ERROR 日志、补错误结果到 sink（使 {@code tools.log} 闭合）、
     * 回流 history（避免留下「有 tool_calls 无 tool_result」的悬挂状态，那会让该会话此后每轮 400）。
     *
     * <p><b>为什么必须由 turn 边界负责</b>：工具抛出的 {@code Error}（如 {@code NoClassDefFoundError}）
     * 会被 Reactor 的 {@code Exceptions.throwIfFatal} 原样 rethrow，绕过所有错误算子；此时
     * {@code executeOne} 的 {@code onErrorResume} 不会执行，工具侧无法自我收口。只有在订阅侧
     * （{@code ChatStreamService} / CLI REPL）捕获后再回调本方法才可靠。
     *
     * <p>幂等：无在途调用时直接返回 0；已收口的条目会被清出集合。
     *
     * @param reason 中断原因（进日志与错误结果文案）
     * @return 本次收口的调用数
     */
    public int closePendingToolCalls(String reason) {
        java.util.List<String> ids;
        synchronized (pendingToolCalls) {
            if (pendingToolCalls.isEmpty()) return 0;
            ids = new java.util.ArrayList<>(pendingToolCalls);
            pendingToolCalls.clear();
        }
        java.util.List<MessageHistory.ToolResultEnvelope> envelopes = new java.util.ArrayList<>();
        for (String id : ids) {
            log.error(
                    "工具调用未完成 toolCallId={} reason={}（本轮未产生结果；已补错误结果以避免"
                            + "会话记录出现悬挂 tool_calls）",
                    id,
                    reason);
            String message = "[未完成] 工具调用被中断（" + reason + "），未返回结果。";
            ToolResult<Object> err = ToolResult.error(id, message);
            sink.onToolResult(err, 0L);
            accSteps++;
            envelopes.add(new MessageHistory.ToolResultEnvelope(id, message, true));
        }
        history.appendToolResults(envelopes);
        return ids.size();
    }

    /** 记录一次工具执行（add-session-stats-bar）：步数 +1、工具耗时累加。 */
    private void recordToolExecution(long elapsedMillis) {
        accSteps++;
        accToolMillis += elapsedMillis;
    }

    /**
     * 执行工具并做类型归一化（raw cast 集中在此，避免污染调用链的类型推断）。
     *
     * @param tool 工具（raw 通配符）
     * @param input 类型化输入
     * @return 归一化后的工具结果 monad
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Mono<ToolResult<Object>> executeTool(Tool tool, Object input) {
        return invokeTool(
                tool.name(),
                STAGE_EXECUTE,
                () -> (Mono<ToolResult<Object>>) (Mono) tool.execute(input, toolContext));
    }

    // ---- 工具错误边界（harden-tool-error-boundary）----

    /** 失败阶段：入参反序列化（{@code AbstractFileTool.parseArguments} 会在这一步解析输入类）。 */
    private static final String STAGE_PARSE = "入参解析";

    /** 失败阶段：权限检查（{@code checkPermissions} 可能触发惰性类加载）。 */
    private static final String STAGE_PERMISSION = "权限检查";

    /** 失败阶段：工具主体调用。 */
    private static final String STAGE_EXECUTE = "工具执行";

    /**
     * 工具交互边界：在代码交给 Reactor 之前，把 {@link LinkageError} 降级为普通异常。
     *
     * <p>为什么不能只靠 {@code onErrorResume}：{@link LinkageError} 属于 {@code Error}，
     * Reactor 的 {@code Exceptions.throwIfFatal} 会把它**原样重新抛出**而不转成 {@code onError}
     * 信号，于是所有下游错误算子都看不到它，错误直接穿透反应式链（2026-09-13 事故：缺失的
     * {@code EditFileTool$Input} 撕掉了整条 SSE 连接）。只有在我们交给 Reactor 执行的那段代码
     * **内部**捕获并换类型，才能让它变成正常的 {@code onError}。
     *
     * <p>只捕获 {@link LinkageError}：{@link VirtualMachineError}（OOM / StackOverflow）与
     * {@code ThreadDeath} 是真致命信号，降级成工具错误会让进程带着损坏状态继续服务，比直接失败
     * 更危险。
     *
     * @param toolName 工具名（进错误消息，便于定位是哪个插件）
     * @param stage 失败阶段（{@link #STAGE_PARSE} / {@link #STAGE_PERMISSION} / {@link #STAGE_EXECUTE}）
     * @param body 实际调用
     * @param <T> 返回值类型
     * @return body 的返回值
     * @throws ToolClassLoadingException body 抛出 {@link LinkageError} 时
     */
    private static <T> T invokeTool(
            String toolName, String stage, java.util.function.Supplier<T> body) {
        try {
            return body.get();
        } catch (LinkageError e) {
            log.warn("工具类加载失败 [{}] 于「{}」阶段", toolName, stage, e);
            throw new ToolClassLoadingException(toolName, stage, e);
        }
    }

    /**
     * 用本次调用的真实 id 覆盖工具结果里的 toolCallId（成功 / 失败都覆盖）。
     *
     * <p>工具内部不持有调用 id，常以 {@code null} 或占位符 {@code "<auto>"} 返回；回流给模型的
     * {@code tool_call_id} 必须与 assistant {@code tool_calls[].id} 一致，否则 DeepSeek 返回 400。
     *
     * @param r      工具返回的原始结果
     * @param callId 本次工具调用的真实 id
     * @return 已替换为真实 id 的结果
     */
    @SuppressWarnings("unchecked")
    private static ToolResult<Object> stampCallId(ToolResult<?> r, String callId) {
        if (r.isError()) {
            return ToolResult.error(callId, ((ToolResult.Err<?>) r).message());
        }
        return (ToolResult<Object>) ToolResult.ok(((ToolResult.Ok<?>) r).output(), callId);
    }

    /**
     * 权限裁决 + 交互确认：DENY 拒绝、ALLOW 放行、ASK 走 confirmer（无 confirmer 时 fail-closed 拒绝）。
     *
     * @param tool  待执行工具
     * @param call  本次工具调用（用于错误结果回填 id）
     * @param input 已反序列化的工具输入
     * @return 执行结果（放行时执行工具；拒绝时返回 error 结果）
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Mono<ToolResult<Object>> authorize(Tool tool, ToolCall call, Object input) {
        // 权限检查同样可能在惰性类加载上抛 LinkageError（harden-tool-error-boundary）
        PermissionDecision d =
                invokeTool(tool.name(), STAGE_PERMISSION, () -> resolvePermission(tool, input));
        if (d.behavior() == PermissionDecision.Behavior.DENY) {
            return Mono.just(ToolResult.error(call.id(), "权限拒绝: " + tool.name()));
        }
        if (d.behavior() == PermissionDecision.Behavior.ALLOW) {
            return executeTool(tool, input);
        }
        String prompt = tool.name() + " → " + renderUse(tool, input);
        if (confirmer != null && confirmer.confirm(prompt)) {
            return executeTool(tool, input);
        }
        return Mono.just(ToolResult.error(call.id(), "用户拒绝执行: " + tool.name()));
    }

    /**
     * 合并全局策略（敏感路径 + 分类默认）与工具级 {@code checkPermissions}（含 {@code ..} 越界 deny）。
     *
     * <p>裁决顺序（rewrite-permission-mode-dsh T5.3 整合 SandboxPolicyService）：
     *
     * <ol>
     *   <li><b>SandboxPolicyService</b>（via {@link PermissionManager#decide}）：
     *     {@link SandboxPolicyService#defaultDecision} 按 mode × ToolCategory 决策
     *     + {@link SensitivePathMatcher} 命中敏感路径升级 ask
     *   <li><b>Tool.checkPermissions</b>（{@code local}）：工具级 deny 终态兜底
     *   <li>合并：
     *     <ul>
     *       <li>任一 DENY → DENY（FULL_ACCESS 也不绕过工具级 deny）
     *       <li>FULL_ACCESS 短路：global allow + FULL_ACCESS → ALLOW（不弹窗）
     *       <li>任一 ASK → ASK
     *       <li>其余 → ALLOW
     *     </ul>
     * </ol>
     *
     * <p>fix-full-access-bypass：原逻辑把工具默认 ASK 与全局策略用 {@code OR} 合并，导致 FULL_ACCESS 仍弹窗。
     *
     * <p>工具级 {@code checkPermissions} 为 {@code null}（mock 未 stub）时视作无意见。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private PermissionDecision resolvePermission(Tool tool, Object input) {
        PermissionDecision global = toolContext.permissions().decide(tool.name(), input, toolContext);
        PermissionDecision local = tool.checkPermissions(input, toolContext);
        if ((local != null && local.behavior() == PermissionDecision.Behavior.DENY)
                || global.behavior() == PermissionDecision.Behavior.DENY) {
            return PermissionDecision.deny();
        }
        // FULL_ACCESS：PermissionManager 已短路敏感路径，global 在该模式下必为 allow；
        // 工具默认 ASK 不再触发弹窗（与 PermissionManager 一致）。
        if (mode == PermissionMode.FULL_ACCESS
                && global.behavior() == PermissionDecision.Behavior.ALLOW) {
            return PermissionDecision.allow();
        }
        if ((local != null && local.behavior() == PermissionDecision.Behavior.ASK)
                || global.behavior() == PermissionDecision.Behavior.ASK) {
            return PermissionDecision.ask();
        }
        return PermissionDecision.allow();
    }

    /**
     * 渲染工具调用描述（确认提示用）；渲染异常时回退到工具名。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String renderUse(Tool tool, Object input) {
        try {
            return String.valueOf(tool.renderUse(input));
        } catch (Exception e) {
            return tool.name();
        }
    }

    /**
     * 把工具结果列表转为 {@link MessageHistory.ToolResultEnvelope}（回流给模型的格式）。
     *
     * @param results 工具结果列表
     * @return 历史信封列表
     */
    private List<MessageHistory.ToolResultEnvelope> toEnvelopes(List<ToolResult<Object>> results) {
        return results.stream()
                .map(
                        r ->
                                new MessageHistory.ToolResultEnvelope(
                                        r.toolCallId(), r.toModelContent(), r.isError()))
                .toList();
    }

    /**
     * 从完整 chunk 序列组装 {@link TurnResult}：拼接文本 + 累计 token。
     *
     * @param chunks 完整 chunk 序列
     * @return 单轮结果
     */
    private TurnResult buildTurnResult(List<StreamChunk> chunks) {
        String text =
                chunks.stream()
                        .filter(c -> c instanceof StreamChunk.TextDelta)
                        .map(c -> ((StreamChunk.TextDelta) c).text())
                        .reduce("", String::concat);
        int prompt = 0, completion = 0;
        for (StreamChunk c : chunks) {
            if (c instanceof StreamChunk.Usage u) {
                prompt += u.promptTokens();
                completion += u.completionTokens();
            } else if (c instanceof StreamChunk.Finished f && f.usage() != null) {
                prompt += f.usage().promptTokens();
                completion += f.usage().completionTokens();
            }
        }
        return new TurnResult(text, prompt, completion, (int) accSteps, currentTurnDelta());
    }
}
