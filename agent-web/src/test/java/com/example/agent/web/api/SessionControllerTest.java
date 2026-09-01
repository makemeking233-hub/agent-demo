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
    void runtimeHasSessionReflectsDisk() throws Exception {
        assertThat(rt.hasSession("nope")).isFalse();
        writeSession("s-x", SessionEntry.user("hi", null));
        assertThat(rt.hasSession("s-x")).isTrue();
    }
}
