package com.example.agent.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public sealed interface StreamChunk
        permits StreamChunk.TextDelta, StreamChunk.ToolCallStart,
                StreamChunk.ToolCallDelta, StreamChunk.ToolCallEnd,
                StreamChunk.Usage, StreamChunk.Finished, StreamChunk.Error {

    record TextDelta(String text) implements StreamChunk {}
    record ToolCallStart(String id, String name) implements StreamChunk {}
    record ToolCallDelta(String id, String argumentsDelta) implements StreamChunk {}
    record ToolCallEnd(String id, String name, String arguments) implements StreamChunk {}
    record Usage(int promptTokens, int completionTokens) implements StreamChunk {}
    record Finished(FinishReason reason, Usage usage) implements StreamChunk {}
    record Error(String message, int httpStatus, Throwable cause) implements StreamChunk {}

    /** 工具调用累积器（按 id 分组） */
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