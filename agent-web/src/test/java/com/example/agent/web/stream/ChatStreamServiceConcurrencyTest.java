package com.example.agent.web.stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 多流并发隔离回归（add-true-streaming）。
 *
 * <p>每个 {@code ChatStreamService.ActiveStream} 持有自己的 sink，4 条流同时跑时事件不得互相串台
 * （错投会把 A 会话的模型输出渲染到 B 会话的界面上）。
 */
class ChatStreamServiceConcurrencyTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int STREAMS = 4;

    /** 收集某条流收到的正文增量。 */
    private static final class Collector {
        final List<String> texts = new CopyOnWriteArrayList<>();
        private final CountDownLatch done = new CountDownLatch(1);

        Collector(ChatStreamService svc, String streamId) {
            svc.stream(streamId)
                    .subscribe(
                            sse -> {
                                JsonNode n = read(sse);
                                if ("text".equals(n.path("delta_type").asText())) {
                                    texts.add(n.path("content").asText());
                                }
                            },
                            err -> done.countDown(),
                            done::countDown);
        }

        void await() throws InterruptedException {
            assertThat(done.await(30, SECONDS)).as("stream should complete").isTrue();
        }
    }

    private static JsonNode read(ServerSentEvent<Object> sse) {
        try {
            return JSON.readTree((String) sse.data());
        } catch (Exception e) {
            throw new IllegalStateException("bad SSE payload: " + sse.data(), e);
        }
    }

    /** 每个会话一个假 provider，各自吐带自身标签的增量。 */
    private static WebAgentRuntime runtimeBySession() {
        Map<String, LlmProvider> bySession = new HashMap<>();
        for (int i = 1; i <= STREAMS; i++) {
            String tag = "S" + i;
            LlmProvider p = mock(LlmProvider.class);
            when(p.contextWindow()).thenReturn(100_000);
            when(p.maxOutputTokens()).thenReturn(8192);
            when(p.streamChat(any(ChatRequest.class)))
                    .thenReturn(
                            Flux.<StreamChunk>just(
                                            new StreamChunk.TextDelta(tag + "-a"),
                                            new StreamChunk.TextDelta(tag + "-b"),
                                            new StreamChunk.Finished(FinishReason.STOP, null))
                                    .delayElements(Duration.ofMillis(20)));
            bySession.put("sess" + i, p);
        }

        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.sinkFor(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        when(runtime.createLoop(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            String sessionId = inv.getArgument(1);
                            SessionLogSink sink = inv.getArgument(2);
                            return AgentLoopFactory.buildLoop(
                                    AgentConfig.defaults(),
                                    bySession.get(sessionId),
                                    AgentLoopFactory.buildTools(AgentConfig.defaults()),
                                    new MessageHistory(new TokenEstimator()),
                                    new StreamingPrinter(),
                                    "deepseek-chat",
                                    sink,
                                    null,
                                    PermissionConfirmer.allowAll());
                        });
        return runtime;
    }

    @Test
    void fourConcurrentStreamsDoNotInterleaveEachOther() throws Exception {
        ChatStreamService svc = new ChatStreamService(runtimeBySession(), new PermissionBridge());

        // 先全部 create + 订阅，再全部 start：最大程度制造并发交错
        List<ChatStreamService.ActiveStream> metas = new ArrayList<>();
        List<Collector> collectors = new ArrayList<>();
        for (int i = 1; i <= STREAMS; i++) {
            ChatStreamService.ActiveStream meta = svc.create("sess" + i, "deepseek-chat");
            metas.add(meta);
            collectors.add(new Collector(svc, meta.streamId()));
        }
        for (ChatStreamService.ActiveStream meta : metas) {
            svc.start(meta.streamId(), "hi");
        }
        for (Collector c : collectors) {
            c.await();
        }

        // 每条流只看到自己的两个增量，顺序不乱、不串台
        for (int i = 1; i <= STREAMS; i++) {
            assertThat(collectors.get(i - 1).texts)
                    .as("stream %d must only see its own deltas", i)
                    .containsExactly("S" + i + "-a", "S" + i + "-b");
        }
    }
}
