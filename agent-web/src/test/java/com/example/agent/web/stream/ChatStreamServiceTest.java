package com.example.agent.web.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.AgentLoop;
import com.example.agent.core.AgentLoopFactory;
import com.example.agent.core.MessageHistory;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.LlmProvider;
import com.example.agent.llm.StreamChunk;
import com.example.agent.llm.TokenEstimator;
import com.example.agent.log.SessionLogSink;
import com.example.agent.permission.PermissionConfirmer;
import com.example.agent.render.StreamingPrinter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/** ChatStreamService（web SSE 编排）：create+start 触发一次真实 AgentLoop turn，SSE 收到完整事件序列。 */
class ChatStreamServiceTest {

    private static AgentLoop loopWithMockProvider(SessionLogSink sink) {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any(ChatRequest.class)))
                .thenReturn(
                        Flux.just(
                                (StreamChunk) new StreamChunk.TextDelta("你好"),
                                new StreamChunk.Finished(FinishReason.STOP, null)));
        return AgentLoopFactory.buildLoop(
                AgentConfig.defaults(),
                provider,
                AgentLoopFactory.buildTools(AgentConfig.defaults()),
                new MessageHistory(new TokenEstimator()),
                new StreamingPrinter(),
                "deepseek-chat",
                sink,
                null,
                PermissionConfirmer.allowAll());
    }

    /** mock 运行时：{@code sinkFor} 把 SSE sink 原样透传（不落盘），{@code createLoop} 用传入 sink 装配。 */
    private static WebAgentRuntime mockRuntime() {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        // 用裸 any()（匹配含 null），因为默认 create 的 workspace / sink 可能为 null。
        when(runtime.sinkFor(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(runtime.createLoop(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            SessionLogSink sink = inv.getArgument(2);
                            AgentLoop loop = loopWithMockProvider(sink);
                            com.example.agent.permission.PermissionMode mode = inv.getArgument(5);
                            if (mode != null) {
                                loop.setPermissionMode(mode);
                            }
                            return loop;
                        });
        return runtime;
    }

    @Test
    void createThenStartRunsTurnAndClosesStream() throws Exception {
        WebAgentRuntime runtime = mockRuntime();
        PermissionBridge bridge = new PermissionBridge();
        ChatStreamService svc = new ChatStreamService(runtime, bridge);
        ChatStreamService.ActiveStream meta = svc.create("session-1", "deepseek-chat");

        CountDownLatch closed = new CountDownLatch(1);
        java.util.List<String> eventTypes = new java.util.concurrent.CopyOnWriteArrayList<>();
        svc.stream(meta.streamId())
                .subscribe(
                        sse -> eventTypes.add((String) sse.event()),
                        err -> closed.countDown(),
                        closed::countDown);

        svc.start(meta.streamId(), "hi");

        assertThat(closed.await(30, TimeUnit.SECONDS)).as("stream should close after turn").isTrue();
        assertThat(eventTypes).contains("message_start", "message_delta", "message_stop");
    }

    @Test
    void submitDecisionForUnknownPermissionReturnsFalse() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("session-1", "deepseek-chat");

        assertThat(svc.submitDecision(meta.streamId(), "no-such-permission", "yes")).isFalse();
    }

    @Test
    void decisionEndpointWakesWaitingThread() throws Exception {
        PermissionBridge bridge = new PermissionBridge();
        String permissionId = bridge.newPermissionId();
        String[] result = new String[1];
        Thread waiting =
                new Thread(
                        () ->
                                result[0] =
                                        bridge.waitForDecision(permissionId, "tc1", "write_file", "理由", java.util.List.of("yes", "no", "always")));
        waiting.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (!bridge.hasPending(permissionId) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(bridge.submitDecision(permissionId, "yes")).isTrue();
        waiting.join(5000);
        assertThat(result[0]).isEqualTo("yes");
    }

    @Test
    void getReturnsActiveStream() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("session-1", "deepseek-chat");
        assertThat(svc.get(meta.streamId())).isSameAs(meta);
        assertThat(svc.get("unknown")).isNull();
    }

    @Test
    void abortUnknownStreamIsNoop() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        svc.abort("unknown");
    }

    @Test
    void submitDecisionUnknownStreamReturnsFalse() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        assertThat(svc.submitDecision("unknown", "p1", "yes")).isFalse();
    }

    @Test
    void shutdownCompletesActiveStreamsAndStopsExecutor() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        svc.create("session-1", "deepseek-chat");
        svc.shutdown();
    }

    // ---- add-permission-mode-dropdown：创建带初始模式 + setPermission 实时切换 ----

    @Test
    void createWithInitialModeSetsLoopMode() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("s1", "deepseek-chat", com.example.agent.permission.PermissionMode.WORKSPACE_WRITE);
        assertThat(meta.loop().permissionMode()).isEqualTo(com.example.agent.permission.PermissionMode.WORKSPACE_WRITE);
    }

    @Test
    void createDefaultModeIsReadOnly() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("s1", "deepseek-chat");
        assertThat(meta.loop().permissionMode()).isEqualTo(com.example.agent.permission.PermissionMode.DEFAULT);
    }

    @Test
    void setPermissionSwitchesActiveStream() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("s1", "deepseek-chat");
        assertThat(svc.setPermission(meta.streamId(), com.example.agent.permission.PermissionMode.FULL_ACCESS)).isTrue();
        assertThat(meta.loop().permissionMode()).isEqualTo(com.example.agent.permission.PermissionMode.FULL_ACCESS);
    }

    @Test
    void setPermissionUnknownStreamReturnsFalse() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        assertThat(svc.setPermission("unknown", com.example.agent.permission.PermissionMode.FULL_ACCESS)).isFalse();
    }

    // ---- add-workspaces-and-rename：create 归属工作区 + workspaceExists ----

    @Test
    void createWithWorkspaceBuilds() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("s1", "deepseek-chat", null, "md-main");
        assertThat(meta).isNotNull();
        assertThat(meta.loop().permissionMode()).isEqualTo(com.example.agent.permission.PermissionMode.DEFAULT);
    }

    @Test
    void workspaceExistsDefaultsTrueForBlank() {
        WebAgentRuntime runtime = mockRuntime();
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        assertThat(svc.workspaceExists(null)).isTrue();
        assertThat(svc.workspaceExists("")).isTrue();
    }
}
