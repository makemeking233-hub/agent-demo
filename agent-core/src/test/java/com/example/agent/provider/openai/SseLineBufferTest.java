package com.example.agent.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

/**
 * {@link SseLineBuffer} 单元测试（add-true-streaming）。
 *
 * <p>用合成 {@link DataBuffer} 精确控制切分位置，不依赖 HTTP，验证「跨 buffer 的半截行」被正确拼接。
 */
class SseLineBufferTest {

    private static final DefaultDataBufferFactory FACTORY = new DefaultDataBufferFactory();

    private static DataBuffer buf(String s) {
        return FACTORY.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> collect(Flux<DataBuffer> source) {
        return SseLineBuffer.lines(source).collectList().block();
    }

    @Test
    void splitsCompleteLinesWithinSingleBuffer() {
        List<String> lines = collect(Flux.just(buf("alpha\nbeta\n")));

        assertThat(lines).containsExactly("alpha", "beta");
    }

    @Test
    void joinsLineSplitAcrossTwoBuffers() {
        // 关键用例：一个 SSE 事件被 TCP 切成两段，必须拼成一行而不是各吐半截
        List<String> lines =
                collect(
                        Flux.just(
                                buf("data: {\"choices\":[{\"delta\""),
                                buf(":{\"content\":\"hi\"}}]}\n\n")));

        assertThat(lines).containsExactly("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}", "");
    }

    @Test
    void joinsLineSplitAtEverySingleCharacter() {
        String payload = "data: {\"a\":1}\ndata: [DONE]\n";
        Flux<DataBuffer> drip =
                Flux.fromIterable(payload.chars().mapToObj(c -> buf(String.valueOf((char) c))).toList());

        assertThat(collect(drip)).containsExactly("data: {\"a\":1}", "data: [DONE]");
    }

    @Test
    void flushesTrailingPartialLineOnCompletion() {
        // 上游结束时最后一行没有 \n（SSE 流被截断），也要吐出来，不能静默丢弃
        List<String> lines = collect(Flux.just(buf("data: {\"a\":1}")));

        assertThat(lines).containsExactly("data: {\"a\":1}");
    }

    @Test
    void keepsCrFromCrlfForCallerToTrim() {
        List<String> lines = collect(Flux.just(buf("data: x\r\n")));

        assertThat(lines).containsExactly("data: x\r");
    }

    @Test
    void bufferStateIsPerSubscription() {
        // 同一 Flux 订阅两次：行缓冲不能跨订阅泄漏（第二次不能收到第一次的残留）
        Flux<String> lines = SseLineBuffer.lines(Flux.just(buf("a\nb\n")));

        assertThat(lines.collectList().block()).containsExactly("a", "b");
        assertThat(lines.collectList().block()).containsExactly("a", "b");
    }

    @Test
    void emptySourceCompletesWithoutEmitting() {
        assertThat(collect(Flux.empty())).isEmpty();
    }
}
