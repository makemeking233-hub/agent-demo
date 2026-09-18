package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.session.SessionEntry;
import com.example.agent.session.SessionStore;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.web.api.dto.CreateWorkspaceRequest;
import com.example.agent.web.api.dto.WorkspaceDto;
import com.example.agent.web.stream.WebAgentRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** WorkspaceController v2（align-dsh-workspace）：create 改单参 path + DELETE / PATCH / PUT order 三个新端点。 */
class WorkspaceControllerTest {

    @TempDir Path tmp;

    private WorkspaceController controller;

    @BeforeEach
    void setUp() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.name()).thenReturn("deepseek");
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        WebAgentRuntime rt =
                new WebAgentRuntime(provider, new ToolRegistry(), new TokenEstimator(), tmp, cfgNoLogging());
        controller = new WorkspaceController(rt);
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

    @Test
    void listIncludesDefaultAndCreated() throws Exception {
        Path workDir = tmp.resolve("md-main");
        Files.createDirectories(workDir);
        controller.create(new CreateWorkspaceRequest(workDir.toString()));

        ResponseEntity<List<WorkspaceDto>> resp = controller.list();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().stream().map(WorkspaceDto::name))
                .contains("agent-demo", "md-main");
        // v2 字段：id / title / status
        WorkspaceDto md = resp.getBody().stream()
                .filter(w -> "md-main".equals(w.name())).findFirst().get();
        assertThat(md.id()).isNotBlank();
        assertThat(md.title()).isEqualTo("md-main");
        assertThat(md.status()).isEqualTo("ok");
    }

    @Test
    void createReturns200AndPersists() throws Exception {
        Path workDir = tmp.resolve("ws-1");
        Files.createDirectories(workDir);
        ResponseEntity<?> resp = controller.create(new CreateWorkspaceRequest(workDir.toString()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(com.example.agent.session.WorkspaceStore.exists(tmp, "ws-1")).isTrue();
    }

    @Test
    void createDerivesNameFromBasename() throws Exception {
        Path workDir = tmp.resolve("project-md-main");
        Files.createDirectories(workDir);
        Map<String, Object> body = (Map<String, Object>) controller.create(
                new CreateWorkspaceRequest(workDir.toString())).getBody();
        // v2：name 自动派生 basename，不传 name
        assertThat(body.get("name")).isEqualTo("project-md-main");
        assertThat(body.get("title")).isEqualTo("project-md-main");
    }

    @Test
    void createDirNotFoundReturns400() {
        ResponseEntity<?> resp = controller.create(
                new CreateWorkspaceRequest(tmp.resolve("nope").toString()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createEmptyPathReturns400() {
        ResponseEntity<?> resp = controller.create(new CreateWorkspaceRequest(""));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSameCanonicalPathReturnsExisting() throws Exception {
        Path workDir = tmp.resolve("project-md-main");
        Files.createDirectories(workDir);
        Map<String, Object> first = (Map<String, Object>) controller.create(
                new CreateWorkspaceRequest(workDir.toString())).getBody();
        // 第二次同 path：复用 record（DSH 语义）
        Map<String, Object> second = (Map<String, Object>) controller.create(
                new CreateWorkspaceRequest(workDir.toString())).getBody();
        assertThat(second.get("id")).isEqualTo(first.get("id"));
    }

    @Test
    void sessionCountReflectsWorkspaceSessions() throws Exception {
        Path workDir = tmp.resolve("md-main");
        Files.createDirectories(workDir);
        controller.create(new CreateWorkspaceRequest(workDir.toString()));
        Path wsSessions = tmp.resolve("workspaces/md-main/sessions");
        Files.createDirectories(wsSessions);
        SessionStore store = new SessionStore(wsSessions.resolve("s-1.jsonl"), 50, 60_000);
        store.append(SessionEntry.user("hi", null));
        store.syncFlush();
        store.close();

        ResponseEntity<List<WorkspaceDto>> resp = controller.list();
        WorkspaceDto md = resp.getBody().stream()
                .filter(w -> w.name().equals("md-main")).findFirst().get();
        assertThat(md.sessionCount()).isEqualTo(1);
    }

    // ---------- DELETE ----------

    @Test
    void deleteRemovesRecordButPreservesDir() throws Exception {
        Path workDir = tmp.resolve("ws-delete");
        Files.createDirectories(workDir);
        Files.writeString(workDir.resolve("file.txt"), "preserve me");
        controller.create(new CreateWorkspaceRequest(workDir.toString()));

        ResponseEntity<?> resp = controller.delete("ws-delete");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // record 消失
        assertThat(com.example.agent.session.WorkspaceStore.get(tmp, "ws-delete")).isNull();
        // dir 完整
        assertThat(Files.isDirectory(workDir)).isTrue();
        assertThat(Files.exists(workDir.resolve("file.txt"))).isTrue();
    }

    @Test
    void deleteUnknownReturns404() {
        ResponseEntity<?> resp = controller.delete("nope");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteDefaultWorkspaceReturns404() {
        // 默认 workspace 不可删
        ResponseEntity<?> resp = controller.delete("agent-demo");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- PATCH rename ----------

    @Test
    void renameChangesTitle() throws Exception {
        Path workDir = tmp.resolve("ws-rename");
        Files.createDirectories(workDir);
        controller.create(new CreateWorkspaceRequest(workDir.toString()));

        var body = Map.<String, String>of("title", "New Pretty Title");
        ResponseEntity<?> resp = controller.rename("ws-rename", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var ws = com.example.agent.session.WorkspaceStore.get(tmp, "ws-rename");
        assertThat(ws.title()).isEqualTo("New Pretty Title");
        assertThat(ws.name()).isEqualTo("ws-rename");
    }

    @Test
    void renameBlankTitleReturns400() throws Exception {
        Path workDir = tmp.resolve("ws-rename-blank");
        Files.createDirectories(workDir);
        controller.create(new CreateWorkspaceRequest(workDir.toString()));

        var body = Map.<String, String>of("title", "   ");
        ResponseEntity<?> resp = controller.rename("ws-rename-blank", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void renameUnknownReturns404() {
        var body = Map.<String, String>of("title", "X");
        ResponseEntity<?> resp = controller.rename("nope", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- PUT order ----------

    @Test
    void updateOrderPersistsDurableOrder() throws Exception {
        Path a = tmp.resolve("a"); Files.createDirectories(a);
        Path b = tmp.resolve("b"); Files.createDirectories(b);
        controller.create(new CreateWorkspaceRequest(a.toString()));
        controller.create(new CreateWorkspaceRequest(b.toString()));

        var body = Map.<String, List<String>>of("order", List.of("b", "a"));
        ResponseEntity<?> resp = controller.updateOrder(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 重读 list：b 在 a 前面
        var names = controller.list().getBody().stream().map(WorkspaceDto::name).toList();
        assertThat(names).containsExactly("agent-demo", "b", "a");
    }

    @Test
    void updateOrderMismatchReturns400() throws Exception {
        Path a = tmp.resolve("a"); Files.createDirectories(a);
        controller.create(new CreateWorkspaceRequest(a.toString()));

        // 包含不存在的 name
        var body = Map.<String, List<String>>of("order", List.of("a", "nope"));
        ResponseEntity<?> resp = controller.updateOrder(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}