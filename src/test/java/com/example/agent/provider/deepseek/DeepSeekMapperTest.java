package com.example.agent.provider.deepseek;

import com.example.agent.provider.ChatRequest;
import com.example.agent.provider.Message;
import com.example.agent.provider.StreamChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekMapperTest {
    private final DeepSeekMapper mapper = new DeepSeekMapper();

    @Test
    void requestBodyIncludesStreamOptions() {
        ChatRequest req = new ChatRequest(
            "deepseek-chat", "system", List.of(Message.user("hi")),
            List.of(), 1.0, 1000, Map.of());
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
        String sse = "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":50,\"total_tokens\":150}}";
        Optional<StreamChunk> opt = mapper.parseSseLine(sse);
        StreamChunk chunk = opt.orElseThrow();
        assertInstanceOf(StreamChunk.Usage.class, chunk);
        StreamChunk.Usage u = (StreamChunk.Usage) chunk;
        assertEquals(100, u.promptTokens());
        assertEquals(50, u.completionTokens());
    }

    @Test
    void ignoresDoneMarker() {
        assertTrue(mapper.parseSseLine("data: [DONE]").isEmpty());
    }

    @Test
    void ignoresNonDataLine() {
        assertTrue(mapper.parseSseLine("event: ping").isEmpty());
    }
}