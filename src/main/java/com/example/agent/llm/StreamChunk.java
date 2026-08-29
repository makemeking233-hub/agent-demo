package com.example.agent.llm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 流式响应 chunk 类型（sealed interface，详见 design.md §6.1）。
 *
 * <p>每个 record 对应 DeepSeek SSE 流的一种事件类型：
 *
 * <ul>
 *   <li>{@link TextDelta} - 增量文本
 *   <li>{@link ToolCallStart} / {@link ToolCallDelta} / {@link ToolCallEnd} - 工具调用流
 *   <li>{@link Usage} - token 计量
 *   <li>{@link Finished} - 流结束（含 finish_reason + usage）
 *   <li>{@link Error} - 流错误
 * </ul>
 *
 * <p>采用 visitor 模式（{@link #accept(StreamChunkVisitor)}）：新增聚合策略时只需新增 visitor 实现，无需改动 sealed 类型或
 * aggregate 方法。
 */
public sealed interface StreamChunk
        permits StreamChunk.TextDelta,
                StreamChunk.ToolCallStart,
                StreamChunk.ToolCallDelta,
                StreamChunk.ToolCallEnd,
                StreamChunk.Usage,
                StreamChunk.Finished,
                StreamChunk.Error {

    /**
     * 分发到 {@link StreamChunkVisitor} 的对应方法（visitor 模式核心）。
     *
     * @param v 访问者
     */
    void accept(StreamChunkVisitor v);

    /** Visitor 接口（默认空实现，按需 override） */
    interface StreamChunkVisitor {
        /**
         * @see TextDelta
         */
        default void visitTextDelta(TextDelta c) {}

        /**
         * @see ToolCallStart
         */
        default void visitToolCallStart(ToolCallStart c) {}

        /**
         * @see ToolCallDelta
         */
        default void visitToolCallDelta(ToolCallDelta c) {}

        /**
         * @see ToolCallEnd
         */
        default void visitToolCallEnd(ToolCallEnd c) {}

        /**
         * @see Usage
         */
        default void visitUsage(Usage c) {}

        /**
         * @see Finished
         */
        default void visitFinished(Finished c) {}

        /**
         * @see Error
         */
        default void visitError(Error c) {}
    }

    /** 增量文本 chunk */
    record TextDelta(String text) implements StreamChunk {
        @Override
        public void accept(StreamChunkVisitor v) {
            v.visitTextDelta(this);
        }
    }

    /** 工具调用开始（携带 id + name） */
    record ToolCallStart(String id, String name) implements StreamChunk {
        @Override
        public void accept(StreamChunkVisitor v) {
            v.visitToolCallStart(this);
        }
    }

    /** 工具调用参数增量（流式 JSON 片段） */
    record ToolCallDelta(String id, String argumentsDelta) implements StreamChunk {
        @Override
        public void accept(StreamChunkVisitor v) {
            v.visitToolCallDelta(this);
        }
    }

    /** 工具调用结束（参数完整） */
    record ToolCallEnd(String id, String name, String arguments) implements StreamChunk {
        @Override
        public void accept(StreamChunkVisitor v) {
            v.visitToolCallEnd(this);
        }
    }

    /** token 计量（prompt + completion） */
    record Usage(int promptTokens, int completionTokens) implements StreamChunk {
        @Override
        public void accept(StreamChunkVisitor v) {
            v.visitUsage(this);
        }
    }

    /** 流结束（含 finish_reason 和可选 usage） */
    record Finished(FinishReason reason, Usage usage) implements StreamChunk {
        @Override
        public void accept(StreamChunkVisitor v) {
            v.visitFinished(this);
        }
    }

    /** 流错误（message + http status + cause） */
    record Error(String message, int httpStatus, Throwable cause) implements StreamChunk {
        @Override
        public void accept(StreamChunkVisitor v) {
            v.visitError(this);
        }
    }

    /**
     * 工具调用累积器（按 id 分组）。
     *
     * <p>把 {@link ToolCallStart} / {@link ToolCallDelta} / {@link ToolCallEnd} 序列合并为完整 {@link
     * ToolCall} 列表。
     *
     * @param chunks 完整 chunk 序列
     * @return 累积后的工具调用列表
     */
    static List<ToolCall> aggregate(List<StreamChunk> chunks) {
        var acc = new ToolCallAccumulator();
        for (StreamChunk c : chunks) c.accept(acc);
        return acc.result();
    }

    /** 工具调用累积 visitor（按 id 分组拼接参数） */
    final class ToolCallAccumulator implements StreamChunkVisitor {
        /** 各 id 的参数累积器 */
        private final Map<String, StringBuilder> argsById = new HashMap<>();

        /** 各 id 的工具名（由 ToolCallStart 提供） */
        private final Map<String, String> namesById = new HashMap<>();

        /** 累积结果 */
        private final List<ToolCall> result = new ArrayList<>();

        @Override
        public void visitToolCallStart(ToolCallStart s) {
            namesById.put(s.id(), s.name());
            argsById.putIfAbsent(s.id(), new StringBuilder());
        }

        @Override
        public void visitToolCallDelta(ToolCallDelta d) {
            argsById.computeIfAbsent(d.id(), k -> new StringBuilder()).append(d.argumentsDelta());
        }

        @Override
        public void visitToolCallEnd(ToolCallEnd e) {
            String name = namesById.getOrDefault(e.id(), e.name());
            String args =
                    e.arguments() != null
                            ? e.arguments()
                            : argsById.getOrDefault(e.id(), new StringBuilder()).toString();
            result.add(new ToolCall(e.id(), name, args));
        }

        /**
         * @return 累积的工具调用列表
         */
        List<ToolCall> result() {
            return result;
        }
    }
}
