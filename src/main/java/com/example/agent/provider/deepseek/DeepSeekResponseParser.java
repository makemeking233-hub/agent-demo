package com.example.agent.provider.deepseek;

import com.example.agent.provider.FinishReason;
import com.example.agent.provider.StreamChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * DeepSeek SSE 响应解析（OpenAI 兼容协议）。
 *
 * <p>职责单一：把单行 SSE payload 解析为一个 {@link StreamChunk}。
 * 请求体构造由 {@link DeepSeekRequestMapper} 负责。
 */
public class DeepSeekResponseParser {
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 解析单行 SSE data。
     * @param line "data: {...}" 或 "data: [DONE]" 行
     * @return 解析出的 chunk，{@code [DONE]} / 空行 / 非 data 行返回 empty
     */
    public Optional<StreamChunk> parseSseLine(String line) {
        if (!line.startsWith("data: ")) return Optional.empty();
        String payload = line.substring(6).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) return Optional.empty();
        try {
            JsonNode root = json.readTree(payload);
            Optional<StreamChunk> fromChoice = parseFirstChoice(root);
            if (fromChoice.isPresent()) return fromChoice;
            return parseTopLevelUsage(root);
        } catch (Exception e) {
            return Optional.of(new StreamChunk.Error("SSE 解析失败: " + e.getMessage(), 0, e));
        }
    }

    /**
     * 从 choices[0] 解析一个 chunk。提取此方法以降低 parseSseLine 嵌套深度
     * （规范 14：嵌套 ≤ 4）。
     */
    private Optional<StreamChunk> parseFirstChoice(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return Optional.empty();
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
        return parseFinishReason(choice0, root);
    }

    /** 解析 finish_reason + usage（从 choices[0] 路径） */
    private Optional<StreamChunk> parseFinishReason(JsonNode choice0, JsonNode root) {
        JsonNode fr = choice0.path("finish_reason");
        if (fr.isNull() || fr.asText().isEmpty()) return Optional.empty();
        StreamChunk.Usage usage = parseUsage(root);
        return Optional.of(new StreamChunk.Finished(toFinishReason(fr.asText()), usage));
    }

    /** 顶层 usage chunk（choices 为空但 SSE 流最后一块仍带 usage） */
    private Optional<StreamChunk> parseTopLevelUsage(JsonNode root) {
        StreamChunk.Usage usage = parseUsage(root);
        return usage == null ? Optional.empty() : Optional.of(new StreamChunk.Usage(
            usage.promptTokens(), usage.completionTokens()));
    }

    /** 共用：从 root 节点读 usage 字段 */
    private StreamChunk.Usage parseUsage(JsonNode root) {
        if (!root.has("usage") || root.get("usage").isNull()) return null;
        JsonNode u = root.get("usage");
        return new StreamChunk.Usage(
            u.path("prompt_tokens").asInt(0),
            u.path("completion_tokens").asInt(0));
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