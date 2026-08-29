package com.example.agent.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.Message;
import com.example.agent.core.MessageHistory;
import com.example.agent.llm.ToolCall;
import com.example.agent.tools.ToolResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

/**
 * SessionReplay 测试：session.jsonl 事件流 → MessageHistory 重建。
 */
class SessionReplayTest {
    @TempDir Path tmp;

    private Path writeOneRound(boolean withToolCall) throws Exception {
        AgentConfig.Logging logging = new AgentConfig.Logging(true, tmp.toString(), 100, 2000, 30, 50);
        try (SessionLogger l = new SessionLogger(logging, "replay-001")) {
            l.onTurnStart(0);
            l.onUser(new Message.User("你好"));
            if (withToolCall) {
                l.onAssistant(
                        new Message.Assistant("", List.of(new ToolCall("call_1", "ReadFile", "{\"path\":\"/tmp/a.txt\"}"))),
                        List.of());
                l.onToolResult(ToolResult.ok("文件内容 abc", "call_1"), 5L);
            }
            l.onAssistant(new Message.Assistant("你好！有什么可以帮你？", null), List.of());
            l.onTurnEnd(new com.example.agent.core.TurnResult("ok", 10, 5, 1));
        }
        return tmp.resolve("sessions").resolve("replay-001").resolve("session.jsonl");
    }

    @Test
    void singleTurnReconstructsMessages() throws Exception {
        Path file = writeOneRound(false);
        MessageHistory hist = SessionReplay.replay(file);
        List<Message> msgs = hist.all();

        assertEquals(2, msgs.size(), "user + assistant");
        assertTrue(msgs.get(0) instanceof Message.User);
        assertEquals("你好", msgs.get(0).content());
        assertTrue(msgs.get(1) instanceof Message.Assistant);
        assertEquals("你好！有什么可以帮你？", msgs.get(1).content());
    }

    @Test
    void toolCallRoundReconstructsOrderAndToolCalls() throws Exception {
        Path file = writeOneRound(true);
        MessageHistory hist = SessionReplay.replay(file);
        List<Message> msgs = hist.all();

        // user → assistant(toolCalls) → tool → assistant
        assertEquals(4, msgs.size());
        assertTrue(msgs.get(1) instanceof Message.Assistant a1 && a1.toolCalls() != null);
        Message.Assistant a1 = (Message.Assistant) msgs.get(1);
        assertEquals(1, a1.toolCalls().size());
        assertEquals("ReadFile", a1.toolCalls().get(0).name());
        assertTrue(msgs.get(2) instanceof Message.ToolResult);
        Message.ToolResult tr = (Message.ToolResult) msgs.get(2);
        assertEquals("call_1", tr.toolCallId());
        assertEquals(true, tr.isError() == false);
    }

    @Test
    void unknownEventTypesAreSkipped() throws Exception {
        // 手工构造含未知 type 的事件流
        Path p = tmp.resolve("session.jsonl");
        java.nio.file.Files.writeString(
                p,
                "{\"seq\":0,\"type\":\"session\",\"version\":2}\n"
                        + "{\"seq\":1,\"type\":\"future/event\",\"x\":1}\n"
                        + "{\"seq\":2,\"type\":\"user/message\",\"content\":\"hi\"}\n"
                        + "{\"seq\":3,\"type\":\"assistant/message\",\"content\":\"ok\"}\n");
        MessageHistory hist = SessionReplay.replay(p);
        assertEquals(2, hist.all().size(), "未知事件应被跳过");
        assertEquals("hi", hist.all().get(0).content());
    }
}
