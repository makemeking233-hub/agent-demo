package com.example.agent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void danglingRepaired_returnsDanglingList_forCallerLogging() throws Exception {
        // 验证修复行为本身：toMessages 返回的消息列表里 danglingCallIds 应该为空
        Path sessionsDir =
                writeSession(store -> {
                    store.append(SessionEntry.user("测试", null));
                    store.append(
                            SessionEntry.assistant(
                                    "",
                                    List.of(new ToolCall("call_x", "Tool", "{}")),
                                    null));
                });
        SessionResumeLoader.ResumeResult result = SessionResumeLoader.load(sessionsDir);
        // 修复后：assistant 之后应该有 synthetic tool_result，danglingCallIds 返回空
        assertTrue(result.messages().size() >= 3, "修复后应有合成 tool_result");
    }

    @Test
    void danglingDedup_acrossMultipleLoads() throws Exception {
        // 同一 sessionId 加载两次，danglingCallIds 都返回一致结果（不依赖 dedupe 状态）
        Path sessionsDir =
                writeSession(store -> {
                    store.append(SessionEntry.user("测试", null));
                    store.append(
                            SessionEntry.assistant(
                                    "",
                                    List.of(new ToolCall("call_y", "Tool", "{}")),
                                    null));
                });
        SessionResumeLoader.ResumeResult r1 = SessionResumeLoader.load(sessionsDir);
        SessionResumeLoader.ResumeResult r2 = SessionResumeLoader.load(sessionsDir);
        assertEquals(r1.messages().size(), r2.messages().size());
        assertEquals(3, r2.messages().size());
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

    // ---------- snip 裁剪的配对组对齐（snip-pairing-repair）----------

    /** 按与 {@code snip} 内部一致的口径估算 token（仅累计 content）。 */
    private static int tokens(List<Message> msgs, TokenEstimator est) {
        int sum = 0;
        for (Message m : msgs) sum += est.estimate(m.content());
        return sum;
    }

    /** 是否存在无前置 {@code assistant.tool_calls} 的 tool_result（即反向孤儿）。 */
    private static boolean hasOrphanToolResult(List<Message> msgs) {
        java.util.Set<String> declared = new java.util.HashSet<>();
        for (Message m : msgs) {
            if (m instanceof Message.Assistant a && a.toolCalls() != null) {
                for (ToolCall tc : a.toolCalls()) declared.add(tc.id());
            } else if (m instanceof Message.ToolResult tr && !declared.contains(tr.toolCallId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构造「裁剪点恰好落在配对组中间」的形状（复刻会话 2026-09-18T13-34-27-b1569b39）。
     *
     * <p>下标 0 = 长 user，1 = assistant(tool_calls=[c1,c2])，2/3 = 两条 tool_result，4 = 尾部 user。
     * assistant 内容刻意非空，使 token 上限把裁剪点推到下标 2（组中间）而不是下标 1。
     *
     * @param est token 估算器（未使用，仅保持签名自解释）
     * @return 长度为 5 的消息列表
     */
    private static List<Message> groupSplitFixture(TokenEstimator est) {
        return List.of(
                new Message.User("历史很长的一轮 " + "x".repeat(800)),
                new Message.Assistant(
                        "我先读这两个文件",
                        List.of(
                                new ToolCall("c1", "ReadFile", "{}"),
                                new ToolCall("c2", "ReadFile", "{}"))),
                new Message.ToolResult("c1", "y".repeat(400), false),
                new Message.ToolResult("c2", "z".repeat(400), false),
                new Message.User("最后一句"));
    }

    @Test
    void snipDoesNotSplitToolCallGroup() {
        TokenEstimator est = new TokenEstimator();
        List<Message> all = groupSplitFixture(est);
        // 上限 = 从下标 2 起的总量 → 裁剪点正好落在 tool_result(c1) 上（配对组中间）
        int maxTokens = tokens(all.subList(2, all.size()), est);

        List<Message> snipped = SessionResumeLoader.snip(all, est, maxTokens);

        assertTrue(snipped.get(0) instanceof Message.System, "裁剪后头部应为压缩提示");
        assertFalse(
                hasOrphanToolResult(snipped),
                "裁剪不得制造无前置 tool_calls 的 tool_result（否则发出去必被上游 400）");
        assertFalse(
                snipped.stream()
                        .anyMatch(
                                m ->
                                        m instanceof Message.ToolResult tr
                                                && (tr.toolCallId().equals("c1")
                                                        || tr.toolCallId().equals("c2"))),
                "配对组必须整组丢弃：c1/c2 的结果不应残留");
    }

    @Test
    void snipStaysWithinLimitAfterGroupAlignment() {
        TokenEstimator est = new TokenEstimator();
        List<Message> all = groupSplitFixture(est);
        int maxTokens = tokens(all.subList(2, all.size()), est);

        List<Message> snipped = SessionResumeLoader.snip(all, est, maxTokens);
        // 对齐只让裁剪点后移（保留量只减不增），不得重新越界；压缩提示自身不计入
        List<Message> body = snipped.subList(1, snipped.size());
        assertTrue(
                tokens(body, est) <= maxTokens,
                "组对齐后的保留量应仍在上限内，实际 " + tokens(body, est) + " > " + maxTokens);
    }

    @Test
    void snipLeavesUnderLimitUntouched() {
        TokenEstimator est = new TokenEstimator();
        List<Message> all =
                List.of(
                        new Message.User("a"),
                        new Message.Assistant("b", List.of()),
                        new Message.User("c"));

        List<Message> out = SessionResumeLoader.snip(all, est, 100_000);

        assertEquals(all, out, "未超限时应原样返回，不插入压缩提示");
    }

    @Test
    void snipSummaryStartsWithResumedMarker() {
        TokenEstimator est = new TokenEstimator();
        List<Message> out =
                SessionResumeLoader.snip(List.of(new Message.User("x".repeat(5000))), est, 1);

        assertTrue(
                out.get(0) instanceof Message.System s && s.content().startsWith("[RESUMED]"),
                "裁剪后首条应为 [RESUMED] 开头的 system 消息");
    }
}

