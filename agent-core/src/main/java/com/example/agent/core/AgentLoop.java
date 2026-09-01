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
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.tools.ToolResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.ArrayList;
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
                            // 回合级异常广播 system/error（message 截断 500 字符）
                            sink.onSystemEvent(
                                    "system/error",
                                    Map.of(
                                            "errorClass",
                                            e.getClass().getSimpleName(),
                                            "message",
                                            truncate(e.getMessage())));
                            sink.onTurnEnd(new TurnResult("", 0, 0, 0));
                            currentTurn++;
                        });
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
        return provider.streamChat(toRequest())
                .doOnNext(this::printChunk)
                .collectList()
                .flatMapMany(
                        chunks -> {
                            Message.Assistant assistant = extractAssistant(chunks);
                            sink.onAssistant(assistant, List.of());
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
     * 组装当前 {@link ChatRequest}：工具 schema + 历史消息 + 默认采样参数。
     *
     * @return 当前轮的聊天请求
     */
    private ChatRequest toRequest() {
        List<ToolSpec> specs = new ArrayList<>();
        for (var t : tools.list()) {
            specs.add(new ToolSpec(t.name(), t.description(), t.inputSchema()));
        }
        List<com.example.agent.core.Message> msgs = new ArrayList<>(history.all());
        sink.onContextSnapshot(buildSnapshot(specs));
        return new ChatRequest(
                model != null ? model : DEFAULT_MODEL,
                systemPrompt,
                msgs,
                specs,
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_TOKENS,
                null);
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
     * 单 chunk 路由：根据 chunk 类型分发到 {@link StreamingPrinter}。 Finished / Usage 不打印。
     *
     * @param chunk 流式 chunk
     */
    private void printChunk(StreamChunk chunk) {
        if (chunk instanceof StreamChunk.TextDelta t) {
            printer.onTextDelta(t.text());
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
        for (StreamChunk c : chunks) {
            if (c instanceof StreamChunk.TextDelta t) content.append(t.text());
        }
        List<ToolCall> calls = StreamChunk.aggregate(chunks);
        return new Message.Assistant(content.toString(), calls);
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
            return Mono.just(
                    List.of(ToolResult.<Object>error(call.id(), "工具不存在: " + call.name())));
        }
        sink.onToolCall(call);
        long startNs = System.nanoTime();
        return Mono.fromCallable(() -> (Object) tool.parseArguments(call.argumentsJson()))
                // raw cast 隔离到 executeTool，主链保持强类型，onErrorResume 的 e 才能正确推断为 Throwable
                .flatMap(input -> authorize(tool, call, input))
                .map(r -> {
                    // 强制用本次调用的真实 id 覆盖工具结果里的 toolCallId（工具常返回 null 或 "<auto>" 占位），
                    // 保证回流给模型的 tool_call_id 与 assistant tool_calls[].id 一致（否则 DeepSeek 400）
                    ToolResult<Object> typed = stampCallId(r, call.id());
                    sink.onToolResult(typed, (System.nanoTime() - startNs) / 1_000_000L);
                    return List.of(typed);
                })
                .onErrorResume(
                        e -> {
                            log.warn("工具执行失败 [{}]: {}", call.name(), e.getMessage());
                            ToolResult<Object> err =
                                    ToolResult.error(call.id(), "工具执行失败: " + e.getMessage());
                            sink.onToolResult(err, (System.nanoTime() - startNs) / 1_000_000L);
                            return Mono.just(List.of(err));
                        });
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
        return (Mono<ToolResult<Object>>) (Mono) tool.execute(input, toolContext);
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
        PermissionDecision d = resolvePermission(tool, input);
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
     * <p>deny 终态；任一 ask → ask；都 allow → allow。工具级 {@code checkPermissions} 为 {@code null}（mock
     * 未 stub）时视作无意见。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private PermissionDecision resolvePermission(Tool tool, Object input) {
        PermissionDecision global = toolContext.permissions().decide(tool.name(), input, toolContext);
        PermissionDecision local = tool.checkPermissions(input, toolContext);
        if ((local != null && local.behavior() == PermissionDecision.Behavior.DENY)
                || global.behavior() == PermissionDecision.Behavior.DENY) {
            return PermissionDecision.deny();
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
        return new TurnResult(text, prompt, completion, 0);
    }
}
