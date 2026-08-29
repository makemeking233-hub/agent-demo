package com.example.agent.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.Message;
import com.example.agent.core.TurnResult;
import com.example.agent.llm.ToolCall;
import com.example.agent.tools.ToolResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class SessionLoggerTest {
    @TempDir Path tmp;

    private AgentConfig.Logging logging() {
        return new AgentConfig.Logging(true, tmp.toString(), 100);
    }

    @Test
    void openCreatesFourFilesAndHeader() throws Exception {
        try (SessionLogger l = new SessionLogger(logging(), "sess-001")) {
            assertTrue(Files.exists(l.sessionDir().resolve("session.jsonl")));
            assertTrue(Files.exists(l.sessionDir().resolve("chat.log")));
            assertTrue(Files.exists(l.sessionDir().resolve("thinking.log")));
            assertTrue(Files.exists(l.sessionDir().resolve("tools.log")));
            String first = Files.readString(l.sessionDir().resolve("session.jsonl")).split("\n")[0];
            assertTrue(first.contains("\"type\":\"session\""));
            assertTrue(first.contains("\"version\":2"));
        }
    }

    @Test
    void userAndAssistantGoToSessionAndChatLogs() throws Exception {
        Path dir;
        try (SessionLogger l = new SessionLogger(logging(), "sess-002")) {
            dir = l.sessionDir();
            l.onUser(new Message.User("你好"));
            l.onAssistant(new Message.Assistant("你好！", null), List.of());
        }
        String session = Files.readString(dir.resolve("session.jsonl"));
        assertTrue(session.contains("\"type\":\"user/message\""));
        assertTrue(session.contains("\"type\":\"assistant/message\""));
        String chat = Files.readString(dir.resolve("chat.log"));
        assertTrue(chat.contains("用户"));
        assertTrue(chat.contains("助手"));
        assertTrue(chat.contains("你好"));
    }

    @Test
    void seqIncreasesAcrossEvents() throws Exception {
        try (SessionLogger l = new SessionLogger(logging(), "sess-003")) {
            l.onTurnStart(0);
            l.onUser(new Message.User("hi"));
            String session = Files.readString(l.sessionDir().resolve("session.jsonl"));
            long[] seqs =
                    java.util.stream.Stream.of(session.split("\n"))
                            .filter(line -> line.contains("\"seq\""))
                            .map(String::trim)
                            .map(line -> line.replaceAll(".*\"seq\":(\\d+).*", "$1"))
                            .mapToLong(Long::parseLong)
                            .toArray();
            // header 行 seq=0，随后 turn/start seq=1，user/message seq=2
            assertEquals(0, seqs[0]);
            assertEquals(1, seqs[1]);
            assertEquals(2, seqs[2]);
        }
    }

    @Test
    void toolCallAndResultGoToSessionAndToolsLogs() throws Exception {
        try (SessionLogger l = new SessionLogger(logging(), "sess-004")) {
            l.onToolCall(new ToolCall("call_1", "ReadFile", "{\"path\":\"README.md\"}"));
            l.onToolResult(ToolResult.ok("contents", "call_1"), 12L);
            String session = Files.readString(l.sessionDir().resolve("session.jsonl"));
            assertTrue(session.contains("\"type\":\"tool/call\""));
            assertTrue(session.contains("\"type\":\"tool/result\""));
            String tools = Files.readString(l.sessionDir().resolve("tools.log"));
            assertTrue(tools.contains("ReadFile"));
            assertTrue(tools.contains("done in 12ms"));
        }
    }

    @Test
    void resultIsTruncatedAtMaxChars() throws Exception {
        try (SessionLogger l = new SessionLogger(logging(), "sess-005")) {
            l.onToolResult(ToolResult.ok("x".repeat(500), "call_2"), 1L);
            String tools = Files.readString(l.sessionDir().resolve("tools.log"));
            assertTrue(tools.contains("truncated"));
            String session = Files.readString(l.sessionDir().resolve("session.jsonl"));
            assertTrue(session.contains("truncated"));
        }
    }

    @Test
    void thinkingGoesToThinkingLog() throws Exception {
        try (SessionLogger l = new SessionLogger(logging(), "sess-006")) {
            l.onAssistant(new Message.Assistant("ok", null), List.of("我先定位文件", "再读取"));
            String thinking = Files.readString(l.sessionDir().resolve("thinking.log"));
            assertTrue(thinking.contains("我先定位文件"));
            assertTrue(thinking.contains("再读取"));
        }
    }

    @Test
    void turnEndWritesUsage() throws Exception {
        try (SessionLogger l = new SessionLogger(logging(), "sess-007")) {
            l.onTurnStart(0);
            l.onTurnEnd(new TurnResult("ok", 10, 5, 1));
            String session = Files.readString(l.sessionDir().resolve("session.jsonl"));
            assertTrue(session.contains("\"type\":\"turn/end\""));
            assertTrue(session.contains("\"prompt\":10"));
            assertTrue(session.contains("\"completion\":5"));
        }
    }

    @Test
    void headerVersionIsTwo() throws Exception {
        try (SessionLogger l = new SessionLogger(logging(), "sess-008")) {
            String first = Files.readString(l.sessionDir().resolve("session.jsonl")).split("\n")[0];
            assertTrue(first.contains("\"version\":2"), "session header 应为 version:2");
        }
    }

    @Test
    void contextSnapshotGoesToSessionLog() throws Exception {
        try (SessionLogger l = new SessionLogger(logging(), "sess-009")) {
            l.onContextSnapshot(
                    new ContextSnapshot(
                            0,
                            "system prompt",
                            true,
                            false,
                            java.util.List.of("a.txt"),
                            java.util.List.of("ReadFile"),
                            3,
                            120));
            String session = Files.readString(l.sessionDir().resolve("session.jsonl"));
            assertTrue(session.contains("\"type\":\"context/snapshot\""));
            assertTrue(session.contains("\"turn\":0"));
            assertTrue(session.contains("\"systemPrompt\":\"system prompt\""));
            assertTrue(session.contains("\"toolNames\":[\"ReadFile\"]"));
            assertTrue(session.contains("\"estTokens\":120"));
        }
    }

    @Test
    void systemAndPermissionEventsGoToSessionLog() throws Exception {
        try (SessionLogger l = new SessionLogger(logging(), "sess-010")) {
            l.onSystemEvent(
                    "system/config",
                    java.util.Map.of("provider", "deepseek", "model", "deepseek-chat"));
            l.onSystemEvent(
                    "system/compact", java.util.Map.of("beforeTokens", 5000, "afterTokens", 800));
            l.onPermissionDecision(
                    java.util.Map.of("tool", "Shell", "path", ".", "decision", "ask", "reason", "exec"));
            String session = Files.readString(l.sessionDir().resolve("session.jsonl"));
            assertTrue(session.contains("\"type\":\"system/config\""));
            assertTrue(session.contains("\"provider\":\"deepseek\""));
            assertTrue(session.contains("\"type\":\"system/compact\""));
            assertTrue(session.contains("\"beforeTokens\":5000"));
            assertTrue(session.contains("\"type\":\"permission/decision\""));
            assertTrue(session.contains("\"decision\":\"ask\""));
        }
    }
}
