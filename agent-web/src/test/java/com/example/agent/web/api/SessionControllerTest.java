package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.session.SessionEntry;
import com.example.agent.session.SessionStore;
import com.example.agent.session.WorkspaceStore;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.web.api.dto.RenameRequest;
import com.example.agent.web.api.dto.SessionMessagesResponse;
import com.example.agent.web.stream.WebAgentRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** SessionController 会话重进恢复端点（直接调用控制器方法，遵循 HealthControllerTest 惯例）。 */
class SessionControllerTest {

    @TempDir Path tmp;

    private SessionController controller;
    private WebAgentRuntime rt;

    @BeforeEach
    void setUp() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.name()).thenReturn("deepseek");
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        rt = new WebAgentRuntime(
                provider, new ToolRegistry(), new TokenEstimator(), tmp, cfgNoLogging());
        controller = new SessionController(rt);
    }

    private static AgentConfig cfgNoLogging() {
        AgentConfig def = AgentConfig.defaults();
        return new AgentConfig(
                def.provider(),
                def.permission(),
                def.cost(),
                def.context(),
                def.shell(),
                def.memoryInject(),
                new AgentConfig.Logging(false, def.logging().dir(), 1_000, 1_000, 30, 50),
                def.memory(),
                def.mcp(),
                def.worktree(),
                def.plugins(),
                def.search(),
                def.voice());
    }

    private void writeSession(String sessionId, SessionEntry... entries) throws Exception {
        Path sessionsDir = tmp.resolve("sessions");
        Files.createDirectories(sessionsDir);
        SessionStore store = new SessionStore(sessionsDir.resolve(sessionId + ".jsonl"), 50, 60_000);
        for (SessionEntry e : entries) store.append(e);
        store.syncFlush();
        store.close();
    }

    @Test
    void messagesReturnsTranscriptForKnownSession() throws Exception {
        writeSession("s-1", SessionEntry.user("你好", null), SessionEntry.assistant("你好！", java.util.List.of(), null));

        ResponseEntity<SessionMessagesResponse> resp = controller.messages("s-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        SessionMessagesResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.sessionId()).isEqualTo("s-1");
        assertThat(body.messages()).hasSize(2);
        assertThat(body.messages().get(0).role()).isEqualTo("user");
        assertThat(body.messages().get(0).content()).isEqualTo("你好");
        assertThat(body.messages().get(1).role()).isEqualTo("assistant");
    }

    @Test
    void messagesUnknownSessionReturns404() {
        ResponseEntity<SessionMessagesResponse> resp = controller.messages("nope");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- add-message-actions P2：历史回填 per-message 读数 ----------

    /** 存档里的 {@code message_meta} 条目按 assistant 序号贴回消息 → 刷新后 clock 仍在。 */
    @Test
    void messagesAttachPerMessageMetaToAssistant() throws Exception {
        SessionEntry assistant = SessionEntry.assistant("你好！", java.util.List.of(), null);
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("uuid", assistant.uuid());
        meta.put("duration_ms", 15000L);
        meta.put("ttft_ms", 1200.0);
        meta.put("tok_per_sec", 34.0);
        meta.put("timestamp", 1736700000000L);
        writeSession(
                "s-p2",
                SessionEntry.user("你好", null),
                assistant,
                SessionEntry.meta("tokens", java.util.List.of(10, 20)),
                SessionEntry.meta("message_meta", meta));

        ResponseEntity<SessionMessagesResponse> resp = controller.messages("s-p2");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        SessionMessagesResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.messages()).hasSize(2);
        // user 消息没有读数
        assertThat(body.messages().get(0).meta()).isNull();
        assertThat(body.messages().get(0).uuid()).isNull();
        // assistant 消息带上 uuid + 读数
        assertThat(body.messages().get(1).uuid()).isEqualTo(assistant.uuid());
        assertThat(body.messages().get(1).meta()).isNotNull();
        assertThat(body.messages().get(1).meta()).containsEntry("duration_ms", 15000);
        assertThat(body.messages().get(1).meta()).containsEntry("ttft_ms", 1200.0);
        assertThat(body.messages().get(1).meta()).containsEntry("tok_per_sec", 34.0);
    }

    /** 读数指向的 uuid 不在存档里（或被裁剪）→ 整体放弃，不误贴到别的消息上。 */
    @Test
    void messagesIgnoreMetaWhenUuidUnknown() throws Exception {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("uuid", "不存在的-uuid");
        meta.put("duration_ms", 15000L);
        writeSession(
                "s-p2-orphan",
                SessionEntry.user("你好", null),
                SessionEntry.assistant("你好！", java.util.List.of(), null),
                SessionEntry.meta("message_meta", meta));

        ResponseEntity<SessionMessagesResponse> resp = controller.messages("s-p2-orphan");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        SessionMessagesResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.messages().get(1).meta()).isNull();
        assertThat(body.messages().get(1).uuid()).isNull();
    }

    /** 老会话（没有 message_meta 条目）依旧正常返回，只是没有读数。 */
    @Test
    void messagesWithoutMessageMetaStillWork() throws Exception {
        writeSession("s-p2-old", SessionEntry.user("旧", null), SessionEntry.assistant("旧答", java.util.List.of(), null));

        ResponseEntity<SessionMessagesResponse> resp = controller.messages("s-p2-old");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().messages()).hasSize(2);
        assertThat(resp.getBody().messages().get(1).meta()).isNull();
    }

    @Test
    void currentReturnsNullSession() {
        ResponseEntity<Map<String, Object>> resp = controller.current();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("session_id", (Object) null);
    }

    @Test
    void listReturnsSessions() throws Exception {
        writeSession("s-1", SessionEntry.user("你好", null));
        writeSession("s-2", SessionEntry.user("世界", null));

        ResponseEntity<java.util.List<com.example.agent.web.api.dto.SessionSummaryDto>> resp =
                controller.list(false, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().stream().map(com.example.agent.web.api.dto.SessionSummaryDto::id))
                .contains("s-1", "s-2");
        // title 取首条 user 消息首行
        assertThat(resp.getBody().stream().filter(s -> s.id().equals("s-1")).findFirst().get().title())
                .isEqualTo("你好");
    }

    @Test
    void listEmptyWhenNoSessions() {
        ResponseEntity<java.util.List<com.example.agent.web.api.dto.SessionSummaryDto>> resp =
                controller.list(false, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    void runtimeHasSessionReflectsDisk() throws Exception {
        assertThat(rt.hasSession("nope")).isFalse();
        writeSession("s-x", SessionEntry.user("hi", null));
        assertThat(rt.hasSession("s-x")).isTrue();
    }

    @Test
    void archiveMovesFileAndExcludesFromList() throws Exception {
        writeSession("s-arch", SessionEntry.user("要被删", null));

        ResponseEntity<Map<String, Object>> resp = controller.archive("s-arch");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.exists(tmp.resolve("sessions").resolve(".archive").resolve("s-arch.jsonl"))).isTrue();
        // 默认列表不再含它，归档列表含它
        assertThat(controller.list(false, null).getBody().stream().map(com.example.agent.web.api.dto.SessionSummaryDto::id))
                .doesNotContain("s-arch");
        assertThat(controller.list(true, null).getBody().stream().map(com.example.agent.web.api.dto.SessionSummaryDto::id))
                .contains("s-arch");
    }

    @Test
    void archivedListCarriesTimeBucket() throws Exception {
        // auto-archive-stale-sessions：归档列表项带时间分档，供前端分组展示。
        writeSession("s-recent", SessionEntry.user("刚聊完就手动归档", null));
        controller.archive("s-recent");
        // 把归档文件的 mtime 推到 10 天前 → 应落在「上周」档
        Files.setLastModifiedTime(
                tmp.resolve("sessions").resolve(".archive").resolve("s-recent.jsonl"),
                java.nio.file.attribute.FileTime.fromMillis(
                        System.currentTimeMillis() - java.time.Duration.ofDays(10).toMillis()));

        var archived = controller.list(true, null).getBody();
        assertThat(archived).isNotNull();
        var item =
                archived.stream()
                        .filter(s -> "s-recent".equals(s.id()))
                        .findFirst()
                        .orElseThrow();
        assertThat(item.bucket()).isEqualTo("last_week");

        // 普通列表不需要分档字段
        var live = controller.list(false, null).getBody();
        assertThat(live).isNotNull();
        assertThat(live).allSatisfy(s -> assertThat(s.bucket()).isNull());
    }

    @Test
    void archiveUnknownReturns404() {
        assertThat(controller.archive("nope").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void restoreReturns200AndMovesBack() throws Exception {
        writeSession("s-res", SessionEntry.user("恢复我", null));
        controller.archive("s-res");

        ResponseEntity<Map<String, Object>> resp = controller.restore("s-res");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.exists(tmp.resolve("sessions").resolve("s-res.jsonl"))).isTrue();
        assertThat(controller.list(false, null).getBody().stream().map(com.example.agent.web.api.dto.SessionSummaryDto::id))
                .contains("s-res");
    }

    @Test
    void restoreUnknownReturns404() {
        assertThat(controller.restore("nope").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void summaryTimeIsNonNegativeLong() throws Exception {
        writeSession("s-t", SessionEntry.user("时间", null));
        var body = controller.list(false, null).getBody();
        assertThat(body).isNotNull();
        assertThat(body.get(0).time()).isGreaterThanOrEqualTo(0);
    }

    // ---- add-workspaces-and-rename：重命名 + 工作区过滤 ----

    @Test
    void renameSetsCustomTitleOverDerived() throws Exception {
        writeSession("s-r", SessionEntry.user("自动标题", null));

        ResponseEntity<Map<String, Object>> resp = controller.rename("s-r", new RenameRequest("我的项目"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = controller.list(false, null).getBody();
        assertThat(body.stream().filter(s -> s.id().equals("s-r")).findFirst().get().title())
                .isEqualTo("我的项目");
    }

    @Test
    void renameUnknownReturns404() {
        assertThat(controller.rename("nope", new RenameRequest("x")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void renameEmptyTitleReturns400() throws Exception {
        writeSession("s-r", SessionEntry.user("hi", null));
        assertThat(controller.rename("s-r", new RenameRequest("   ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listFiltersByWorkspace() throws Exception {
        Path workDir = tmp.resolve("md-main");
        Files.createDirectories(workDir);
        WorkspaceStore.create(tmp, "md-main", workDir.toString());
        Path wsSessions = tmp.resolve("workspaces/md-main/sessions");
        Files.createDirectories(wsSessions);
        SessionStore ws = new SessionStore(wsSessions.resolve("s-ws.jsonl"), 50, 60_000);
        ws.append(SessionEntry.user("ws 会话", null));
        ws.syncFlush();
        ws.close();
        writeSession("s-def", SessionEntry.user("默认会话", null));

        var defList = controller.list(false, null).getBody();
        assertThat(defList.stream().map(com.example.agent.web.api.dto.SessionSummaryDto::id))
                .contains("s-def").doesNotContain("s-ws");
        var wsList = controller.list(false, "md-main").getBody();
        assertThat(wsList.stream().map(com.example.agent.web.api.dto.SessionSummaryDto::id))
                .contains("s-ws").doesNotContain("s-def");
        assertThat(wsList.get(0).workspace()).isEqualTo("md-main");
    }

    // ---- add-session-stats-bar：统计端点 ----

    @Test
    void statsReturnsZerosForKnownSessionWithoutData() throws Exception {
        writeSession("s-st", SessionEntry.user("hi", null));

        ResponseEntity<?> resp = controller.stats("s-st", null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var dto = (com.example.agent.web.api.dto.SessionStatsDto) resp.getBody();
        assertThat(dto.turns()).isZero();
        assertThat(dto.avgTtftMs()).isNull();
        assertThat(dto.cacheHitRate()).isNull();
    }

    @Test
    void statsReflectsAccumulatedValues() throws Exception {
        writeSession("s-st2", SessionEntry.user("hi", null));
        rt.accumulateStats(
                null,
                "s-st2",
                new com.example.agent.stats.TurnDelta(2, 100, 50, 0, 2000, 300, 500, 1, 80, 20));

        ResponseEntity<?> resp = controller.stats("s-st2", null);

        var dto = (com.example.agent.web.api.dto.SessionStatsDto) resp.getBody();
        assertThat(dto.turns()).isEqualTo(1);
        assertThat(dto.steps()).isEqualTo(2);
        assertThat(dto.tokensIn()).isEqualTo(100);
        assertThat(dto.cacheHitRate()).isEqualTo(0.8);
    }

    @Test
    void statsUnknownSessionReturns404() {
        assertThat(controller.stats("nope", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

