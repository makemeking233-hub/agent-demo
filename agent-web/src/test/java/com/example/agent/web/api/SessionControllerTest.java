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
                def.search());
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
}
