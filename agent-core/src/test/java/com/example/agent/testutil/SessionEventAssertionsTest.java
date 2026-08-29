package com.example.agent.testutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * SessionEventAssertions 工具自身单测。
 */
class SessionEventAssertionsTest {
    @TempDir Path tmp;

    private Path writeEvents(String... lines) throws Exception {
        Path p = tmp.resolve("session.jsonl");
        Files.writeString(p, String.join("\n", lines) + "\n");
        return p;
    }

    @Test
    void readsAndFiltersByType() throws Exception {
        Path p =
                writeEvents(
                        "{\"seq\":0,\"type\":\"turn/start\",\"turn\":0}",
                        "{\"seq\":1,\"type\":\"user/message\",\"content\":\"hi\"}",
                        "{\"seq\":2,\"type\":\"turn/end\",\"turn\":0}");
        var events = SessionEventAssertions.readEvents(p);
        assertEquals(3, events.size());
        assertEquals(1, SessionEventAssertions.byType(events, "user/message").size());
        assertEquals(
                List.of("turn/start", "user/message", "turn/end"),
                SessionEventAssertions.typeSequence(events));
    }

    @Test
    void skipsBadLines() throws Exception {
        Path p = writeEvents("{\"type\":\"session\"}", "not-json{{{", "");
        var events = SessionEventAssertions.readEvents(p);
        assertEquals(1, events.size());
    }

    @Test
    void normalizesVolatileFields() throws Exception {
        Path p =
                writeEvents(
                        "{\"seq\":0,\"timestamp\":1785000000000,\"type\":\"tool/call\",\"callId\":\"call_1\",\"turn\":0}");
        List<Map<String, Object>> norm =
                SessionEventAssertions.normalized(SessionEventAssertions.readEvents(p));
        assertEquals("<n>", norm.get(0).get("seq"));
        assertEquals("<n>", norm.get(0).get("timestamp"));
        assertEquals("<n>", norm.get(0).get("callId"));
        assertEquals("tool/call", norm.get(0).get("type"));
        assertTrue(true);
    }
}
