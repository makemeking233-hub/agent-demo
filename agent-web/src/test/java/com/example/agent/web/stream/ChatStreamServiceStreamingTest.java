package com.example.agent.web.stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.config.AgentConfig;
import com.example.agent.core.AgentLoopFactory;
import com.example.agent.core.Message;
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 真流式端到端验证（add-true-streaming）。
 *
 * <p>断言 SSE 契约层面的行为：模型还在生成时，每个 {@code TextDelta} 就各自变成一条
 * {@code message_delta(text)} 推给前端；而不是等整轮 {@code collectList()} 完成后整段一次推。
 * 同时验证「整段重发」不会发生（否则前端会把正文渲染两遍）。
 */
class ChatStreamServiceStreamingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 一条 SSE 事件的观测记录。 */
    private record Seen(String type, String deltaType, String content, long atMs) {}

    private static WebAgentRuntime runtimeWithProvider(LlmProvider provider) {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        // 裸 any()：create 的 workspace / sink 可能为 null，带类型的 any() 不匹配 null。
        when(runtime.sinkFor(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        when(runtime.createLoop(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            // 参数顺序: streamId, sessionId, model, sessionSink, confirmer, abortSignal, mode, workspace
                            SessionLogSink sink = inv.getArgument(3);
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
                        });
        return runtime;
    }

    /** 每 150ms 吐一个 chunk 的假 provider（A/B/C + Finished）。 */
    private static LlmProvider slowProvider() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.contextWindow()).thenReturn(100_000);
        when(provider.maxOutputTokens()).thenReturn(8192);
        when(provider.streamChat(any(ChatRequest.class)))
                .thenReturn(
                        Flux.<StreamChunk>just(
                                        new StreamChunk.TextDelta("A"),
                                        new StreamChunk.TextDelta("B"),
                                        new StreamChunk.TextDelta("C"),
                                        new StreamChunk.Finished(FinishReason.STOP, null))
                                .delayElements(Duration.ofMillis(150)));
        return provider;
    }

    /** 订阅 SSE 并累积事件；订阅后调用方再触发 {@code start}，模拟前端「先连流再发消息」。 */
    private static final class Collector {
        final List<Seen> seen = new CopyOnWriteArrayList<>();
        private final CountDownLatch done = new CountDownLatch(1);

        Collector(ChatStreamService svc, String streamId, long t0) {
            svc.stream(streamId)
                    .subscribe(
                            sse -> seen.add(parse(sse, t0)),
                            err -> done.countDown(),
                            done::countDown);
        }

        void await() throws InterruptedException {
            assertThat(done.await(30, SECONDS)).as("stream should complete").isTrue();
        }
    }

    private static List<Seen> collect(ChatStreamService svc, String streamId, long t0)
            throws InterruptedException {
        Collector c = new Collector(svc, streamId, t0);
        c.await();
        return c.seen;
    }

    private static Seen parse(ServerSentEvent<Object> sse, long t0) {
        try {
            JsonNode node = JSON.readTree((String) sse.data());
            return new Seen(
                    node.path("type").asText(),
                    node.path("delta_type").asText(""),
                    node.path("content").asText(""),
                    System.currentTimeMillis() - t0);
        } catch (Exception e) {
            throw new IllegalStateException("bad SSE payload: " + sse.data(), e);
        }
    }

    private static List<String> textPayloads(List<Seen> seen) {
        return seen.stream().filter(s -> "text".equals(s.deltaType())).map(Seen::content).toList();
    }

    @Test
    void textDeltasReachSseIncrementallyAndAreNotRepeatedAsWholeParagraph() throws Exception {
        ChatStreamService svc =
                new ChatStreamService(runtimeWithProvider(slowProvider()), new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("s1", "deepseek-chat");

        long t0 = System.currentTimeMillis();
        Collector collector = new Collector(svc, meta.streamId(), t0);
        svc.start(meta.streamId(), "hi");
        collector.await();

        List<Seen> seen = collector.seen;
        // 逐 token：三段各自一条；若 onAssistant 又整段重发一次，这里会多出 "ABC"
        assertThat(textPayloads(seen)).containsExactly("A", "B", "C");

        long firstTextAt = seen.stream().filter(s -> "text".equals(s.deltaType())).findFirst().orElseThrow().atMs();
        long stopAt = seen.stream().filter(s -> "message_stop".equals(s.type())).findFirst().orElseThrow().atMs();
        // 首个 token 明显早于流结束（150ms 出 A，约 600ms 才 Finished）
        assertThat(firstTextAt)
                .as("first text delta should arrive well before completion")
                .isLessThan(stopAt - 200);
    }

    @Test
    void onAssistantStillEmitsWholeTextWhenNothingWasStreamed() throws Exception {
        // 兜底：非流式 provider（整轮没有任何 TextDelta）仍需把正文推出去
        ChatStreamService svc =
                new ChatStreamService(runtimeWithProvider(slowProvider()), new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("s1", "deepseek-chat");

        long t0 = System.currentTimeMillis();
        meta.sinkAdapter().onAssistant(new Message.Assistant("整段文本", List.of()), List.of());
        svc.stop(meta.streamId(), "stop");

        assertThat(textPayloads(collect(svc, meta.streamId(), t0))).containsExactly("整段文本");
    }

    @Test
    void onAssistantDoesNotRepeatTextAlreadyStreamedIncrementally() throws Exception {
        ChatStreamService svc =
                new ChatStreamService(runtimeWithProvider(slowProvider()), new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("s1", "deepseek-chat");

        long t0 = System.currentTimeMillis();
        meta.sinkAdapter().onTextDelta("你");
        meta.sinkAdapter().onTextDelta("好");
        // 整轮结束时的 onAssistant 带全文 "你好" — 不能再发一遍
        meta.sinkAdapter().onAssistant(new Message.Assistant("你好", List.of()), List.of());
        svc.stop(meta.streamId(), "stop");

        assertThat(textPayloads(collect(svc, meta.streamId(), t0))).containsExactly("你", "好");
    }

    @Test
    void textStreamedFlagResetsPerModelIteration() throws Exception {
        // 每轮模型输出独立判重：上一轮推过增量，不能抑制这一轮 onAssistant 的兜底
        ChatStreamService svc =
                new ChatStreamService(runtimeWithProvider(slowProvider()), new PermissionBridge());
        ChatStreamService.ActiveStream meta = svc.create("s1", "deepseek-chat");

        long t0 = System.currentTimeMillis();
        meta.sinkAdapter().onTextDelta("第一轮");
        meta.sinkAdapter().onAssistant(new Message.Assistant("第一轮", List.of()), List.of());
        // 第二轮没有任何增量（例如工具轮），全文必须兜底发出
        meta.sinkAdapter().onAssistant(new Message.Assistant("第二轮全文", List.of()), List.of());
        svc.stop(meta.streamId(), "stop");

        assertThat(textPayloads(collect(svc, meta.streamId(), t0)))
                .containsExactly("第一轮", "第二轮全文");
    }
}
