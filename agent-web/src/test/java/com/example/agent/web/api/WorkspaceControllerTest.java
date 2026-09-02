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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** WorkspaceController（add-workspaces-and-rename）：列工作区 + 创建工作区校验。 */
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
                def.search());
    }

    @Test
    void listIncludesDefaultAndCreated() throws Exception {
        Path workDir = tmp.resolve("md-main");
        Files.createDirectories(workDir);
        controller.create(new CreateWorkspaceRequest("md-main", workDir.toString()));

        ResponseEntity<List<WorkspaceDto>> resp = controller.list();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().stream().map(WorkspaceDto::name))
                .contains("agent-demo", "md-main");
    }

    @Test
    void createReturns200AndPersists() throws Exception {
        Path workDir = tmp.resolve("ws-1");
        Files.createDirectories(workDir);
        ResponseEntity<?> resp = controller.create(new CreateWorkspaceRequest("ws-1", workDir.toString()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(com.example.agent.session.WorkspaceStore.exists(tmp, "ws-1")).isTrue();
    }

    @Test
    void createDirNotFoundReturns400() {
        ResponseEntity<?> resp = controller.create(new CreateWorkspaceRequest("ws", tmp.resolve("nope").toString()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        Path workDir = tmp.resolve("ws");
        Files.createDirectories(workDir);
        controller.create(new CreateWorkspaceRequest("ws", workDir.toString()));
        ResponseEntity<?> resp = controller.create(new CreateWorkspaceRequest("ws", workDir.toString()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void sessionCountReflectsWorkspaceSessions() throws Exception {
        Path workDir = tmp.resolve("md-main");
        Files.createDirectories(workDir);
        controller.create(new CreateWorkspaceRequest("md-main", workDir.toString()));
        Path wsSessions = tmp.resolve("workspaces/md-main/sessions");
        Files.createDirectories(wsSessions);
        SessionStore store = new SessionStore(wsSessions.resolve("s-1.jsonl"), 50, 60_000);
        store.append(SessionEntry.user("hi", null));
        store.syncFlush();
        store.close();

        ResponseEntity<List<WorkspaceDto>> resp = controller.list();
        WorkspaceDto md =
                resp.getBody().stream().filter(w -> w.name().equals("md-main")).findFirst().get();
        assertThat(md.sessionCount()).isEqualTo(1);
    }
}
