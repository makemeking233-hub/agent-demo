package com.example.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;

class StreamChunkAggregateTest {
    @Test
    void aggregatesToolCalls() {
        List<StreamChunk> chunks =
                List.of(
                        new StreamChunk.ToolCallStart("1", "ReadFile", null),
                        new StreamChunk.ToolCallDelta("1", "{\"path\":"),
                        new StreamChunk.ToolCallDelta("1", "\"/tmp\"}"),
                        new StreamChunk.ToolCallEnd("1", "ReadFile", null));
        List<ToolCall> calls = StreamChunk.aggregate(chunks);
        assertEquals(1, calls.size());
        assertEquals("ReadFile", calls.get(0).name());
        assertEquals("{\"path\":\"/tmp\"}", calls.get(0).argumentsJson());
    }

    @Test
    void usesEndArgumentsWhenProvided() {
        List<StreamChunk> chunks =
                List.of(
                        new StreamChunk.ToolCallStart("1", "WriteFile", null),
                        new StreamChunk.ToolCallDelta("1", "{\"partial\":"),
                        new StreamChunk.ToolCallEnd("1", "WriteFile", "{\"complete\":true}"));
        List<ToolCall> calls = StreamChunk.aggregate(chunks);
        assertEquals(1, calls.size());
        assertEquals("{\"complete\":true}", calls.get(0).argumentsJson());
    }

    @Test
    void emptyChunksReturnsEmpty() {
        assertEquals(0, StreamChunk.aggregate(List.of()).size());
    }

    @Test
    void aggregatesIncrementalToolCallsWithoutIds() {
        // OpenAI 标准增量流：Start + 多个无 id 的 Delta + Finished(TOOL_CALLS) 收尾
        List<StreamChunk> chunks =
                List.of(
                        new StreamChunk.ToolCallStart("call_1", "ReadFile", null),
                        new StreamChunk.ToolCallDelta("", "{\"path\":"),
                        new StreamChunk.ToolCallDelta("", "\"/tmp/a.txt\"}"),
                        new StreamChunk.Finished(FinishReason.TOOL_CALLS, null));
        List<ToolCall> calls = StreamChunk.aggregate(chunks);
        assertEquals(1, calls.size());
        assertEquals("ReadFile", calls.get(0).name());
        assertEquals("{\"path\":\"/tmp/a.txt\"}", calls.get(0).argumentsJson());
    }

    @Test
    void startCarryingFullArgumentsYieldsSingleCall() {
        // 一次性完整参数场景：Start 携带完整 arguments，无 Delta，Finished 收尾
        List<StreamChunk> chunks =
                List.of(
                        new StreamChunk.ToolCallStart("call_1", "ReadFile", "{\"path\":\"/tmp/a.txt\"}"),
                        new StreamChunk.Finished(FinishReason.TOOL_CALLS, null));
        List<ToolCall> calls = StreamChunk.aggregate(chunks);
        assertEquals(1, calls.size());
        assertEquals("ReadFile", calls.get(0).name());
        assertEquals("{\"path\":\"/tmp/a.txt\"}", calls.get(0).argumentsJson());
    }
}
