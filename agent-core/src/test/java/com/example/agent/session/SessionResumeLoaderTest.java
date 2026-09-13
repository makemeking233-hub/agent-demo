package com.example.agent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.core.Message;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.llm.ToolCall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class SessionResumeLoaderTest {
    @TempDir Path tmp;

    private Path writeSession(java.util.function.Consumer<SessionStore> fill) throws Exception {
        Path sessionsDir = tmp.resolve("sessions-" + System.nanoTime());
        Files.createDirectories(sessionsDir);
        Path file = sessionsDir.resolve("s.jsonl");
        SessionStore store = new SessionStore(file, 50, 60_000);
        fill.accept(store);
        store.syncFlush();
        store.close();
        return sessionsDir;
    }

    @Test
    void restoresToolCallsAndToken() throws Exception {
        Path sessionsDir =
                writeSession(store -> {
                    store.append(SessionEntry.user("读一下文件", null));
                    store.append(
                            SessionEntry.assistant(
                                    "我用工具",
                                    List.of(new ToolCall("call_1", "ReadFile", "{\"path\":\"a.txt\"}")),
                                    null));
                    store.append(SessionEntry.toolResult("call_1", "文件内容", false, null));
                    store.append(SessionEntry.meta("prompt", 10));
                    store.append(SessionEntry.meta("completion", 5));
                });

        SessionResumeLoader.ResumeResult result = SessionResumeLoader.load(sessionsDir);
        assertEquals(3, result.messages().size()); // user + assistant + tool_result（meta 不进 messages）
        // assistant 恢复 toolCalls
        Message.Assistant assistant = (Message.Assistant) result.messages().get(1);
        assertEquals(1, assistant.toolCalls().size());
        assertEquals("call_1", assistant.toolCalls().get(0).id());
        assertEquals("ReadFile", assistant.toolCalls().get(0).name());
        // tool_result 恢复 callId + isError
        Message.ToolResult tool = (Message.ToolResult) result.messages().get(2);
        assertEquals("call_1", tool.toolCallId());
        // token 恢复
        assertEquals(10, result.promptTokens());
        assertEquals(5, result.completionTokens());
    }

    @Test
    void repairsDanglingToolCallsFromInterruptedTurn() throws Exception {
        // 真实事故形状（会话 1789277081599）：assistant(EditFile) 之后直接是下一条 user。
        // 工具抛 NoClassDefFoundError 打断整轮，tool_result 从未落盘；不修复的话该会话
        // 每轮重放历史都会被上游 400（insufficient tool messages following tool_calls）。
        Path sessionsDir =
                writeSession(store -> {
                    store.append(SessionEntry.user("存入长期记忆吧", null));
                    store.append(
                            SessionEntry.assistant(
                                    "",
                                    List.of(
                                            new ToolCall(
                                                    "call_edit",
                                                    "EditFile",
                                                    "{\"path\":\"MEMORY.md\"}")),
                                    null));
                    store.append(SessionEntry.user("怎么还是没有流式输出", null));
                });

        SessionResumeLoader.ResumeResult result = SessionResumeLoader.load(sessionsDir);
        List<Message> msgs = result.messages();

        assertEquals(4, msgs.size()); // user + assistant + 合成 tool_result + user
        Message.ToolResult injected = (Message.ToolResult) msgs.get(2);
        assertEquals("call_edit", injected.toolCallId());
        assertTrue(injected.isError(), "合成结果必须标记为错误（不伪装成功）");
        assertTrue(
                msgs.get(3) instanceof Message.User,
                "补的结果必须插在 assistant 与下一条 user 之间，否则配对仍被打破");
    }

    @Test
    void injectsOrphanSkeletonForOrphanToolResult() throws Exception {
        Path sessionsDir =
                writeSession(store -> {
                    store.append(SessionEntry.user("执行", null));
                    // tool_result 无前置 assistant.tool_calls（孤儿）
                    store.append(SessionEntry.toolResult("orphan_1", "结果", false, null));
                });

        SessionResumeLoader.ResumeResult result = SessionResumeLoader.load(sessionsDir);
        // 注入合成 assistant 骨架 → 至少 3 条消息（user + 合成 assistant + tool_result）
        assertTrue(result.messages().size() >= 3, "应注入合成 assistant 骨架");
        Message.Assistant synth = (Message.Assistant) result.messages().get(1);
        assertEquals("orphan_1", synth.toolCalls().get(0).id());
        assertTrue(synth.toolCalls().get(0).name().equals("resumed_tool"));
    }

    @Test
    void snipCapsOversizedHistory() throws Exception {
        // 构造许多消息，使 token 总量超上限
        SessionResumeLoader.ResumeResult result =
                new SessionResumeLoader.ResumeResult(
                        List.of(new Message.User("x".repeat(5000))), 0, 0);
        TokenEstimator estimator = new TokenEstimator();
        List<Message> snipped =
                SessionResumeLoader.snip(result.messages(), estimator, 1);
        assertTrue(snipped.size() >= 1);
        assertTrue(snipped.get(0) instanceof Message.System, "裁剪后头部应为 summary 系统消息");
    }

    @Test
    void noSessionReturnsEmpty() {
        SessionResumeLoader.ResumeResult result =
                SessionResumeLoader.load(tmp.resolve("no-such-dir"));
        assertTrue(result.messages().isEmpty());
        assertEquals(0, result.promptTokens());
    }

    @Test
    void loadById_restoresNamedSession() throws Exception {
        Path sessionsDir = tmp.resolve("sessions-" + System.nanoTime());
        Files.createDirectories(sessionsDir);
        Path file = sessionsDir.resolve("s-9.jsonl");
        SessionStore store = new SessionStore(file, 50, 60_000);
        store.append(SessionEntry.user("你好", null));
        store.append(SessionEntry.assistant("你好！", List.of(), null));
        store.syncFlush();
        store.close();

        SessionResumeLoader.ResumeResult result = SessionResumeLoader.loadById(sessionsDir, "s-9");
        assertEquals(2, result.messages().size());
        assertEquals("你好", result.messages().get(0).content());
        assertEquals("assistant", result.messages().get(1).role());
    }

    @Test
    void loadById_missingSession_returnsEmpty() throws Exception {
        Path sessionsDir = tmp.resolve("sessions-" + System.nanoTime());
        Files.createDirectories(sessionsDir);

        SessionResumeLoader.ResumeResult result = SessionResumeLoader.loadById(sessionsDir, "nope");
        assertTrue(result.messages().isEmpty());
        assertEquals(0, result.promptTokens());
    }
}

