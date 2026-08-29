package com.example.agent.log;

import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.ToolCall;
import com.example.agent.session.SessionEntry;
import com.example.agent.session.SessionStore;
import com.example.agent.tools.ToolResult;

import java.io.IOException;
import java.util.List;

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
        safeStore(() -> store.append(SessionEntry.user(user.content(), null)));
    }

    @Override
    public void onAssistant(Message.Assistant assistant, List<String> thinking) {
        if (logger != null) logger.onAssistant(assistant, thinking);
        safeStore(() -> store.append(SessionEntry.assistant(assistant.content(), assistant.toolCalls(), null)));
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
        safeStore(() -> {
            store.append(SessionEntry.meta("tokens", List.of(result.totalPromptTokens(), result.totalCompletionTokens())));
            store.syncFlush();
        });
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
