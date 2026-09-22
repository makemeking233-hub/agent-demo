package com.example.agent.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.session.SessionEntry;
import com.example.agent.session.SessionStore;
import com.example.agent.stats.TurnDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * SessionRecorder 透传测试：三类新事件转发到 SessionLogger，logger/store 为 null 时零副作用。
 */
class SessionRecorderTest {

    @Test
    void nullLoggerAndStoreAcceptNewEvents() {
        SessionRecorder recorder = new SessionRecorder(null, null);
        assertDoesNotThrow(
                () -> recorder.onContextSnapshot(new ContextSnapshot(0, "", false, false, java.util.List.of(), java.util.List.of(), 0, 0)));
        assertDoesNotThrow(() -> recorder.onSystemEvent("system/config", Map.of("provider", "deepseek")));
        assertDoesNotThrow(() -> recorder.onPermissionDecision(Map.of("tool", "Shell", "decision", "ask")));
    }

    @Test
    void newEventsAreForwardedToLogger() {
        SessionLogger logger = mock(SessionLogger.class);
        SessionRecorder recorder = new SessionRecorder(logger, null);

        ContextSnapshot snapshot =
                new ContextSnapshot(1, "sys", true, false, java.util.List.of(), java.util.List.of("Ls"), 4, 300);
        recorder.onContextSnapshot(snapshot);
        recorder.onSystemEvent("system/retry", Map.of("attempt", 2));
        recorder.onPermissionDecision(Map.of("decision", "deny"));

        verify(logger).onContextSnapshot(snapshot);
        verify(logger).onSystemEvent("system/retry", Map.of("attempt", 2));
        verify(logger).onPermissionDecision(Map.of("decision", "deny"));
    }

    // ---------- add-message-actions P2：per-message 读数落盘 ----------

    /**
     * 回合结束时应追加一条 {@code meta(key="message_meta")} 条目，携带本轮 assistant 的 uuid 与
     * TTFT / 吞吐读数——这正是「刷新后 clock 仍在」的数据来源。
     */
    @Test
    void turnEndPersistsMessageMetaWithAssistantUuid(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("s-1.jsonl");
        SessionStore store = new SessionStore(file, 50, 60_000L);
        SessionRecorder recorder = new SessionRecorder(null, store);
        recorder.onUser(new Message.User("问"));
        recorder.onAssistant(new Message.Assistant("答", List.of()), List.of());
        recorder.onTurnEnd(
                new TurnResult("答", 100, 300, 0, new TurnDelta(0, 100, 300, 0, 2000, 0, 500, 1, null, null)));
        store.close();

        List<SessionEntry> entries = SessionStore.loadFile(file);
        SessionEntry assistant = entries.stream().filter(e -> "assistant".equals(e.type())).findFirst().orElseThrow();
        SessionEntry meta =
                entries.stream()
                        .filter(e -> "meta".equals(e.type()))
                        .filter(e -> e.extras() != null && "message_meta".equals(e.extras().get("key")))
                        .findFirst()
                        .orElseThrow();
        assertThat(assistant.uuid()).isEqualTo(recorder.lastAssistantUuid());
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) meta.extras().get("value");
        assertThat(value.get("uuid")).isEqualTo(recorder.lastAssistantUuid());
        assertThat(value.get("ttft_ms")).isEqualTo(500.0);
        assertThat(value.get("tok_per_sec")).isEqualTo(200.0);
        assertThat(((Number) value.get("duration_ms")).longValue()).isGreaterThanOrEqualTo(0L);
    }

    /** 本轮没有任何 assistant 落盘时不写「无主读数」（uuid 为空的 meta 没有意义）。 */
    @Test
    void turnEndSkipsMessageMetaWhenNoAssistant(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("s-2.jsonl");
        SessionStore store = new SessionStore(file, 50, 60_000L);
        SessionRecorder recorder = new SessionRecorder(null, store);
        recorder.onUser(new Message.User("问"));
        recorder.onTurnEnd(new TurnResult("", 0, 0, 0, TurnDelta.empty()));
        store.close();

        assertThat(recorder.lastAssistantUuid()).isNull();
        assertThat(
                        SessionStore.loadFile(file).stream()
                                .filter(e -> "meta".equals(e.type()))
                                .filter(e -> e.extras() != null && "message_meta".equals(e.extras().get("key")))
                                .count())
                .isZero();
    }
}

