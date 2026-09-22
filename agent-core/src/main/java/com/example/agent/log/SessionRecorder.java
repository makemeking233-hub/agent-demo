package com.example.agent.log;

import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.ToolCall;
import com.example.agent.session.SessionEntry;
import com.example.agent.session.SessionStore;
import com.example.agent.stats.SessionStats;
import com.example.agent.stats.TurnDelta;
import com.example.agent.tools.ToolResult;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话录制聚合器（详见 logging-design.md §5）。
 *
 * <p>实现 {@link SessionLogSink}，把主循环广播的事件同时做两件事：
 *
 * <ul>
 *   <li>转发给 {@link SessionLogger}，写四类结构化会话日志
 *   <li>把用户/助手/工具调用追加到 {@link SessionStore}，实现会话持久化（sessions/*.jsonl）
 * </ul>
 *
 * <p>{@link #close()} 会关闭内部 store 与 logger。所有方法吞异常（日志故障不打断对话）。
 */
public class SessionRecorder implements SessionLogSink, AutoCloseable {
    private final SessionLogger logger;
    private final SessionStore store;

    /** 本轮用户输入时刻（ms）；add-message-actions P2：用于算出 per-message 的 wall time。 */
    private volatile long turnStartedAtMs;

    /** 本轮最后一条 assistant 落盘时刻（ms）；0 = 本轮尚无 assistant。 */
    private volatile long lastAssistantAtMs;

    /** 本轮最后一条 assistant 条目的 uuid（add-message-actions P2）：供 SSE 侧 message_meta / 后续赞踩定位。 */
    private volatile String lastAssistantUuid;

    /**
     * 构造录制聚合器。
     *
     * @param logger 结构化会话日志器（可空）
     * @param store 会话存档（可空）
     */
    public SessionRecorder(SessionLogger logger, SessionStore store) {
        this.logger = logger;
        this.store = store;
    }

    @Override
    public void onTurnStart(int turn) {
        if (logger != null) logger.onTurnStart(turn);
    }

    @Override
    public void onUser(Message.User user) {
        if (logger != null) logger.onUser(user);
        // 本轮 wall time 起点：用户输入到回合结束（含工具执行与全部迭代）
        turnStartedAtMs = System.currentTimeMillis();
        lastAssistantAtMs = 0;
        lastAssistantUuid = null;
        safeStore(() -> store.append(SessionEntry.user(user.content(), null)));
    }

    @Override
    public void onAssistant(Message.Assistant assistant, List<String> thinking) {
        if (logger != null) logger.onAssistant(assistant, thinking);
        safeStore(
                () -> {
                    SessionEntry entry =
                            SessionEntry.assistant(assistant.content(), assistant.toolCalls(), null);
                    lastAssistantUuid = entry.uuid();
                    lastAssistantAtMs = System.currentTimeMillis();
                    store.append(entry);
                });
    }

    /**
     * 本轮最后一条 assistant 条目的 uuid（add-message-actions P2）。
     *
     * <p>SSE 侧 {@code message_meta} 事件与 {@code GET /api/sessions/{id}/messages} 的历史读数都靠它
     * 把读数绑到具体消息上。尚无 assistant 落盘（或该会话不落盘）时为 {@code null}。
     *
     * @return 最后一条 assistant 的 uuid；无则 {@code null}
     */
    public String lastAssistantUuid() {
        return lastAssistantUuid;
    }

    @Override
    public void onToolCall(ToolCall call) {
        if (logger != null) logger.onToolCall(call);
    }

    @Override
    public void onToolResult(ToolResult<?> result, long elapsedMs) {
        if (logger != null) logger.onToolResult(result, elapsedMs);
        safeStore(
                () ->
                        store.append(
                                SessionEntry.toolResult(
                                        String.valueOf(result.toolCallId()),
                                        result.toModelContent(),
                                        result.isError(),
                                        null)));
    }

    @Override
    public void onTurnEnd(TurnResult result) {
        if (logger != null) logger.onTurnEnd(result);
        // 先算读数（在 append 之前，保证同一 syncFlush 批次里 message_meta 紧跟 tokens）
        Map<String, Object> messageMeta = messageMeta(result);
        safeStore(() -> {
            store.append(SessionEntry.meta("tokens", List.of(result.totalPromptTokens(), result.totalCompletionTokens())));
            if (messageMeta != null) {
                store.append(SessionEntry.meta("message_meta", messageMeta));
            }
            store.syncFlush();
        });
    }

    /**
     * 组装本轮 per-message 读数（add-message-actions P2），供落盘与历史回填。
     *
     * <p>字段口径与 SSE {@code message_meta} 事件逐字一致，避免「刷新前后读数不一样」。
     * 无 assistant 落盘（uuid 为空）时返回 {@code null}（不写无主读数）。
     *
     * @param result 本轮结果（不可空）
     * @return 可直接放进 {@code SessionEntry.extras.value} 的 map；无可归属消息时 {@code null}
     */
    private Map<String, Object> messageMeta(TurnResult result) {
        String uuid = lastAssistantUuid;
        if (uuid == null) return null;
        TurnDelta delta = result == null ? null : result.delta();
        // 复用会话统计的派生口径：ttft = 均值，tok/s = tokensOut / 纯生成耗时
        SessionStats perTurn = SessionStats.empty().plus(delta);
        long durationMs =
                lastAssistantAtMs > 0 && turnStartedAtMs > 0
                        ? lastAssistantAtMs - turnStartedAtMs
                        : (delta == null ? 0L : delta.llmMillis() + delta.toolMillis());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuid", uuid);
        m.put("duration_ms", Math.max(0L, durationMs));
        m.put("ttft_ms", perTurn.avgTtftMs());
        m.put("tok_per_sec", perTurn.tokPerSec());
        m.put("timestamp", System.currentTimeMillis());
        return m;
    }

    @Override
    public void onContextSnapshot(ContextSnapshot snapshot) {
        // context/snapshot 仅写入结构化日志，不进 SessionStore 存档（存档只含对话消息）
        if (logger != null) logger.onContextSnapshot(snapshot);
    }

    @Override
    public void onSystemEvent(String type, Map<String, Object> payload) {
        if (logger != null) logger.onSystemEvent(type, payload);
    }

    @Override
    public void onPermissionDecision(Map<String, Object> payload) {
        if (logger != null) logger.onPermissionDecision(payload);
    }

    /** 关键节点主动刷盘（如 /clear 前） */
    public void flush() {
        if (logger != null) logger.flush();
        safeStore(store::syncFlush);
    }

    private void safeStore(StoreRunnable r) {
        if (store == null) return;
        try {
            r.run();
        } catch (Exception ignored) {
            // 存档失败不打断对话
        }
    }

    @Override
    public void close() throws IOException {
        if (store != null) {
            try {
                store.close();
            } catch (IOException ignored) {
            }
        }
        if (logger != null) logger.close();
    }

    @FunctionalInterface
    private interface StoreRunnable {
        void run() throws Exception;
    }
}
