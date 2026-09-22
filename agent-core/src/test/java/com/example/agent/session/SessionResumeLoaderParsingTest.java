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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SessionResumeLoader} 存档解析分支覆盖（fix-jacoco-rule）。
 *
 * <p>该类 BRANCH 此前 0.667。未覆盖分支集中在 {@code toMessagesRaw} 的类型分发与三个私有
 * 取值辅助（{@code str} / {@code intOf} / {@code boolVal}）的容错路径——它们只在存档字段「类型
 * 不符预期」时才走到，正常存档跑不出来。本类用**手工构造的 {@link SessionEntry}**（extras 字段故意
 * 放错类型/缺失/非法值）把这些容错分支逐条跑到。
 *
 * <p>全部用例在内存中构造，不写真实数据目录（全局规则 §10）。
 */
class SessionResumeLoaderParsingTest {

    @TempDir Path tmp;

    private static SessionEntry entry(String type, String content, Map<String, Object> extras) {
        return new SessionEntry(type, "uuid", null, content, extras, 1L);
    }

    // ---------- toMessagesRaw：类型分发 ----------

    @Test
    void emptyEntriesYieldEmptyResult() {
        SessionResumeLoader.ResumeResult r = SessionResumeLoader.toMessagesRaw(List.of());
        assertTrue(r.messages().isEmpty());
        assertEquals(0, r.promptTokens());
    }

    @Test
    void systemEntryIsRestored() {
        var r =
                SessionResumeLoader.toMessagesRaw(
                        List.of(entry("system", "[COMPACTED] 摘要", null)));
        assertEquals(1, r.messages().size());
        assertTrue(r.messages().get(0) instanceof Message.System);
    }

    @Test
    void unknownTypeIsSkipped() {
        var r =
                SessionResumeLoader.toMessagesRaw(
                        List.of(entry("bogus", "x", null), entry("user", "real", null)));
        // 未知类型被跳过，只剩 user
        assertEquals(1, r.messages().size());
        assertTrue(r.messages().get(0) instanceof Message.User);
    }

    @Test
    void nullExtrasIsTreatedAsEmptyMap() {
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("assistant", "plain", null)));
        Message.Assistant a = (Message.Assistant) r.messages().get(0);
        // extras 为 null → toolCalls 解析出空列表
        assertTrue(a.toolCalls().isEmpty());
    }

    // ---------- 私有辅助 str / intOf / boolVal 的容错分支 ----------

    @Test
    void metaValueAsNumericStringIsParsed() {
        Map<String, Object> ex = new HashMap<>();
        ex.put("key", "prompt");
        ex.put("value", "42"); // String 而非 Number → 走 parseInt 路径
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("meta", null, ex)));
        assertEquals(42, r.promptTokens());
    }

    @Test
    void metaValueNonNumericStringFallsBackToZero() {
        Map<String, Object> ex = new HashMap<>();
        ex.put("key", "completion");
        ex.put("value", "not-a-number"); // 触发 NumberFormatException 分支
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("meta", null, ex)));
        assertEquals(0, r.completionTokens());
    }

    @Test
    void metaValueNullFallsBackToZero() {
        Map<String, Object> ex = new HashMap<>();
        ex.put("key", "prompt");
        ex.put("value", null); // 覆盖 intOf 的 v != null 假分支
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("meta", null, ex)));
        assertEquals(0, r.promptTokens());
    }

    @Test
    void metaWithUnknownKeyIsIgnored() {
        Map<String, Object> ex = new HashMap<>();
        ex.put("key", "something-else");
        ex.put("value", 7);
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("meta", null, ex)));
        assertEquals(0, r.promptTokens());
        assertEquals(0, r.completionTokens());
        assertTrue(r.messages().isEmpty());
    }

    @Test
    void metaValueAsNumberIsParsed() {
        var r =
                SessionResumeLoader.toMessagesRaw(
                        List.of(entry("meta", null, Map.of("key", "prompt", "value", 10))));
        assertEquals(10, r.promptTokens());
    }

    @Test
    void toolResultNonBooleanIsErrorIsParsedFromString() {
        Map<String, Object> ex = new HashMap<>();
        ex.put("toolCallId", "c1");
        ex.put("isError", "true"); // String 而非 Boolean → boolVal 的非 Boolean 路径
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("tool_result", "out", ex)));
        Message.ToolResult tr = (Message.ToolResult) r.messages().get(0);
        assertTrue(tr.isError());
    }

    @Test
    void toolResultNonBooleanIsErrorFalseIsParsedFromString() {
        Map<String, Object> ex = new HashMap<>();
        ex.put("toolCallId", "c1");
        ex.put("isError", "false");
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("tool_result", "ok", ex)));
        assertFalse(((Message.ToolResult) r.messages().get(0)).isError());
    }

    @Test
    void toolResultNullFieldsBecomeEmptyStringsAndFalse() {
        Map<String, Object> ex = new HashMap<>();
        ex.put("toolCallId", null); // 覆盖 str 的 null 分支
        ex.put("isError", null); // 覆盖 boolVal 的 v != null 假分支
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("tool_result", "c", ex)));
        Message.ToolResult tr = (Message.ToolResult) r.messages().get(0);
        assertEquals("", tr.toolCallId());
        assertFalse(tr.isError());
    }

    @Test
    void assistantToolCallsThatIsNotAListYieldsEmpty() {
        Map<String, Object> ex = new HashMap<>();
        ex.put("toolCalls", "not-a-list"); // 覆盖 parseToolCalls 的类型不符分支
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("assistant", "a", ex)));
        assertTrue(((Message.Assistant) r.messages().get(0)).toolCalls().isEmpty());
    }

    @Test
    void assistantToolCallEntriesThatAreNotMapsAreSkipped() {
        List<Object> raw = new ArrayList<>();
        raw.add("garbage"); // 非 Map → continue 分支
        raw.add(Map.of("id", "c1", "name", "ReadFile", "argumentsJson", "{}"));
        Map<String, Object> ex = new HashMap<>();
        ex.put("toolCalls", raw);
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("assistant", "a", ex)));
        List<ToolCall> calls = ((Message.Assistant) r.messages().get(0)).toolCalls();
        assertEquals(1, calls.size());
        assertEquals("c1", calls.get(0).id());
    }

    @Test
    void assistantToolCallNullFieldsBecomeEmptyStrings() {
        List<Object> raw = new ArrayList<>();
        Map<String, Object> tc = new HashMap<>();
        tc.put("id", null);
        tc.put("name", null);
        tc.put("argumentsJson", null);
        raw.add(tc);
        Map<String, Object> ex = new HashMap<>();
        ex.put("toolCalls", raw);
        var r = SessionResumeLoader.toMessagesRaw(List.of(entry("assistant", "a", ex)));
        ToolCall c = ((Message.Assistant) r.messages().get(0)).toolCalls().get(0);
        assertEquals("", c.id());
        assertEquals("", c.name());
        assertEquals("", c.argumentsJson());
    }

    // ---------- loadArchivedById ----------

    @Test
    void loadArchivedByIdReadsArchiveDirAndHandlesMissing() throws Exception {
        Path sessionsDir = tmp.resolve("sessions-arch");
        Path archiveDir = sessionsDir.resolve(".archive");
        Files.createDirectories(archiveDir);

        // 缺档 → 空结果
        assertTrue(SessionResumeLoader.loadArchivedById(sessionsDir, "nope").messages().isEmpty());

        // 写一份归档会话文件（写临时目录，不碰真实数据目录）
        SessionStore store = new SessionStore(archiveDir.resolve("a1.jsonl"), 50, 60_000);
        store.append(SessionEntry.user("归档会话", null));
        store.append(SessionEntry.assistant("回复", List.of(), null));
        store.syncFlush();
        store.close();

        var r = SessionResumeLoader.loadArchivedById(sessionsDir, "a1");
        assertEquals(2, r.messages().size());
        assertEquals("归档会话", r.messages().get(0).content());
    }

    // ---------- snip 的边界分支 ----------

    @Test
    void snipWithNonPositiveLimitReturnsMessagesUnchanged() {
        TokenEstimator est = new TokenEstimator();
        List<Message> msgs =
                List.of(new Message.User("x".repeat(5000)), new Message.Assistant("a", List.of()));

        // maxTokens <= 0 → 原样返回（不裁剪、不加 summary）
        assertEquals(msgs, SessionResumeLoader.snip(msgs, est, 0));
        assertEquals(msgs, SessionResumeLoader.snip(msgs, est, -1));
    }

    @Test
    void snipWithEmptyListReturnsEmpty() {
        assertEquals(List.of(), SessionResumeLoader.snip(List.of(), new TokenEstimator(), 100));
    }
}
