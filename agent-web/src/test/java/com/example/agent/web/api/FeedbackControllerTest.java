package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.session.MessageFeedbackStore;
import com.example.agent.session.MessageFeedbackStore.FeedbackSnapshot;
import com.example.agent.session.SessionEntry;
import com.example.agent.session.SessionStore;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.web.api.dto.FeedbackDeleteRequest;
import com.example.agent.web.api.dto.FeedbackPutRequest;
import com.example.agent.web.api.dto.FeedbackResponse;
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

/**
 * FeedbackController 单元测试（add-message-feedback F2.4）。
 *
 * <p>直接调控制器方法，{@code @TempDir} 隔离真实 {@code ~/.agent-demo/feedback/}。
 */
class FeedbackControllerTest {

    @TempDir Path tmp;

    private FeedbackController controller;
    private MessageFeedbackStore store;

    @BeforeEach
    void setUp() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.name()).thenReturn("deepseek");
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        WebAgentRuntime rt = new WebAgentRuntime(
                provider, new ToolRegistry(), new TokenEstimator(), tmp, cfgNoLogging());
        // 用 @TempDir 下的 feedback 子目录
        Path feedbackDir = tmp.resolve("feedback");
        store = new MessageFeedbackStore(feedbackDir);
        controller = new FeedbackController(store, rt);
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
        SessionStore s = new SessionStore(sessionsDir.resolve(sessionId + ".jsonl"), 50, 60_000);
        for (SessionEntry e : entries) s.append(e);
        s.syncFlush();
        s.close();
    }

    private static String u(String s) {
        return "u-" + s;
    }

    @Test
    void getEmptySessionReturnsEmptyItems() {
        ResponseEntity<FeedbackResponse> resp = controller.get("s-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        FeedbackResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.sessionId()).isEqualTo("s-1");
        assertThat(body.items()).isEmpty();
    }

    @Test
    void getReturnsItemsAfterWrites() {
        // 直接写 sidecar，绕过 runtime.hasSession 限制（GET 不查 session 存在性）
        store.put("s-1", u("a"), MessageFeedbackStore.Rating.UP, null);
        store.put("s-1", u("b"), MessageFeedbackStore.Rating.DOWN, null);
        FeedbackResponse body = controller.get("s-1").getBody();
        assertThat(body).isNotNull();
        assertThat(body.items()).containsOnlyKeys(u("a"), u("b"));
        assertThat(body.items().get(u("a")).rating()).isEqualTo("up");
        assertThat(body.items().get(u("b")).rating()).isEqualTo("down");
    }

    @Test
    void getWithInvalidSessionIdReturns400() {
        ResponseEntity<FeedbackResponse> resp = controller.get("../escape");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void putHappyPathReturns200WithVersion() throws Exception {
        writeSession("s-1", SessionEntry.user("问", null),
                SessionEntry.assistant("答", List.of(), null));
        ResponseEntity<?> resp = controller.put("s-1", u("m"),
                new FeedbackPutRequest("up", null));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsEntry("rating", "up");
        assertThat(body).containsEntry("version", 1L);
        assertThat(body).containsEntry("uuid", u("m"));
    }

    @Test
    void putWithInvalidRatingReturns400() throws Exception {
        writeSession("s-1", SessionEntry.user("问", null),
                SessionEntry.assistant("答", List.of(), null));
        ResponseEntity<?> resp = controller.put("s-1", u("m"),
                new FeedbackPutRequest("maybe", null));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).extracting("error").isEqualTo("rating_invalid");
    }

    @Test
    void putOnUnknownSessionReturns404() {
        ResponseEntity<?> resp = controller.put("nope", u("m"),
                new FeedbackPutRequest("up", null));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).extracting("error").isEqualTo("session_not_found");
    }

    @Test
    void putWithPathTraversalReturns400() throws Exception {
        writeSession("s-1", SessionEntry.user("q", null));
        ResponseEntity<?> resp = controller.put("s-1", "../escape",
                new FeedbackPutRequest("up", null));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).extracting("error").isEqualTo("message_invalid");
    }

    @Test
    void putCasConflictReturns409WithCurrent() throws Exception {
        writeSession("s-1", SessionEntry.user("q", null));
        // 第一次创建 v=1
        ResponseEntity<?> first = controller.put("s-1", u("m"),
                new FeedbackPutRequest("up", null));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 再用 ifVersion=null → 冲突，current 为 v=1
        ResponseEntity<?> dup = controller.put("s-1", u("m"),
                new FeedbackPutRequest("up", null));
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) dup.getBody();
        assertThat(body).containsKey("current");
        @SuppressWarnings("unchecked")
        Map<String, Object> current = (Map<String, Object>) body.get("current");
        assertThat(current).containsEntry("rating", "up");
        assertThat(current).containsEntry("version", 1L);
    }

    @Test
    void putVersionMismatchReturns409WithCurrent() throws Exception {
        writeSession("s-1", SessionEntry.user("q", null));
        controller.put("s-1", u("m"), new FeedbackPutRequest("up", null));
        // v=1 存在；用 ifVersion=99 → 冲突
        ResponseEntity<?> resp = controller.put("s-1", u("m"),
                new FeedbackPutRequest("down", 99L));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsKey("current");
    }

    @Test
    void deleteWithMatchingVersionRemoves() throws Exception {
        writeSession("s-1", SessionEntry.user("q", null));
        controller.put("s-1", u("m"), new FeedbackPutRequest("up", null));
        ResponseEntity<?> resp = controller.delete("s-1", u("m"),
                new FeedbackDeleteRequest(1L));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(store.get("s-1", u("m"))).isNull();
    }

    @Test
    void deleteAbsentReturns409WithNullCurrent() throws Exception {
        writeSession("s-1", SessionEntry.user("q", null));
        ResponseEntity<?> resp = controller.delete("s-1", u("nope"),
                new FeedbackDeleteRequest(null));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsKey("current");
        assertThat(body.get("current")).isNull();
    }

    @Test
    void deleteWithVersionMismatchReturns409() throws Exception {
        writeSession("s-1", SessionEntry.user("q", null));
        controller.put("s-1", u("m"), new FeedbackPutRequest("up", null));
        ResponseEntity<?> resp = controller.delete("s-1", u("m"),
                new FeedbackDeleteRequest(99L));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void snapshotOrderingIsStable() {
        store.put("s-1", u("a"), MessageFeedbackStore.Rating.UP, null);
        store.put("s-1", u("b"), MessageFeedbackStore.Rating.UP, null);
        store.put("s-1", u("a"), MessageFeedbackStore.Rating.DOWN, 1L); // a 升到 v2
        FeedbackSnapshot snap = store.getAll("s-1");
        assertThat(snap.items()).hasSize(2);
        assertThat(snap.items().keySet().iterator().next()).isEqualTo(u("a"));
    }
}