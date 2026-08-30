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
        // 用传入 sink（SseSessionLogSink）捕获 text，供断言
        // 用传入 sink（SseSessionLogSink）接收 AgentLoop 回调
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

    @Test
    void createThenStartRunsTurnAndClosesStream() throws Exception {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        // mock 接受 runtime 传入的 sink，确保 SSE 事件确实被 ChatStreamService 下发
        when(runtime.createLoop(any(String.class), any(String.class), any(SessionLogSink.class), any(), any()))
                .thenAnswer(
                        inv -> {
                            SessionLogSink sink = inv.getArgument(2);
                            return loopWithMockProvider(sink);
                        });

        PermissionBridge bridge = new PermissionBridge();
        ChatStreamService svc = new ChatStreamService(runtime, bridge);
        ChatStreamService.ActiveStream meta = svc.create("session-1", "deepseek-chat");

        // 提前订阅，避免 miss 事件；onComplete 用 latch 等待
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
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.createLoop(any(String.class), any(String.class), any(SessionLogSink.class), any(), any()))
                .thenAnswer(
                        inv -> {
                            SessionLogSink sink = inv.getArgument(2);
                            return loopWithMockProvider(sink);
                        });
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("session-1", "deepseek-chat");

        // 没有待决策的 permission_id → return false
        assertThat(svc.submitDecision(meta.streamId(), "no-such-permission", "yes")).isFalse();
    }

    @Test
    void decisionEndpointWakesWaitingThread() throws Exception {
        // 验证 submitDecision 走 PermissionBridge 唤醒一个 waitForDecision 线程。
        PermissionBridge bridge = new PermissionBridge();
        String permissionId = bridge.newPermissionId();
        String[] result = new String[1];
        Thread waiting =
                new Thread(
                        () ->
                                result[0] =
                                        bridge.waitForDecision(permissionId, "tc1", "write_file", "理由", java.util.List.of("yes", "no", "always")));
        waiting.start();
        // 等 waitForDecision 注册 waiter
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
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.createLoop(any(String.class), any(String.class), any(SessionLogSink.class), any(), any()))
                .thenAnswer(
                        inv -> {
                            SessionLogSink sink = inv.getArgument(2);
                            return loopWithMockProvider(sink);
                        });
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("session-1", "deepseek-chat");
        assertThat(svc.get(meta.streamId())).isSameAs(meta);
        assertThat(svc.get("unknown")).isNull();
    }

    @Test
    void abortUnknownStreamIsNoop() {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.createLoop(any(String.class), any(String.class), any(SessionLogSink.class), any(), any()))
                .thenAnswer(
                        inv -> {
                            SessionLogSink sink = inv.getArgument(2);
                            return loopWithMockProvider(sink);
                        });
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        svc.abort("unknown"); // 不抛即通过
    }

    @Test
    void submitDecisionUnknownStreamReturnsFalse() {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.createLoop(any(String.class), any(String.class), any(SessionLogSink.class), any(), any()))
                .thenAnswer(
                        inv -> {
                            SessionLogSink sink = inv.getArgument(2);
                            return loopWithMockProvider(sink);
                        });
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        assertThat(svc.submitDecision("unknown", "p1", "yes")).isFalse();
    }

    @Test
    void shutdownCompletesActiveStreamsAndStopsExecutor() {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.createLoop(any(String.class), any(String.class), any(SessionLogSink.class), any(), any()))
                .thenAnswer(
                        inv -> {
                            SessionLogSink sink = inv.getArgument(2);
                            return loopWithMockProvider(sink);
                        });
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        svc.create("session-1", "deepseek-chat");
        svc.shutdown(); // 不抛即通过
    }
}

