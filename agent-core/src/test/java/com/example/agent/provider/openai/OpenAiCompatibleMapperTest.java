package com.example.agent.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.StreamChunk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class OpenAiCompatibleMapperTest {
    private final OpenAiCompatibleMapper mapper = new OpenAiCompatibleMapper();

    @Test
    void requestBodyIncludesStreamOptions() {
        ChatRequest req =
                new ChatRequest(
                        "deepseek-chat",
                        "system",
                        List.of(new Message.User("hi")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());
        Map<String, Object> body = mapper.toRequestBody(req);

        assertTrue(body.containsKey("stream_options"));
        @SuppressWarnings("unchecked")
        Map<String, Object> opts = (Map<String, Object>) body.get("stream_options");
        assertEquals(Boolean.TRUE, opts.get("include_usage"));
        assertEquals(true, body.get("stream"));
    }

    @Test
    void parsesTextDeltaChunk() {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}";
        Optional<StreamChunk> opt = mapper.parseSseLine(sse);
        StreamChunk chunk = opt.orElseThrow();
        assertInstanceOf(StreamChunk.TextDelta.class, chunk);
        assertEquals("Hello", ((StreamChunk.TextDelta) chunk).text());
    }

    @Test
    void parsesUsageChunk() {
        String sse =
                "data:"
                    + " {\"choices\":[{\"delta\":{}}],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3}}";
        Optional<StreamChunk> opt = mapper.parseSseLine(sse);
        StreamChunk chunk = opt.orElseThrow();
        assertInstanceOf(StreamChunk.Usage.class, chunk);
        StreamChunk.Usage usage = (StreamChunk.Usage) chunk;
        assertEquals(7, usage.promptTokens());
        assertEquals(3, usage.completionTokens());
    }

    @Test
    void parsesFinishReasonChunk() {
        String sse =
                "data:"
                    + " {\"choices\":[{\"finish_reason\":\"stop\",\"delta\":{}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2}}";
        Optional<StreamChunk> opt = mapper.parseSseLine(sse);
        StreamChunk chunk = opt.orElseThrow();
        assertInstanceOf(StreamChunk.Finished.class, chunk);
        StreamChunk.Finished finished = (StreamChunk.Finished) chunk;
        assertEquals(FinishReason.STOP, finished.reason());
    }

    @Test
    void doneLineReturnsEmpty() {
        assertTrue(mapper.parseSseLine("data: [DONE]").isEmpty());
    }

    @Test
    void parsesToolCallStartChunk() {
        String sse =
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\","
                    + "\"type\":\"function\",\"function\":{\"name\":\"ReadFile\",\"arguments\":"
                    + "\"{\\\"path\\\":\\\"/tmp/a.txt\\\"}\"}}]}}]}";
        StreamChunk chunk = mapper.parseSseLine(sse).orElseThrow();
        assertInstanceOf(StreamChunk.ToolCallStart.class, chunk);
        StreamChunk.ToolCallStart s = (StreamChunk.ToolCallStart) chunk;
        assertEquals("call_1", s.id());
        assertEquals("ReadFile", s.name());
        // 一次性完整参数场景：完整 arguments 随 Start 携带
        assertEquals("{\"path\":\"/tmp/a.txt\"}", s.argumentsDelta());
    }

    @Test
    void parsesIncrementalToolCallDeltaChunk() {
        // OpenAI 标准增量流：后续 chunk 无 id，仅携带 arguments 增量
        String sse =
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":"
                    + "{\"arguments\":\"{\\\"path\\\":\\\"/tmp/\"}}]}}]}";
        StreamChunk chunk = mapper.parseSseLine(sse).orElseThrow();
        assertInstanceOf(StreamChunk.ToolCallDelta.class, chunk);
        StreamChunk.ToolCallDelta d = (StreamChunk.ToolCallDelta) chunk;
        assertEquals("", d.id());
        assertEquals("{\"path\":\"/tmp/", d.argumentsDelta());
    }

    @Test
    void ignoresToolCallChunkWithoutIdAndArguments() {
        String sse =
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                    + "\"function\":{}}]}}]}";
        assertTrue(mapper.parseSseLine(sse).isEmpty());
    }

    // ===== add-reasoning-thinking-streaming =====

    @Test
    void o1RequestIncludesReasoningEffort() {
        ChatRequest req =
                new ChatRequest(
                        "o1-preview",
                        null,
                        List.of(new Message.User("hi")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());
        Map<String, Object> body = mapper.toRequestBody(req);
        assertEquals("medium", body.get("reasoning_effort"));
    }

    @Test
    void o3RequestIncludesReasoningEffort() {
        ChatRequest req =
                new ChatRequest(
                        "o3-mini",
                        null,
                        List.of(new Message.User("hi")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());
        Map<String, Object> body = mapper.toRequestBody(req);
        assertEquals("medium", body.get("reasoning_effort"));
    }

    @Test
    void nonReasonerRequestHasNoReasoningEffort() {
        ChatRequest req =
                new ChatRequest(
                        "deepseek-chat",
                        null,
                        List.of(new Message.User("hi")),
                        List.of(),
                        1.0,
                        1000,
                        Map.of());
        Map<String, Object> body = mapper.toRequestBody(req);
        assertTrue(!body.containsKey("reasoning_effort"));
    }

    @Test
    void customReasoningEffortOverridesDefault() {
        Map<String, Object> extra = new java.util.HashMap<>();
        extra.put("reasoning_effort", "high");
        ChatRequest req =
                new ChatRequest(
                        "o1-preview",
                        null,
                        List.of(new Message.User("hi")),
                        List.of(),
                        1.0,
                        1000,
                        extra);
        Map<String, Object> body = mapper.toRequestBody(req);
        assertEquals("high", body.get("reasoning_effort"));
    }

    @Test
    void sseLineWithReasoningContentProducesThinkingDelta() {
        String sse = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"我先思考\"}}]}";
        Optional<StreamChunk> chunk = mapper.parseSseLine(sse);
        assertTrue(chunk.isPresent());
        assertInstanceOf(StreamChunk.ThinkingDelta.class, chunk.get());
        assertEquals("我先思考", ((StreamChunk.ThinkingDelta) chunk.get()).text());
    }

    @Test
    void sseLineWithReasoningAndContentProducesThinkingDeltaOnly() {
        // reasoning + content 同时出现：parser pipeline 第一个命中者（reasoning）胜出
        String sse =
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"思考\",\"content\":\"答案\"}}]}";
        Optional<StreamChunk> chunk = mapper.parseSseLine(sse);
        assertInstanceOf(StreamChunk.ThinkingDelta.class, chunk.get());
    }

    @Test
    void o1UsageIncludesReasoningTokens() {
        String sse =
                "data: {\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,"
                    + "\"completion_tokens_details\":{\"reasoning_tokens\":15}}}";
        Optional<StreamChunk> chunk = mapper.parseSseLine(sse);
        assertTrue(chunk.isPresent());
        assertInstanceOf(StreamChunk.Usage.class, chunk.get());
        StreamChunk.Usage usage = (StreamChunk.Usage) chunk.get();
        assertEquals(10, usage.promptTokens());
        assertEquals(20, usage.completionTokens());
        assertEquals(15, usage.reasoningTokens());
    }
}

