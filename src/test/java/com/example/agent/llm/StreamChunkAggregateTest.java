package com.example.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class StreamChunkAggregateTest {
  @Test
  void aggregatesToolCalls() {
    List<StreamChunk> chunks =
        List.of(
            new StreamChunk.ToolCallStart("1", "ReadFile"),
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
            new StreamChunk.ToolCallStart("1", "WriteFile"),
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
}
