package com.example.agent.provider.deepseek;

import com.example.agent.provider.ChatRequest;
import com.example.agent.provider.FinishReason;
import com.example.agent.provider.Message;
import com.example.agent.provider.StreamChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DeepSeekMapper {
    private final ObjectMapper json = new ObjectMapper();

    /** §7.1 强制 stream_options.include_usage=true */
    public Map<String, Object> toRequestBody(ChatRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.model());
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        if (req.temperature() != null) body.put("temperature", req.temperature());
        if (req.maxTokens() != null) body.put("max_tokens", req.maxTokens());
        if (req.systemPrompt() != null && !req.systemPrompt().isEmpty()) {
            body.put("messages", mergeSystemPrompt(req));
        } else {
            body.put("messages", toMessageArray(req.messages()));
        }
        if (req.tools() != null && !req.tools().isEmpty()) {
            body.put("tools", req.tools());
            body.put("tool_choice", "auto");
        }
        if (req.extra() != null) body.putAll(req.extra());
        return body;
    }

    public Optional<StreamChunk> parseSseLine(String line) {
        if (!line.startsWith("data: ")) return Optional.empty();
        String payload = line.substring(6).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) return Optional.empty();
        try {
            JsonNode root = json.readTree(payload);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode choice0 = choices.get(0);
                JsonNode delta = choice0.path("delta");
                if (delta.has("content") && !delta.get("content").isNull()) {
                    return Optional.of(new StreamChunk.TextDelta(delta.get("content").asText()));
                }
                if (delta.has("tool_calls")) {
                    JsonNode tc = delta.get("tool_calls").get(0);
                    return Optional.of(new StreamChunk.ToolCallEnd(
                        tc.path("id").asText(),
                        tc.path("function").path("name").asText(),
                        tc.path("function").path("arguments").asText("{}")));
                }
                JsonNode fr = choice0.path("finish_reason");
                if (!fr.isNull() && !fr.asText().isEmpty()) {
                    return Optional.of(new StreamChunk.Finished(toFinishReason(fr.asText()), null));
                }
            }
            if (root.has("usage") && !root.get("usage").isNull()) {
                JsonNode u = root.get("usage");
                return Optional.of(new StreamChunk.Usage(
                    u.path("prompt_tokens").asInt(0),
                    u.path("completion_tokens").asInt(0)));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new StreamChunk.Error("SSE 解析失败: " + e.getMessage(), 0, e));
        }
    }

    private List<Map<String, Object>> mergeSystemPrompt(ChatRequest req) {
        List<Map<String, Object>> arr = new ArrayList<>();
        arr.add(Map.of("role", "system", "content", req.systemPrompt()));
        arr.addAll(toMessageArray(req.messages()));
        return arr;
    }

    private List<Map<String, Object>> toMessageArray(List<Message> messages) {
        List<Map<String, Object>> arr = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", m.role());
            entry.put("content", m.content());
            arr.add(entry);
        }
        return arr;
    }

    private FinishReason toFinishReason(String s) {
        return switch (s) {
            case "stop" -> FinishReason.STOP;
            case "tool_calls" -> FinishReason.TOOL_CALLS;
            case "length" -> FinishReason.LENGTH;
            default -> FinishReason.ERROR;
        };
    }
}