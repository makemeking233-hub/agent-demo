package com.example.agent.provider.openai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;

/**
 * SSE 行累积器（add-true-streaming）。
 *
 * <p>{@code bodyToFlux(DataBuffer.class)} 按 TCP 收到多少字节就给多少字节，与 SSE 的 {@code \n}
 * 事件边界无关：一个 {@code data:} 行可能被切成两个 buffer（前半 + 后半）。直接对单个 buffer
 * {@code split("\n")} 会把半截 JSON 交给解析器。本工具维护跨 buffer 的行缓冲，**边收边切**，
 * 只在遇到 {@code \n} 时向下游吐出完整行（不等整个响应结束）。
 *
 * <p>用法：
 *
 * <pre>{@code
 * bodyToFlux(DataBuffer.class)
 *     .transform(SseLineBuffer::lines)
 *     .filter(line -> line.startsWith("data: "))
 *     ...
 * }</pre>
 *
 * <p>语义约定：
 *
 * <ul>
 *   <li>输出的是**每一行**（去掉行尾 {@code \n}），包括空行；是否过滤空行由调用方决定。
 *   <li>{@code \r\n} 行尾会残留一个 {@code \r}，由调用方 {@code trim()}；SSE 规范里这两种行尾等价。
 *   <li>状态（未闭合的行缓冲）是**每个订阅独立**的（{@link Flux#defer} 保证），可安全重复订阅。
 *   <li>源 buffer 在本算子内读取后立即 {@link DataBufferUtils#release} 归还，调用方无需处理。
 * </ul>
 */
public final class SseLineBuffer {

    private SseLineBuffer() {}

    /**
     * 把 {@code Flux<DataBuffer>} 转为逐行的 {@code Flux<String>}。
     *
     * <p>用 {@link Flux#concatMap}（而非 {@code flatMap}）串行处理每个 buffer：行缓冲是有状态的，
     * 并发处理会让后到的 buffer 先于先到的 buffer 写进缓冲，拼出错误的行。
     *
     * @param source 原始响应体流（WebClient {@code bodyToFlux(DataBuffer.class)}）
     * @return 逐行流；源结束后若仍有未闭合的尾行，也会补吐一次
     */
    public static Flux<String> lines(Flux<DataBuffer> source) {
        return Flux.defer(
                () -> {
                    StringBuilder pending = new StringBuilder();
                    return source
                            .concatMap(
                                    buf -> {
                                        try {
                                            pending.append(
                                                    buf.toString(StandardCharsets.UTF_8));
                                        } finally {
                                            DataBufferUtils.release(buf);
                                        }
                                        List<String> complete = new ArrayList<>();
                                        int idx;
                                        while ((idx = pending.indexOf("\n")) >= 0) {
                                            complete.add(pending.substring(0, idx));
                                            pending.delete(0, idx + 1);
                                        }
                                        return Flux.fromIterable(complete);
                                    })
                            .concatWith(
                                    Flux.defer(
                                            () ->
                                                    pending.length() == 0
                                                            ? Flux.empty()
                                                            : Flux.just(pending.toString())));
                });
    }
}
