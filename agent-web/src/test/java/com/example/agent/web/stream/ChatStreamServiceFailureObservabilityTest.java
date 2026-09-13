package com.example.agent.web.stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.agent.core.AgentLoop;
import com.example.agent.core.Message;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 故障路径可观测性（improve-failure-observability）。
 *
 * <p>复现 2026-09-13 事故：工具抛出的 {@code NoClassDefFoundError}（属 {@code LinkageError}）被 Reactor
 * 的 {@code Exceptions.throwIfFatal} 原样 rethrow，绕过 {@code AgentLoop} 的
 * {@code doOnError} / {@code onErrorResume}。
 *
 * <p>此前 {@code ChatStreamService.start} 只 {@code catch (Exception)}，于是这类失败在日志里**零记录**、
 * 只留下一个撕裂的连接与一份悬挂 tool_calls 的会话存档，排查时连是哪个会话都要靠文件 mtime 猜。
 * 本测试断言修复后：日志带齐关联 id、在途工具调用被收口、SSE 收到 error 并正常关流。
 */
class ChatStreamServiceFailureObservabilityTest {

    @Test
    void linkageErrorFromTurnIsReportedWithContextAndClosesPendingTools() throws Exception {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.processTurn(any(Message.User.class)))
                .thenReturn(
                        Mono.error(
                                new NoClassDefFoundError(
                                        "com/example/agent/tools/file/EditFileTool$Input")));

        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        // 裸 any()：create 的 workspace / sink 可能为 null
        when(runtime.sinkFor(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        when(runtime.createLoop(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(loop);

        ChatStreamService svc = new ChatStreamService(runtime, new PermissionBridge());
        ChatStreamService.ActiveStream meta =
                svc.create("sess-1", "deepseek-chat", null, "ws-1");

        List<String> eventTypes = new CopyOnWriteArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        svc.stream(meta.streamId())
                .subscribe(
                        sse -> eventTypes.add((String) sse.event()),
                        err -> closed.countDown(),
                        closed::countDown);

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            svc.start(meta.streamId(), "hi");
            assertThat(closed.await(30, SECONDS)).as("流应被关闭").isTrue();

            // 日志必须同时带 stream / session / workspace / 异常类型 —— 缺任一项都无法反查
            assertThat(appender.list)
                    .as("必须留下可检索的 ERROR 记录")
                    .anySatisfy(
                            e ->
                                    assertThat(e.getFormattedMessage())
                                            .contains("turn failed")
                                            .contains("stream=" + meta.streamId())
                                            .contains("session=sess-1")
                                            .contains("workspace=ws-1")
                                            .contains("NoClassDefFoundError"));
            // 在途工具调用被收口（补 TOOL< 与 history）
            verify(loop).closePendingToolCalls(anyString());
            // 前端收到 error 事件
            assertThat(eventTypes).contains("error");
        } finally {
            detachAppender(appender);
        }
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
