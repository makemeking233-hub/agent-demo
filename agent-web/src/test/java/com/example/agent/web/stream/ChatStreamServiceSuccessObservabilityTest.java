package com.example.agent.web.stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.agent.core.AgentLoop;
import com.example.agent.core.Message;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 成功回合的可观测性（fix-stale-model-fallback T4）。
 *
 * <p>动机：本次排查「前端选择/显示的模型 vs 实际请求上游的模型」分歧时，发现日志**只在回合
 * 失败时**记 {@code model=}，成功回合不记。于是唯一能确证实际所用模型的手段只剩浏览器抓包。
 * 本测试锁死成功路径也留下同样字段的记录，使两条路径可对照。
 *
 * <p>失败路径的对照实现在 {@link ChatStreamServiceFailureObservabilityTest}。
 */
class ChatStreamServiceSuccessObservabilityTest {

    private static final String MODEL = "deepseek-v4-flash";
    private static final List<String> SHARED_KEYS = List.of("stream=", "session=", "workspace=", "model=");

    @Test
    void successfulTurnLogsModelName() throws Exception {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.processTurn(any(Message.User.class))).thenReturn(Mono.empty());
        ChatStreamService svc = serviceReturning(loop);
        ChatStreamService.ActiveStream meta = svc.create("sess-ok", MODEL, null, "ws-ok");

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            svc.start(meta.streamId(), "hi");
            awaitMessage(appender, "turn completed");

            assertThat(appender.list)
                    .as("成功回合必须留下可检索的记录")
                    .anySatisfy(
                            e ->
                                    assertThat(e.getFormattedMessage())
                                            .contains("turn completed")
                                            .contains("stream=" + meta.streamId())
                                            .contains("session=sess-ok")
                                            .contains("workspace=ws-ok")
                                            .contains("model=" + MODEL));
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void successAndFailureRecordsShareTheSameFieldKeys() throws Exception {
        AgentLoop okLoop = mock(AgentLoop.class);
        when(okLoop.processTurn(any(Message.User.class))).thenReturn(Mono.empty());
        AgentLoop failLoop = mock(AgentLoop.class);
        when(failLoop.processTurn(any(Message.User.class)))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.sinkFor(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        when(runtime.createLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(okLoop, failLoop);
        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            ChatStreamService.ActiveStream ok = svc.create("sess-sym", MODEL, null, "ws-sym");
            svc.start(ok.streamId(), "hi");
            awaitMessage(appender, "turn completed");

            ChatStreamService.ActiveStream bad = svc.create("sess-sym", MODEL, null, "ws-sym");
            svc.start(bad.streamId(), "hi");
            awaitMessage(appender, "turn failed");

            String completed = messageContaining(appender, "turn completed");
            String failed = messageContaining(appender, "turn failed");

            // 两条记录必须能用同一组键直接对照；键名逐字符相同才算对称
            for (String key : SHARED_KEYS) {
                assertThat(completed).as("成功记录缺 %s", key).contains(key);
                assertThat(failed).as("失败记录缺 %s", key).contains(key);
            }
            assertThat(completed).contains("model=" + MODEL);
            assertThat(failed).contains("model=" + MODEL);
        } finally {
            detachAppender(appender);
        }
    }

    private static ChatStreamService serviceReturning(AgentLoop loop) {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.sinkFor(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        when(runtime.createLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(loop);
        return new ChatStreamService(runtime, new PermissionBridge());
    }

    /**
     * 回合在后台 executor 里跑，日志出现时机不确定，故轮询等待而非固定 sleep。
     * 超时不抛异常，交给后续断言给出「缺了哪条」的可读失败信息。
     */
    private static void awaitMessage(ListAppender<ILoggingEvent> appender, String needle)
            throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (appender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains(needle))) {
                return;
            }
            Thread.sleep(50);
        }
    }

    private static String messageContaining(ListAppender<ILoggingEvent> appender, String needle) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains(needle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到含 " + needle + " 的日志记录"));
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(ChatStreamService.class)).addAppender(appender);
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(ChatStreamService.class)).detachAppender(appender);
    }
}
