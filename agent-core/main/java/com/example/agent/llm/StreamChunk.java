package com.example.agent.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /** 工具调用开始（携带 id + name + 首个参数增量，增量可为空） */
    record ToolCallStart(String id, String name, String argumentsDelta) implements StreamChunk {
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
     * 工具调用累积器（按 id 分组，无 id 的增量按出现顺序归入最近一组）。
     *
     * <p>兼容两种上游格式：
     *
     * <ul>
     *   <li>一次性完整参数：单个 {@link ToolCallEnd} 携带完整 arguments（DeepSeek 默认）
     *   <li>OpenAI 标准增量流：{@link ToolCallStart} + 多个无 id 的 {@link ToolCallDelta}，
     *       由 {@link FinishReason#TOOL_CALLS} 的 {@link Finished} 收尾
     * </ul>
     *
     * @param chunks 完整 chunk 序列
     * @return 累积后的工具调用列表
     */
    static List<ToolCall> aggregate(List<StreamChunk> chunks) {
        var acc = new ToolCallAccumulator();
        for (StreamChunk c : chunks) c.accept(acc);
        return acc.result();
    }

    /** 工具调用累积 visitor（有序归组；无 id 的增量追加到最近一组） */
    final class ToolCallAccumulator implements StreamChunkVisitor {
        /** 各 id 的累积组（插入序 = 出现序） */
        private final Map<String, Group> groupsById = new LinkedHashMap<>();

        /** 组出现顺序（用于无 id 增量归组） */
        private final List<Group> order = new ArrayList<>();

        /** 累积结果 */
        private final List<ToolCall> result = new ArrayList<>();

        /** 单个工具调用的累积状态 */
        private static final class Group {
            final String id;
            String name;
            final StringBuilder args = new StringBuilder();
            boolean closed;

            Group(String id) {
                this.id = id;
            }
        }

        @Override
        public void visitToolCallStart(ToolCallStart s) {
            Group g = group(s.id());
            if (s.name() != null && !s.name().isEmpty()) g.name = s.name();
            // 首个 chunk 可能已携带参数增量（一次性完整参数场景）
            if (s.argumentsDelta() != null && !s.argumentsDelta().isEmpty()) {
                g.args.append(s.argumentsDelta());
            }
        }

        @Override
        public void visitToolCallDelta(ToolCallDelta d) {
            if (d.argumentsDelta() == null || d.argumentsDelta().isEmpty()) return;
            lastGroup(d.id()).args.append(d.argumentsDelta());
        }

        @Override
        public void visitToolCallEnd(ToolCallEnd e) {
            Group g = group(e.id());
            if (e.name() != null && !e.name().isEmpty()) g.name = e.name();
            if (e.arguments() != null && !e.arguments().isEmpty()) {
                // 完整参数（一次性返回）：覆盖累积的增量
                g.args.setLength(0);
                g.args.append(e.arguments());
            }
            flush(g);
        }

        @Override
        public void visitFinished(Finished f) {
            if (f.reason() == FinishReason.TOOL_CALLS) flushAll();
        }

        /**
         * @return 累积的工具调用列表（未收尾的组在此兜底收尾）
         */
        List<ToolCall> result() {
            flushAll();
            return result;
        }

        /** 按 id 取组（不存在则新建并记录顺序） */
        private Group group(String id) {
            Group g = groupsById.get(id);
            if (g == null) {
                g = new Group(id);
                groupsById.put(id, g);
                order.add(g);
            }
            return g;
        }

        /** 取归组目标：id 非空按 id；为空（OpenAI 增量流）归入最近一组 */
        private Group lastGroup(String id) {
            if (id != null && !id.isEmpty()) return group(id);
            if (order.isEmpty()) return group("");
            Group last = order.get(order.size() - 1);
            return last.closed ? group("") : last;
        }

        /** 收尾单个组（closed 的跳过） */
        private void flush(Group g) {
            if (g.closed) return;
            result.add(new ToolCall(g.id, g.name != null ? g.name : "", g.args.toString()));
            g.closed = true;
        }

        /** 收尾所有未完成组 */
        private void flushAll() {
            for (Group g : order) flush(g);
        }
    }
}
