/**
 * AnthropicProvider reasoning / thinking blocks 单测（add-reasoning-thinking-streaming）。
 */
package com.example.agent.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.StreamChunk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicProviderTest {

    @Test
    void thinkingModelRequestIncludesThinkingField() {
        AnthropicProvider p = new AnthropicProvider("test-key");
        ChatRequest req =
                new ChatRequest(
                        "claude-opus-4-20250514",
                        "you are helpful",
                        List.of(new Message.User("hi")),
                        List.of(),
                        1.0,
                        4096,
                        Map.of());
        String body = p.buildRequestBody(req);
        assertThat(body).contains("\"thinking\":{");
        assertThat(body).contains("\"type\":\"enabled\"");
        assertThat(body).contains("\"budget_tokens\":4096");
    }

    @Test
    void nonThinkingModelRequestHasNoThinkingField() {
        AnthropicProvider p = new AnthropicProvider("test-key");
        ChatRequest req =
                new ChatRequest(
                        "claude-3-5-sonnet-20241022",
                        null,
                        List.of(new Message.User("hi")),
                        List.of(),
                        1.0,
                        4096,
                        Map.of());
        String body = p.buildRequestBody(req);
        assertThat(body).doesNotContain("\"thinking\"");
    }

    @Test
    void sseLineWithThinkingDeltaProducesThinkingChunk() {
        AnthropicProvider p = new AnthropicProvider("test-key");
        String sse =
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"text\":\"我先思考\"}}";
        StreamChunk chunk = p.parseSseLine(sse);
        assertThat(chunk).isInstanceOf(StreamChunk.ThinkingDelta.class);
        assertThat(((StreamChunk.ThinkingDelta) chunk).text()).isEqualTo("我先思考");
    }

    @Test
    void sseLineWithTextDeltaProducesTextChunk() {
        AnthropicProvider p = new AnthropicProvider("test-key");
        String sse =
                "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"答案\"}}";
        StreamChunk chunk = p.parseSseLine(sse);
        assertThat(chunk).isInstanceOf(StreamChunk.TextDelta.class);
        assertThat(((StreamChunk.TextDelta) chunk).text()).isEqualTo("答案");
    }

    @Test
    void sseMessageStopProducesFinished() {
        AnthropicProvider p = new AnthropicProvider("test-key");
        String sse = "data: {\"type\":\"message_stop\"}";
        StreamChunk chunk = p.parseSseLine(sse);
        assertThat(chunk).isInstanceOf(StreamChunk.Finished.class);
        assertThat(((StreamChunk.Finished) chunk).reason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void sseEventLineIsIgnored() {
        // Anthropic SSE 形如 "event: content_block_delta\ndata: {...}"
        AnthropicProvider p = new AnthropicProvider("test-key");
        assertThat(p.parseSseLine("event: content_block_delta")).isNull();
    }

    @Test
    void isThinkingModelDetectsOpusAndSonnet() {
        assertThat(AnthropicProvider.isThinkingModel("claude-opus-4-20250514")).isTrue();
        assertThat(AnthropicProvider.isThinkingModel("claude-sonnet-4-20250514")).isTrue();
        assertThat(AnthropicProvider.isThinkingModel("claude-3-7-sonnet-20250219")).isTrue();
        assertThat(AnthropicProvider.isThinkingModel("claude-3-5-sonnet-20241022")).isFalse();
        assertThat(AnthropicProvider.isThinkingModel(null)).isFalse();
    }
}