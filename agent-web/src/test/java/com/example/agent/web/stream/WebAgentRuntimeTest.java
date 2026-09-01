package com.example.agent.web.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.Message;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.CompositeSessionLogSink;
import com.example.agent.log.SessionLogSink;
import com.example.agent.log.SessionRecorder;
import com.example.agent.session.SessionEntry;
import com.example.agent.session.SessionStore;
import com.example.agent.tools.ToolRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Bump web.stream coverage: WebAgentRuntime 装配 & createLoop / tools / agentDataDir / 会话重进恢复. */
class WebAgentRuntimeTest {

    @TempDir Path tmp;

    private LlmProvider mockProvider() {
        LlmProvider p = mock(LlmProvider.class);
        when(p.name()).thenReturn("deepseek");
        when(p.contextWindow()).thenReturn(100_000);
        when(p.maxOutputTokens()).thenReturn(8192);
        return p;
    }

    /** 关闭会话日志（避免写工作区 logs/）的测试用配置。 */
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

    private WebAgentRuntime runtime(Path agentDataDir) {
        return new WebAgentRuntime(mockProvider(), new ToolRegistry(), new TokenEstimator(), agentDataDir, cfgNoLogging());
    }

    private void writeSession(Path agentDataDir, String sessionId, SessionEntry... entries) throws Exception {
        Path sessionsDir = agentDataDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        SessionStore store = new SessionStore(sessionsDir.resolve(sessionId + ".jsonl"), 50, 60_000);
        for (SessionEntry e : entries) store.append(e);
        store.syncFlush();
        store.close();
    }

    @Test
    void createLoopReturnsAgentLoop() {
        WebAgentRuntime rt = runtime(tmp);
        var loop = rt.createLoop("s1", "s1", SessionLogSink.NOOP, null, null);
        assertThat(loop).isNotNull();
    }

    @Test
    void exposesToolsAndAgentDataDir() {
        WebAgentRuntime rt = runtime(tmp);
        assertThat(rt.tools()).isNotNull();
        assertThat(rt.agentDataDir()).isNotNull();
    }

    @Test
    void createLoopWithConfirmerBuilds() {
        WebAgentRuntime rt = runtime(tmp);
        var loop = rt.createLoop("s1", "s1", SessionLogSink.NOOP, com.example.agent.permission.PermissionConfirmer.allowAll(), null);
        assertThat(loop).isNotNull();
    }

    @Test
    void historyForBackfillsFromDisk() throws Exception {
        WebAgentRuntime rt = runtime(tmp);
        writeSession(
                tmp,
                "s-9",
                SessionEntry.user("你好", null),
                SessionEntry.assistant("你好！", List.of(), null));

        com.example.agent.core.MessageHistory history = rt.historyFor("s-9");
        assertThat(history.size()).isGreaterThanOrEqualTo(2);
        assertThat(history.all().get(0).role()).isEqualTo("user");
    }

    @Test
    void historyForNewSessionReturnsEmpty() {
        WebAgentRuntime rt = runtime(tmp);
        com.example.agent.core.MessageHistory history = rt.historyFor("brand-new");
        assertThat(history.size()).isZero();
    }

    @Test
    void messagesForRestoresFromDisk() throws Exception {
        WebAgentRuntime rt = runtime(tmp);
        writeSession(tmp, "s-8", SessionEntry.user("hi", null), SessionEntry.assistant("hello", List.of(), null));

        List<Message> messages = rt.messagesFor("s-8");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content()).isEqualTo("hi");
    }

    @Test
    void sinkForKnownSessionReturnsComposite() throws Exception {
        WebAgentRuntime rt = runtime(tmp);
        writeSession(tmp, "s-forget", SessionEntry.user("x", null));
        SessionLogSink sse = mock(SessionLogSink.class);
        SessionLogSink sink = rt.sinkFor("s-forget", sse);
        assertThat(sink).isInstanceOf(CompositeSessionLogSink.class);
        rt.close();
    }

    @Test
    void sinkForNullSessionReturnsSseOnly() {
        WebAgentRuntime rt = runtime(tmp);
        SessionLogSink sse = mock(SessionLogSink.class);
        assertThat(rt.sinkFor(null, sse)).isSameAs(sse);
    }

    @Test
    void hasSessionReflectsDiskAndMemory() throws Exception {
        WebAgentRuntime rt = runtime(tmp);
        assertThat(rt.hasSession("nope")).isFalse();
        writeSession(tmp, "s-mem", SessionEntry.user("hi", null));
        assertThat(rt.hasSession("s-mem")).isTrue();
        // 主动触达后（内存活动）也 true
        rt.historyFor("s-mem");
        assertThat(rt.hasSession("s-mem")).isTrue();
    }

    @Test
    void historyForNullOrBlankReturnsEmpty() {
        WebAgentRuntime rt = runtime(tmp);
        assertThat(rt.historyFor((String) null).size()).isZero();
        assertThat(rt.historyFor("").size()).isZero();
    }

    @Test
    void messagesForNullOrBlankReturnsEmpty() {
        WebAgentRuntime rt = runtime(tmp);
        assertThat(rt.messagesFor((String) null)).isEmpty();
        assertThat(rt.messagesFor(" ")).isEmpty();
    }

    @Test
    void messagesForLiveSessionReturnsLiveMessages() throws Exception {
        WebAgentRuntime rt = runtime(tmp);
        writeSession(tmp, "s-live", SessionEntry.user("hi", null), SessionEntry.assistant("hello", List.of(), null));
        rt.historyFor("s-live"); // 回填并缓存 live history
        List<Message> messages = rt.messagesFor("s-live");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content()).isEqualTo("hi");
    }

    @Test
    void sinkForBlankSessionReturnsSseOnly() {
        WebAgentRuntime rt = runtime(tmp);
        SessionLogSink sse = mock(SessionLogSink.class);
        assertThat(rt.sinkFor("", sse)).isSameAs(sse);
    }
}


