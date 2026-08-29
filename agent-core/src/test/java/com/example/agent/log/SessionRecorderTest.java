package com.example.agent.log;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

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
}
