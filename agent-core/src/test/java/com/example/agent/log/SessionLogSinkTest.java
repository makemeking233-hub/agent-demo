package com.example.agent.log;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * SessionLogSink 新事件方法（context/snapshot、system/*、permission/decision）的契约测试：
 * default 实现必须零副作用，已有实现类不受影响。
 */
class SessionLogSinkTest {

    @Test
    void noopSinkAcceptsAllNewMethods() {
        SessionLogSink sink = SessionLogSink.NOOP;
        ContextSnapshot snapshot =
                new ContextSnapshot(
                        0,
                        "system",
                        true,
                        false,
                        List.of(),
                        List.of("ReadFile"),
                        3,
                        120);
        assertDoesNotThrow(() -> sink.onContextSnapshot(snapshot));
        assertDoesNotThrow(() -> sink.onSystemEvent("system/config", Map.of("provider", "deepseek")));
        assertDoesNotThrow(() -> sink.onPermissionDecision(Map.of("tool", "Shell", "decision", "ask")));
    }

    @Test
    void existingImplementationsUnaffectedByNewDefaults() {
        // 只实现旧方法的匿名 sink：新 default 方法直接走空实现，不抛异常
        SessionLogSink sink =
                new SessionLogSink() {
                    @Override
                    public void onTurnStart(int turn) {
                        // 仅验证默认实现不干扰
                    }
                };
        assertDoesNotThrow(() -> sink.onContextSnapshot(new ContextSnapshot(1, "", false, false, List.of(), List.of(), 0, 0)));
        assertDoesNotThrow(() -> sink.onSystemEvent("system/retry", Map.of("attempt", 2)));
        assertDoesNotThrow(() -> sink.onPermissionDecision(Map.of()));
    }

    @Test
    void contextSnapshotRecordCarriesAllFields() {
        ContextSnapshot s =
                new ContextSnapshot(
                        2, "system prompt", true, true, List.of("a.txt"), List.of("Ls"), 5, 800);
        assertEquals(2, s.turn());
        assertEquals("system prompt", s.systemPrompt());
        assertEquals(true, s.memoryInjected());
        assertEquals(true, s.compacted());
        assertEquals(List.of("a.txt"), s.recentFiles());
        assertEquals(List.of("Ls"), s.toolNames());
        assertEquals(5, s.messageCount());
        assertEquals(800, s.estTokens());
    }
}
