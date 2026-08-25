package com.example.agent.agent;

import com.example.agent.provider.TokenEstimator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageHistoryTest {
    private final TokenEstimator est = new TokenEstimator();
    private final MessageHistory hist = new MessageHistory(est);

    @Test
    void appendIncreasesSize() {
        hist.append(new Message.User("hello"));
        assertEquals(1, hist.size());
    }

    @Test
    void tokenEstimateAccumulates() {
        hist.append(new Message.User("hello world"));
        hist.append(new Message.User("你好世界"));
        int t = hist.estimateTokens();
        assertTrue(t > 0, "estimateTokens 应 > 0；实际 " + t);
    }

    @Test
    void compactFailuresIncrement() {
        assertEquals(0, hist.consecutiveCompactFailures());
        hist.incrementCompactFailures();
        hist.incrementCompactFailures();
        assertEquals(2, hist.consecutiveCompactFailures());
        hist.resetCompactFailures();
        assertEquals(0, hist.consecutiveCompactFailures());
    }

    @Test
    void appendToolResults() {
        hist.append(new Message.Assistant("ok", java.util.List.of()));
        hist.appendToolResults(java.util.List.of(
            new MessageHistory.ToolResultEnvelope("call_1", "result", false)));
        assertEquals(2, hist.size());
        assertTrue(hist.last() instanceof Message.ToolResult);
        assertEquals("call_1", ((Message.ToolResult) hist.last()).toolCallId());
    }

    @Test
    void rememberAndReinjectFiles() {
        hist.rememberFileContent("/tmp/a.txt", "line1\nline2\nline3");
        hist.append(new Message.User("look at file"));
        hist.reinjectRecentFileContents(200);
        // reinject 插入到头部（index 0）
        Message top = hist.all().get(0);
        assertTrue(top instanceof Message.System);
        assertTrue(((Message.System) top).content().contains("[RECENT FILES]"));
        assertTrue(((Message.System) top).content().contains("/tmp/a.txt"));
    }
}