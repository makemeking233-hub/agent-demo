package com.example.agent.web.stream;

import com.example.agent.core.AgentLoop;
import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.log.SessionLogSink;
import com.example.agent.log.SessionRecorder;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.permission.PermissionMode;
import com.example.agent.session.WorkspaceStore;
import com.example.agent.stats.SessionStats;
import com.example.agent.stats.TurnDelta;
import com.example.agent.web.api.dto.SseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
@Profile("web")
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    /** 每流后台执行槽（跑 AgentLoop.processTurn，不阻塞 HTTP handler 线程）。 */
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, ActiveStream> actives = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final java.util.concurrent.atomic.AtomicLong lastEventId =
            new java.util.concurrent.atomic.AtomicLong(0);
    private final WebAgentRuntime runtime;
    private final PermissionBridge permissionBridge;

    public ChatStreamService(WebAgentRuntime runtime, PermissionBridge permissionBridge) {
        this.runtime = runtime;
        this.permissionBridge = permissionBridge;
    }

    /**
     * 活动流：一个 SSE sink + 关联的 AgentLoop（单会话）+ 该会话的权限桥。
     *
     * @param streamId SSE 流 id
     * @param sessionId 会话 id
     * @param model 模型名
     * @param startedAt 开始时间戳（ms）
     * @param sink SSE 发布 sink
     * @param loop 该会话的 AgentLoop（可空，调用 start 时装配）
     * @param sinkAdapter SSE 事件观察者（把 AgentLoop 回调转 SSE 事件）
     * @param aborted 中断标记
     * @param workspace 归属工作区（add-session-stats-bar：统计按工作区路由）
     * @param logSink 复合 session sink（SSE + 落盘；rewrite-permission-mode-dsh T8.2 引入，可空）
     */
    public record ActiveStream(
            String streamId,
            String sessionId,
            String model,
            long startedAt,
            Sinks.Many<ServerSentEvent<Object>> sink,
            AgentLoop loop,
            SseSessionLogSink sinkAdapter,
            java.util.concurrent.atomic.AtomicBoolean aborted,
            String workspace,
            SessionLogSink logSink) {
        /** 9 参便捷构造：{@code logSink} 为 {@code null}（仅 SSE，不落盘）。 */
        public ActiveStream(
                String streamId,
                String sessionId,
                String model,
                long startedAt,
                Sinks.Many<ServerSentEvent<Object>> sink,
                AgentLoop loop,
                SseSessionLogSink sinkAdapter,
                java.util.concurrent.atomic.AtomicBoolean aborted,
                String workspace) {
            this(streamId, sessionId, model, startedAt, sink, loop, sinkAdapter, aborted, workspace, null);
        }
    }

    public ActiveStream create(String sessionId, String model) {
        return create(sessionId, model, null);
    }

    public ActiveStream create(String sessionId, String model, PermissionMode mode) {
        return create(sessionId, model, mode, null);
    }

    public ActiveStream create(String sessionId, String model, PermissionMode mode, String workspace) {
        return create(sessionId, model, mode, workspace, null);
    }

    /**
     * 创建一条活动流（带初始权限模式 + 工作区 + 思考强度，add-models-dropdown-v0）。
     *
     * <p>新增 {@code reasoningEffort} 参数透传到 {@code AgentLoop.setReasoningEffort(...)}：
     * {@code null} = 不切换（沿用 Provider 内部默认）。
     *
     * @param sessionId 会话 id
     * @param model 模型名
     * @param mode 初始权限模式（{@code null} 用缺省 {@link PermissionMode#READ_ONLY}）
     * @param workspace 归属工作区（{@code null} 用默认工作区）
     * @param reasoningEffort 思考强度（{@code low} / {@code medium} / {@code high}；{@code null} = 沿用 Provider 默认）
     * @return 活动流元数据
     */
    public ActiveStream create(
            String sessionId,
            String model,
            PermissionMode mode,
            String workspace,
            String reasoningEffort) {
        return create(null, sessionId, model, mode, workspace, reasoningEffort);
    }

    /**
     * 创建一条活动流（add-provider-catalog-abstract task 7.1：新增 6 参重载）。
     *
     * <p>新增 {@code providerId} 字段透传到 {@code AgentLoop.setProviderId(providerId)}；多 provider 路由
     * （v0.2）会在 {@code WebAgentRuntime.createLoop} 按 providerId 选 provider bean。当前 v0.1
     * 仍只路由 DeepSeek，故 providerId 仅写到 AgentLoop 用于后续 validateProvider 校验，不影响
     * 实际 HTTP 路由。
     *
     * @param providerId   provider 标识（{@code deepseek} / {@code openai} / {@code anthropic}）；
     *                     {@code null} = 不切换（沿用 AgentLoop 默认）
     * @param sessionId    会话 id
     * @param model        模型名
     * @param mode         初始权限模式
     * @param workspace    工作区
     * @param reasoningEffort 思考强度
     * @return 活动流元数据
     */
    public ActiveStream create(
            String providerId,
            String sessionId,
            String model,
            PermissionMode mode,
            String workspace,
            String reasoningEffort) {
        String streamId = UUID.randomUUID().toString();
        // replay().all(): 延迟订阅者(客户端 turn 完成后再连)能收到全部事件 + complete,
        // 支撑 spec §resume/Last-Event-ID 与测试中 send→stream 的先后时序。
        Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().replay().all();
        SseSessionLogSink adapter = new SseSessionLogSink(this, streamId);
        java.util.concurrent.atomic.AtomicBoolean aborted = new java.util.concurrent.atomic.AtomicBoolean(false);
        // 权限桥包装成 PermissionConfirmer：confirm(prompt) emit permission_request 后阻塞等待前端决策。
        PermissionConfirmer confirmer =
                prompt -> {
                    String permissionId = permissionBridge.newPermissionId();
                    // AgentLoop 传的是 "toolName → 描述" 的 prompt; 取 toolName 首段, reason 为全文。
                    String toolName = prompt.split("→", 2)[0].trim();
                    emit(streamId, new SseEvent.PermissionRequest(permissionId, null, toolName, prompt, DECISION_CHOICES));
                    String decision =
                            permissionBridge.waitForDecision(permissionId, null, toolName, prompt, DECISION_CHOICES);
                    emit(streamId, new SseEvent.PermissionResponse(permissionId, decision));
                    // "yes" 与 "always" 都放行（v0.1 简化：always 不持久化到 PermissionPolicy，
                    // 仅本次放行；v0.2 应改为真正记忆同类工具为 allowAll）。
                    // 修 bug: 之前只 "yes".equals(decision) 导致 "always" 被错误当成拒绝.
                    return !"no".equals(decision);
                };
        // abort 信号: abort() 置 true, AgentLoop 工具执行会感知并中断。
        // v0.3 会话重进恢复：用复合 sink (SSE + 落盘)，使该会话持续写入 sessions/<id>.jsonl。
        SessionLogSink sessionSink = runtime.sinkFor(workspace, sessionId, adapter);
        AgentLoop loop = runtime.createLoop(streamId, sessionId, model, sessionSink, confirmer, aborted::get, mode, workspace);
        // add-models-dropdown-v0：透传 reasoningEffort 到 AgentLoop volatile 字段
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            loop.setReasoningEffort(reasoningEffort);
        }
        // add-provider-catalog-abstract task 7.2：透传 provider + model 到 AgentLoop
        // loop 可能在测试中 mock 为 null（只验证 createLoop 参数），用 null-check 跳过副作用调用
        if (loop != null) {
            if (providerId != null && !providerId.isBlank()) {
                loop.setProviderId(providerId);
            }
            loop.setModel(model);
        }
        ActiveStream meta =
                new ActiveStream(
                        streamId, sessionId, model, System.currentTimeMillis(), sink, loop, adapter, aborted, workspace, sessionSink);
        actives.put(streamId, meta);
        emit(meta, new SseEvent.MessageStart(streamId, sessionId, model, System.currentTimeMillis()));
        // T8.1: 初始 mode 广播 (reason=initial)
        emitSandboxMode(meta, null,
                mode != null ? mode.toSandboxMode().wireValue() : PermissionMode.DEFAULT.toSandboxMode().wireValue(),
                "initial");
        return meta;
    }

    /**
     * 启动一个 turn：在后台执行 {@link AgentLoop#processTurn(Message.User)}。SSE 事件由
     * {@link SseSessionLogSink} 下发到本流。turn 结束（自然或异常）后关闭流。
     *
     * @param streamId 已创建流 id
     * @param content 用户消息
     * @return 是否找到该流并已启动
     */
    public boolean start(String streamId, String content) {
        ActiveStream meta = actives.get(streamId);
        if (meta == null) return false;
        AgentLoop loop = meta.loop();
        executor.execute(
                () -> {
                    try {
                        loop.processTurn(new Message.User(content))
                                .block(Duration.ofMinutes(30));
                        // fix-stale-model-fallback：成功路径也记 model。此前**只有失败路径**记，
                        // 于是「前端选了什么模型 vs 上游实际收到什么模型」无法靠日志对照，
                        // 只能靠浏览器抓包。字段与下面的 turn failed 逐字对齐，便于直接比对。
                        log.info(
                                "turn completed stream={} session={} workspace={} model={}",
                                streamId,
                                meta.sessionId(),
                                meta.workspace(),
                                meta.model());
                    } catch (Throwable t) {
                        // JVM 级致命错误（OOM / StackOverflow）原样抛出，不降级为回合失败
                        com.example.agent.core.Throwables.reraiseIfJvmFatal(t);
                        // improve-failure-observability：这里**必须**捕获 Throwable。工具抛出的 Error
                        // （如 NoClassDefFoundError）会被 Reactor 的 Exceptions.throwIfFatal 原样
                        // rethrow，绕过 AgentLoop 的 doOnError / onErrorResume，只有订阅侧这个边界
                        // 才兜得住。此前只 catch Exception，导致这类失败在日志里零记录，且只留下
                        // 一行不含 sessionId 的 "turn failed for stream"。
                        log.error(
                                "turn failed stream={} session={} workspace={} model={} error={}: {}",
                                streamId,
                                meta.sessionId(),
                                meta.workspace(),
                                meta.model(),
                                t.getClass().getName(),
                                t.getMessage(),
                                t);
                        // 收口在途工具调用：补 TOOL< 与 history，避免留下悬挂 tool_calls（会让该会话
                        // 此后每轮被上游 400）
                        try {
                            loop.closePendingToolCalls(
                                    "回合异常中断: " + t.getClass().getSimpleName());
                        } catch (Throwable ignored) {
                            // 收口本身失败不应阻碍关流
                        }
                        emit(meta, new SseEvent.Error("turn_failed", String.valueOf(t.getMessage())));
                        stop(streamId, "error");
                    }
                });
        return true;
    }

    public Flux<ServerSentEvent<Object>> stream(String streamId) {
        ActiveStream meta = actives.get(streamId);
        if (meta == null) {
            return Flux.error(new IllegalArgumentException("stream_not_found: " + streamId));
        }
        return meta.sink().asFlux().timeout(Duration.ofMinutes(30));
    }

    public void emit(ActiveStream meta, SseEvent event) {
        if (meta == null || meta.sink() == null) {
            log.debug("emit to unknown/closed stream: {}", event.type());
            return;
        }
        try {
            String json = mapper.writeValueAsString(event);
            long id = lastEventId.incrementAndGet();
            ServerSentEvent<Object> sse =
                    ServerSentEvent.builder((Object) json).id(String.valueOf(id)).event(event.type()).build();
            Sinks.EmitResult result = meta.sink().tryEmitNext(sse);
            if (result.isFailure()) {
                log.debug("emit dropped for stream {}: {}", meta.streamId(), result);
            }
        } catch (Exception e) {
            log.warn("emit serialization failed: {}", e.toString());
        }
    }

    public void emit(String streamId, SseEvent event) {
        emit(actives.get(streamId), event);
    }

    public void stop(String streamId, String finishReason) {
        ActiveStream meta = actives.get(streamId);
        if (meta == null) return;
        emit(meta, new SseEvent.MessageStop(finishReason));
        meta.sink().tryEmitComplete();
        // 不立即从 map 移除: replay sink 保留全部事件, 延迟订阅 / 重连 (spec §resume)
        // 仍能通过 get(streamId) 拿到已完成的流。由 TTL 清理 + shutdown 回收。
        evictIfExpired(meta, STREAM_RETENTION_MS);
    }

    /** 保留时长上限(ms): 完成后仍允许客户端在此窗口内订阅 / 用 Last-Event-ID 重连。 */
    private static final long STREAM_RETENTION_MS = 10 * 60 * 1000L;

    /** 权限决策选项 (spec §Requirement: permission_request 载荷)。 */
    private static final List<String> DECISION_CHOICES = List.of("yes", "no", "always");

    /**
     * 用户提交权限决策 (spec §Requirement: permission_request 决策)。把 yes/no/always 交给
     * {@link PermissionBridge#submitDecision} 唤醒等待线程。返回 {@code true} 表示找到待决策流并已提交。
     *
     * @param streamId 流 id
     * @param permissionId 待决策的 permission_id
     * @param decision 决策值 (yes/no/always)
     * @return 是否成功提交 (找到 permission_id 且决策合法)
     */
    public boolean submitDecision(String streamId, String permissionId, String decision) {
        if (actives.get(streamId) == null) return false;
        return permissionBridge.submitDecision(permissionId, decision);
    }

    /**
     * 推送单条 assistant 消息的读数（add-message-actions P2）。
     *
     * <p>在 {@code turn_stats} 与 {@code message_stop} **之前**推送，携带本轮（而非会话累计）的
     * wall time / TTFT / 吞吐，供前端在消息底部渲染 DSH 风格 clock。
     *
     * <p>uuid 取该会话落盘录制器记录的「本轮最后一条 assistant 条目 uuid」；会话不落盘时为
     * {@code null}（前端退化为只显示时间）。派生指标复用 {@link SessionStats} 的口径，
     * 保证与底部统计栏一致。
     *
     * @param streamId   流 id
     * @param result     本轮结果（可空；空时读数退化为 N/A）
     * @param durationMs 本轮 wall time（毫秒）
     */
    public void emitMessageMeta(String streamId, TurnResult result, long durationMs) {
        ActiveStream meta = actives.get(streamId);
        if (meta == null) return;
        SessionRecorder recorder = runtime.recorderIfPresent(meta.workspace(), meta.sessionId());
        String uuid = recorder == null ? null : recorder.lastAssistantUuid();
        TurnDelta delta = result != null ? result.delta() : null;
        SessionStats perTurn = SessionStats.empty().plus(delta);
        emit(
                meta,
                new SseEvent.MessageMeta(
                        uuid, durationMs, perTurn.avgTtftMs(), perTurn.tokPerSec(), System.currentTimeMillis()));
    }

    /**
     * 回合结束（add-session-stats-bar）：累加该会话统计 → 推送 {@code turn_stats} → 停止流。
     *
     * <p>在 {@code message_stop} 之前推送，携带会话累计值。
     *
     * @param streamId 流 id
     * @param result   本轮结果（可空；空时只读当前累计值）
     */
    public void onTurnEnd(String streamId, TurnResult result) {
        ActiveStream meta = actives.get(streamId);
        if (meta != null) {
            // T8.3: turn 结束时恢复 escalate 前的 mode + 广播 turn_end_restore
            String beforeRestore = meta.loop() != null && meta.loop().permissionMode() != null
                    ? meta.loop().permissionMode().toSandboxMode().wireValue()
                    : null;
            boolean restored = meta.loop() != null && meta.loop().restoreEscalatedPermission(streamId);
            if (restored) {
                String afterRestore = meta.loop().permissionMode() != null
                        ? meta.loop().permissionMode().toSandboxMode().wireValue()
                        : null;
                emitSandboxMode(meta, beforeRestore, afterRestore, "turn_end_restore");
            }
            TurnDelta delta = result != null ? result.delta() : null;
            SessionStats stats = runtime.accumulateStats(meta.workspace(), meta.sessionId(), delta);
            // 防御：mock/异常路径可能返回 null，退化为空统计而非 NPE。
            emit(meta, new SseEvent.TurnStats(stats != null ? stats : SessionStats.empty()));
        }
        stop(streamId, "stop");
    }

    /** 工作区是否存在（空/null = 默认为 true，走默认工作区）。 */
    public boolean workspaceExists(String workspace) {
        return workspace == null
                || workspace.isBlank()
                || WorkspaceStore.exists(runtime.agentDataDir(), workspace);
    }

    /** 惰性清理: 超过保留时长的已结束流从 map 移除, 防内存泄漏。 */
    private void evictIfExpired(ActiveStream meta, long retentionMs) {
        long ttl = meta.startedAt() + retentionMs;
        // 简单惰性: 仅当流确实完成且超过 TTL 才移除; 由下一次 create/get 触发
        if (System.currentTimeMillis() > ttl) {
            actives.remove(meta.streamId());
        }
    }

    public void abort(String streamId) {
        ActiveStream meta = actives.get(streamId);
        if (meta == null) return;
        // 置中断信号, 让 AgentLoop 工具执行感知并尽快停止
        if (meta.aborted() != null) {
            meta.aborted().set(true);
        }
        emit(meta, new SseEvent.Error("aborted", "turn aborted by user"));
        stop(streamId, "aborted");
    }

    /**
     * 实时切换某流的权限模式（add-permission-mode-dropdown + rewrite-permission-mode-dsh T7.1/T8.1）。
     *
     * <p>T8.1：切换时广播 SSE {@code sandbox/mode} 事件 + 落盘 session.jsonl。
     *
     * @param streamId 流 id
     * @param mode 新模式（不可空）
     * @param escalate 是否 escalate（{@code true} 表示临时升级，turn 结束自动恢复）
     * @return 是否找到该流并已切换
     */
    public boolean setPermission(String streamId, PermissionMode mode, boolean escalate) {
        ActiveStream meta = actives.get(streamId);
        if (meta == null || meta.loop() == null) return false;
        String fromMode = meta.loop().permissionMode() != null
                ? meta.loop().permissionMode().toSandboxMode().wireValue()
                : null;
        if (escalate) {
            meta.loop().escalatePermission(streamId, mode);
        } else {
            meta.loop().setPermissionMode(mode);
        }
        emitSandboxMode(meta, fromMode, mode.toSandboxMode().wireValue(),
                escalate ? "escalate" : "user_set");
        return true;
    }

    /** v0.1 兼容：不带 escalate 参数 */
    public boolean setPermission(String streamId, PermissionMode mode) {
        return setPermission(streamId, mode, false);
    }

    /**
     * 广播 sandbox/mode 事件（SSE + session.jsonl 落盘；T8.1 + T8.2）。
     *
     * @param meta     活动流
     * @param fromMode 变更前 mode（wire value；首设为 null）
     * @param toMode   变更后 mode（wire value）
     * @param reason   变更原因（initial / user_set / escalate / turn_end_restore）
     */
    private void emitSandboxMode(ActiveStream meta, String fromMode, String toMode, String reason) {
        long ts = System.currentTimeMillis();
        // T8.1: SSE 广播
        emit(meta, new SseEvent.SandboxModeChanged(meta.streamId(), fromMode, toMode, reason, ts));
        // T8.2: 落盘 (SessionLogger.onSystemEvent 追加到 session.jsonl)
        SessionLogSink sink = meta.logSink();
        if (sink != null) {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("stream_id", meta.streamId());
            payload.put("from_mode", fromMode != null ? fromMode : "");
            payload.put("to_mode", toMode);
            payload.put("reason", reason);
            payload.put("ts", ts);
            sink.onSystemEvent("sandbox/mode", payload);
        }
    }

    public ActiveStream get(String streamId) {
        return actives.get(streamId);
    }

    /**
     * 指定会话是否存在活动流（auto-archive-stale-sessions）。
     *
     * <p>供自动归档跳过进行中的会话：这类会话随时会继续写入，把存档文件搬走会破坏后续写入。
     *
     * @param sessionId 会话 id（{@code null} 视为不活动）
     * @return 是否存在归属该会话的未结束流
     */
    public boolean isSessionActive(String sessionId) {
        if (sessionId == null) return false;
        return actives.values().stream().anyMatch(s -> sessionId.equals(s.sessionId()));
    }

    @PreDestroy
    public void shutdown() {
        actives.forEach((id, meta) -> meta.sink().tryEmitComplete());
        actives.clear();
        executor.shutdownNow();
    }
}
