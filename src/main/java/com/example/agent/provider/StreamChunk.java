package com.example.agent.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 流式响应 chunk 类型（sealed interface，详见 design.md §6.1）。
 *
 * <p>每个 record 对应 DeepSeek SSE 流的一种事件类型：
 * <ul>
 *   <li>{@link TextDelta} - 增量文本</li>
 *   <li>{@link ToolCallStart} / {@link ToolCallDelta} / {@link ToolCallEnd} - 工具调用流</li>
 *   <li>{@link Usage} - token 计量</li>
 *   <li>{@link Finished} - 流结束（含 finish_reason + usage）</li>
 *   <li>{@link Error} - 流错误</li>
 * </ul>
 */
public sealed interface StreamChunk
        permits StreamChunk.TextDelta, StreamChunk.ToolCallStart,
                StreamChunk.ToolCallDelta, StreamChunk.ToolCallEnd,
                StreamChunk.Usage, StreamChunk.Finished, StreamChunk.Error {

    /** 增量文本 chunk */
    record TextDelta(String text) implements StreamChunk {}

    /** 工具调用开始（携带 id + name） */
    record ToolCallStart(String id, String name) implements StreamChunk {}

    /** 工具调用参数增量（流式 JSON 片段） */
    record ToolCallDelta(String id, String argumentsDelta) implements StreamChunk {}

    /** 工具调用结束（参数完整） */
    record ToolCallEnd(String id, String name, String arguments) implements StreamChunk {}

    /** token 计量（prompt + completion） */
    record Usage(int promptTokens, int completionTokens) implements StreamChunk {}

    /** 流结束（含 finish_reason 和可选 usage） */
    record Finished(FinishReason reason, Usage usage) implements StreamChunk {}

    /** 流错误（message + http status + cause） */
    record Error(String message, int httpStatus, Throwable cause) implements StreamChunk {}

    /**
     * 工具调用累积器（按 id 分组）。
     * <p>把 {@link ToolCallStart} / {@link ToolCallDelta} / {@link ToolCallEnd} 序列合并为完整 {@link ToolCall} 列表。
     * @param chunks 完整 chunk 序列
     * @return 累积后的工具调用列表
     */
    static List<ToolCall> aggregate(List<StreamChunk> chunks) {
        Map<String, StringBuilder> argsById = new HashMap<>();
        Map<String, String> namesById = new HashMap<>();
        List<ToolCall> result = new ArrayList<>();
        for (StreamChunk c : chunks) {
            if (c instanceof ToolCallStart s) {
                namesById.put(s.id(), s.name());
                argsById.putIfAbsent(s.id(), new StringBuilder());
            } else if (c instanceof ToolCallDelta d) {
                argsById.computeIfAbsent(d.id(), k -> new StringBuilder()).append(d.argumentsDelta());
            } else if (c instanceof ToolCallEnd e) {
                String name = namesById.getOrDefault(e.id(), e.name());
                String args = e.arguments() != null ? e.arguments() : argsById.getOrDefault(e.id(), new StringBuilder()).toString();
                result.add(new ToolCall(e.id(), name, args));
            }
        }
        return result;
    }
}