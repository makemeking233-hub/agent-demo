package com.example.agent.web.stream;

import com.example.agent.config.AgentConfig;
import com.example.agent.config.ConfigLoader;
import com.example.agent.core.AgentLoop;
import com.example.agent.core.AgentLoopFactory;
import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.CompositeSessionLogSink;
import com.example.agent.log.SessionLogSink;
import com.example.agent.log.SessionLogger;
import com.example.agent.log.SessionRecorder;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.permission.PermissionMode;
import com.example.agent.render.StreamingPrinter;
import com.example.agent.session.SessionResumeLoader;
import com.example.agent.session.SessionStore;
import com.example.agent.session.WorkspaceStore;
import com.example.agent.signal.AbortSignal;
import com.example.agent.tools.ToolRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Web 运行时装配（add-web-ui-v0-1 / D2，v0.3 加会话重进恢复）。
 *
 * <p>注入共享的 {@link LlmProvider} / {@link ToolRegistry} / {@link TokenEstimator}（由
 * {@link com.example.agent.web.config.WebRuntimeConfig} 提供，与 CLI 的装配一致）。每个 web 会话
 * 调用 {@link #createLoop(String, String, SessionLogSink, PermissionConfirmer, AbortSignal)} 生成独立
 * {@link AgentLoop}（各会话独立 history）。
 *
 * <p>会话重进恢复（add-web-session-restore）：web 会话按 {@code sessionId} 落盘到
 * {@code ~/.agent-demo/sessions/<id>.jsonl}（复用 CLI 的 SessionStore/SessionRecorder 格式）；首次
 * 触达该 {@code sessionId} 时从磁盘回填历史，浏览器刷新 / 服务端重启后模型仍能看到重启前的对话。
 *
 * <p>集成测试可用 {@code @MockBean LlmProvider} 替换 provider，注入固定 chunk 序列。
 */
@Service
@Profile("web")
public class WebAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(WebAgentRuntime.class);

    /** 回填历史的 token 上限（与 CLI /resume 的 MAX_RESUME_TOKENS 对齐）。 */
    private static final int MAX_RESUME_TOKENS = 100_000;

    /** agent 数据目录（{@code ~/.agent-demo}）。 */
    private final Path agentDataDir;

    /** 已装配的共享 provider。 */
    private final LlmProvider provider;

    /** 已装配的共享工具注册表。 */
    private final ToolRegistry tools;

    /** token 估算器（history 创建用）。 */
    private final TokenEstimator estimator;

    /** 模型名（web 固定 deepseek-chat；v0.2 支持前端传参）。 */
    private final String model;

    /** 已加载的配置。 */
    private final AgentConfig cfg;

    /** session 级对话历史缓存（按 sessionId 复用，支撑多轮对话记忆；首次触达从磁盘回填）。 */
    private final Map<String, MessageHistory> sessionHistories = new ConcurrentHashMap<>();

    /** 每会话的落盘录制器（懒创建；写盘失败时该会话降级为不落盘）。 */
    private final Map<String, SessionRecorder> sessionRecorders = new ConcurrentHashMap<>();

    /** 每会话的 {@link SessionStore}（用于关闭时 flush；与 SessionRecorder 一一对应）。 */
    private final Map<String, SessionStore> sessionStores = new ConcurrentHashMap<>();

    @Autowired
    public WebAgentRuntime(LlmProvider provider, ToolRegistry tools, TokenEstimator estimator) {
        this(
                provider,
                tools,
                estimator,
                defaultAgentDataDir(),
                loadConfig());
    }

    /**
     * 可注入构造：允许指定 agentDataDir 与配置（测试/高级装配用，避免依赖真实 {@code ~/.agent-demo}）。
     *
     * @param agentDataDir agent 数据目录
     * @param cfg          已加载配置
     */
    public WebAgentRuntime(
            LlmProvider provider, ToolRegistry tools, TokenEstimator estimator, Path agentDataDir, AgentConfig cfg) {
        this.provider = provider;
        this.tools = tools;
        this.estimator = estimator;
        this.model = "deepseek-chat";
        this.cfg = cfg;
        this.agentDataDir = agentDataDir;
    }

    /** 解析默认 agent 数据目录（{@code <user.home>/.agent-demo}，尊重 {@code AGENT_DEMO_HOME}）。 */
    private static Path defaultAgentDataDir() {
        String userHome =
                System.getenv("AGENT_DEMO_HOME") != null
                                && !System.getenv("AGENT_DEMO_HOME").isBlank()
                        ? System.getenv("AGENT_DEMO_HOME")
                        : System.getProperty("user.home");
        return Paths.get(userHome, ".agent-demo");
    }

    /** 加载默认配置（{@code <user.home>/.agent-demo/config.yaml}）。 */
    private static AgentConfig loadConfig() {
        return new ConfigLoader()
                .load(Paths.get(System.getProperty("user.home"), ".agent-demo", "config.yaml"));
    }

    /**
     * 为单个 web 会话生成 {@link AgentLoop}（按 sessionId 复用 history，支撑多轮对话记忆）。
     *
     * <p>{@code sink} 为 web 的 SSE 通知（SseSessionLogSink）；{@code confirmer} 为权限交互桥
     * （web 用 PermissionBridge）。printer 用 no-op（stdout 打印交给 CLI；web 只通过
     * SessionLogSink 下发粗粒度事件）。
     *
     * @param streamId 当前流 id（留作扩展）
     * @param sessionId 会话 id（用于复用/回填该会话的 history；同一 sessionId 连续对话共享上下文）
     * @param sink 会话日志观察者（web 转 SSE）
     * @param confirmer 权限确认器（可 null = fail-closed 拒绝）
     * @return 装配好的 {@link AgentLoop}
     */
    public AgentLoop createLoop(
            String streamId, String sessionId, SessionLogSink sink, PermissionConfirmer confirmer, AbortSignal abortSignal) {
        return createLoop(streamId, sessionId, sink, confirmer, abortSignal, null);
    }

    /**
     * 为单个 web 会话生成 {@link AgentLoop}（带初始权限模式，add-permission-mode-dropdown）。
     *
     * <p>{@code mode} 为该会话初始权限基准（{@code null} 用缺省 {@link PermissionMode#READ_ONLY}；
     * 运行期可经 {@code POST /api/chat/{stream_id}/permission} 切换）。
     */
    public AgentLoop createLoop(
            String streamId,
            String sessionId,
            SessionLogSink sink,
            PermissionConfirmer confirmer,
            AbortSignal abortSignal,
            PermissionMode mode) {
        return createLoop(streamId, sessionId, sink, confirmer, abortSignal, mode, null);
    }

    /**
     * 为单个 web 会话生成 {@link AgentLoop}（带初始权限模式 + 工作区，add-workspaces-and-rename）。
     *
     * <p>{@code workspace} 非默认时，该会话运行目录 = 工作区 dir（{@code null} 或缺省工作区 = 项目根），
     * 会话历史/存储按工作区路由。
     */
    public AgentLoop createLoop(
            String streamId,
            String sessionId,
            SessionLogSink sink,
            PermissionConfirmer confirmer,
            AbortSignal abortSignal,
            PermissionMode mode,
            String workspace) {
        return AgentLoopFactory.buildLoop(
                cfg,
                provider,
                tools,
                historyFor(workspace, sessionId),
                new StreamingPrinter(),
                model,
                sink,
                agentDataDir,
                confirmer,
                abortSignal,
                mode,
                workspaceDir(workspace));
    }

    /**
     * 按 sessionId 获取（或回填）该会话的 {@link MessageHistory}。
     *
     * <p>首次触达某个已知 {@code sessionId} 时，若磁盘存在 {@code sessions/<id>.jsonl}，则用
     * {@link SessionResumeLoader#loadById} 回填历史（超限走 {@link SessionResumeLoader#snip}），使模型
     * 在服务端重启后仍能看到重启前的对话。同一 {@code sessionId} 复用同一 history 实例。
     *
     * @param sessionId 会话 id（{@code null} 时用独立 history，不缓存）
     * @return 该会话的 history
     */
    public MessageHistory historyFor(String sessionId) {
        return historyFor(null, sessionId);
    }

    /** 按工作区 + 会话 id 获取（或回填）{@link MessageHistory}。 */
    public MessageHistory historyFor(String workspace, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new MessageHistory(estimator);
        }
        return sessionHistories.computeIfAbsent(key(workspace, sessionId), k -> restoreHistory(workspace, sessionId));
    }

    /**
     * 返回某会话当前可见的消息列表（供历史端点用）。
     *
     * <p>若会话当前在内存中活动（有进行中的对话或刚回填的 live history），返回内存历史（最新）；
     * 否则返回从磁盘回填的历史。不会改写内存缓存。
     *
     * @param sessionId 会话 id（{@code null} 时返回空）
     * @return 该会话的消息列表；无存档时为空
     */
    public List<Message> messagesFor(String sessionId) {
        return messagesFor(null, sessionId);
    }

    /** 按工作区 + 会话 id 返回当前可见消息。 */
    public List<Message> messagesFor(String workspace, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return List.of();
        MessageHistory live = sessionHistories.get(key(workspace, sessionId));
        if (live != null) return live.all();
        return restoreHistory(workspace, sessionId).all();
    }

    /**
     * 组装某会话的复合 {@link SessionLogSink}：SSE 通知 + 落盘录制。
     *
     * <p>该 sink 既把事件转发给 {@code sseSink}（web 前端），又经 {@link SessionRecorder} 追加到
     * {@code sessions/<id>.jsonl}（重进恢复用）。落盘失败时降级为仅 SSE。
     *
     * @param sessionId 会话 id（{@code null} 时不落盘）
     * @param sseSink   SSE 通知 sink（可 null）
     * @return 复合 sink
     */
    public SessionLogSink sinkFor(String sessionId, SessionLogSink sseSink) {
        return sinkFor(null, sessionId, sseSink);
    }

    /** 按工作区组装复合 sink（含落盘录制）。 */
    public SessionLogSink sinkFor(String workspace, String sessionId, SessionLogSink sseSink) {
        SessionRecorder recorder = recorderFor(workspace, sessionId);
        if (recorder == null) return sseSink == null ? SessionLogSink.NOOP : sseSink;
        return new CompositeSessionLogSink(sseSink, recorder);
    }

    /** 按会话懒创建（并缓存）落盘录制器；失败时该会话降级为不落盘并返回 null。 */
    public SessionRecorder recorderFor(String sessionId) {
        return recorderFor(null, sessionId);
    }

    /** 按工作区 + 会话懒创建落盘录制器。 */
    public SessionRecorder recorderFor(String workspace, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        String mapKey = key(workspace, sessionId);
        return sessionRecorders.computeIfAbsent(
                mapKey,
                sid -> {
                    try {
                        Path sessionsDir = sessionsDirFor(workspace);
                        Files.createDirectories(sessionsDir);
                        SessionStore store = new SessionStore(sessionsDir.resolve(sessionId + ".jsonl"), 50, 200L);
                        sessionStores.put(mapKey, store);
                        SessionLogger logger = null;
                        if (cfg.logging() != null && cfg.logging().enabled()) {
                            try {
                                logger = new SessionLogger(cfg.logging(), sessionId);
                            } catch (Exception e) {
                                log.warn("初始化 web 会话日志失败，降级为仅存档: {}", e.getMessage());
                            }
                        }
                        return new SessionRecorder(logger, store);
                    } catch (Exception e) {
                        log.warn("初始化 web 会话存档失败，降级为不落盘: {}", e.getMessage());
                        sessionStores.remove(mapKey);
                        return null;
                    }
                });
    }

    /** 判断某会话是否可恢复（内存活动或有存档文件）。 */
    public boolean hasSession(String sessionId) {
        return hasSession(null, sessionId);
    }

    /** 按工作区判断会话是否可恢复。 */
    public boolean hasSession(String workspace, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return false;
        if (sessionHistories.containsKey(key(workspace, sessionId))) return true;
        return Files.isRegularFile(sessionsDirFor(workspace).resolve(sessionId + ".jsonl"));
    }

    /** 归档（软删除）某会话：移动文件到 .archive/，并清理该会话的内存缓存与落盘录制器。 */
    public boolean archiveSession(String sessionId) {
        return archiveSession(null, sessionId);
    }

    /** 按工作区归档某会话。 */
    public boolean archiveSession(String workspace, String sessionId) {
        Path dir = sessionsDirFor(workspace);
        boolean ok = SessionStore.archive(dir, sessionId);
        if (ok) {
            String mapKey = key(workspace, sessionId);
            sessionHistories.remove(mapKey);
            SessionRecorder recorder = sessionRecorders.remove(mapKey);
            if (recorder != null) {
                try {
                    recorder.close();
                } catch (IOException e) {
                    log.warn("关闭归档会话录制器失败: {}", e.getMessage());
                }
            }
            sessionStores.remove(mapKey);
        }
        return ok;
    }

    /** 恢复某归档会话：把文件从 .archive/ 移回 sessions/。 */
    public boolean restoreSession(String sessionId) {
        return restoreSession(null, sessionId);
    }

    /** 按工作区恢复某归档会话。 */
    public boolean restoreSession(String workspace, String sessionId) {
        return SessionStore.restore(sessionsDirFor(workspace), sessionId);
    }

    /** 归档会话 id 列表（供「归档/回收站」视图，默认工作区）。 */
    public List<String> archivedIds() {
        return SessionStore.listArchived(sessionsDir());
    }

    /** 会话存档目录（默认工作区 {@code <agentDataDir>/sessions}）。 */
    public Path sessionsDir() {
        return sessionsDirFor(null);
    }

    /** 某工作区的会话存储目录（add-workspaces-and-rename）。 */
    public Path sessionsDirFor(String workspace) {
        return WorkspaceStore.sessionsDirFor(agentDataDir, workspace);
    }

    /** 某工作区的运行目录（缺省工作区=项目根）。 */
    private Path workspaceDir(String workspace) {
        if (workspace == null || workspace.isBlank()) return null;
        WorkspaceStore.Workspace ws = WorkspaceStore.get(agentDataDir, workspace);
        return ws != null ? ws.dir() : null;
    }

    /** map 复合 key（工作区 + ":" + 会话 id）。 */
    private static String key(String workspace, String sessionId) {
        String ws = workspace == null || workspace.isBlank()
                ? WorkspaceStore.DEFAULT_WORKSPACE
                : workspace;
        return ws + ":" + sessionId;
    }

    /** 首次触达某会话时的历史回填（只读磁盘，不改写存档），按工作区路由目录。 */
    private MessageHistory restoreHistory(String workspace, String sessionId) {
        MessageHistory history = new MessageHistory(estimator);
        SessionResumeLoader.ResumeResult result =
                SessionResumeLoader.loadById(sessionsDirFor(workspace), sessionId);
        if (!result.messages().isEmpty()) {
            history.replaceAll(
                    SessionResumeLoader.snip(result.messages(), estimator, MAX_RESUME_TOKENS));
        }
        return history;
    }

    public ToolRegistry tools() {
        return tools;
    }

    public Path agentDataDir() {
        return agentDataDir;
    }

    /** 关闭时 flush 并关闭所有落盘录制器（保证最后一批事件写入存档）。 */
    @PreDestroy
    public void close() {
        for (SessionRecorder recorder : sessionRecorders.values()) {
            try {
                recorder.close();
            } catch (IOException e) {
                log.warn("关闭 web 会话录制器失败: {}", e.getMessage());
            }
        }
        sessionRecorders.clear();
        sessionStores.clear();
    }
}
