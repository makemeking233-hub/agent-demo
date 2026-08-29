package com.example.agent.provider.openai;

import com.example.agent.core.Message;
import com.example.agent.llm.ChatRequest;
import com.example.agent.llm.FinishReason;
import com.example.agent.llm.StreamChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI 兼容协议 Mapper（DeepSeek / MiniMax / OpenAI / 任何遵循 OpenAI chat-completion 协议的服务）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #toRequestBody(ChatRequest)}：把内部 {@link ChatRequest} 序列化为 HTTP JSON body
 *   <li>{@link #parseSseLine(String)}：把单行 SSE data 解析为 {@link StreamChunk}
 * </ul>
 *
 * <p>强制规则（design.md §7.1）：每个请求体必须带 {@code stream_options.include_usage=true}， 否则 {@code
 * usage.prompt_tokens} 永远为 null，压缩触发器失效。
 */
public class OpenAiCompatibleMapper {

    /**
     * 强制字段：开启 usage 透传
     */
    private static final String STREAM_OPTIONS_KEY = "stream_options";

    /**
     * {@code stream_options.include_usage} 子键名
     */
    private static final String INCLUDE_USAGE_KEY = "include_usage";

    /**
     * JSON 解析器（SSE payload → JsonNode）
     */
    private final ObjectMapper json = new ObjectMapper();

    /**
     * SSE parser pipeline（按优先级排序；第一个命中者胜出）
     */
    private final List<SsePayloadParser> parsers =
            List.of(
                    new ChoiceContentParser(),
                    new ChoiceToolCallParser(),
                    new ChoiceFinishReasonParser(),
                    new TopLevelUsageParser());

    /**
     * 构造 OpenAI 格式 chat completion 请求体（含 {@code stream_options.include_usage=true}）。
     *
     * @param req 聊天请求
     * @return OpenAI 兼容 API 请求体 Map
     */
    public Map<String, Object> toRequestBody(ChatRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.model());
        body.put("stream", true);
        body.put(STREAM_OPTIONS_KEY, Map.of(INCLUDE_USAGE_KEY, true));
        if (req.temperature() != null) body.put("temperature", req.temperature());
        if (req.maxTokens() != null) body.put("max_tokens", req.maxTokens());
        if (req.systemPrompt() != null && !req.systemPrompt().isEmpty()) {
            body.put("messages", mergeSystemPrompt(req));
        } else {
            body.put("messages", toMessageArray(req.messages()));
        }
        if (req.tools() != null && !req.tools().isEmpty()) {
            // OpenAI 标准 tools 格式：{"type":"function","function":{name,description,parameters}}
            // ToolSpec record 字段是 name/description/inputSchema，需要包装
            List<Map<String, Object>> tools = new ArrayList<>();
            for (com.example.agent.llm.ToolSpec spec : req.tools()) {
                tools.add(
                        Map.of(
                                "type",
                                "function",
                                "function",
                                Map.of(
                                        "name", spec.name(),
                                        "description", spec.description(),
                                        "parameters", spec.inputSchema())));
            }
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        if (req.extra() != null) body.putAll(req.extra());
        return body;
    }

    /**
     * 解析单行 SSE data。
     *
     * @param line "data: {...}" 或 "data: [DONE]" 行
     * @return 解析出的 chunk；{@code [DONE]} / 空行 / 非 data 行返回 empty
     */
    public Optional<StreamChunk> parseSseLine(String line) {
        if (!line.startsWith("data: ")) return Optional.empty();
        String payload = line.substring(6).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) return Optional.empty();
        try {
            JsonNode root = json.readTree(payload);
            for (SsePayloadParser p : parsers) {
                Optional<StreamChunk> chunk = p.parse(root);
                if (chunk.isPresent()) return chunk;
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new StreamChunk.Error("SSE 解析失败: " + e.getMessage(), 0, e));
        }
    }

    /**
     * 合并 system prompt 到 messages 数组头部
     */
    private List<Map<String, Object>> mergeSystemPrompt(ChatRequest req) {
        List<Map<String, Object>> arr = new ArrayList<>();
        arr.add(Map.of("role", "system", "content", req.systemPrompt()));
        arr.addAll(toMessageArray(req.messages()));
        return arr;
    }

    /**
     * 把内部 {@link Message} 列表转为 OpenAI 格式 Map 数组（通过 sealed {@code Message.toMap()} 多态）
     */
    private List<Map<String, Object>> toMessageArray(List<Message> messages) {
        List<Map<String, Object>> arr = new ArrayList<>();
        for (var m : messages) arr.add(m.toMap());
        return arr;
    }

    /**
     * Parser 单元接口（pipeline 节点；提供给子类扩展或覆盖）
     */
    public interface SsePayloadParser {
        /**
         * 尝试从 root 解析一个 chunk；不适用时返回 empty（pipeline 继续尝试下一个 parser）。
         *
         * @param root 已解析的 JSON 根节点
         * @return 解析出的 chunk；不适用时返回 empty
         */
        Optional<StreamChunk> parse(JsonNode root);
    }

    /**
     * 解析 choices[0].delta.content → TextDelta
     */
    static final class ChoiceContentParser implements SsePayloadParser {
        @Override
        public Optional<StreamChunk> parse(JsonNode root) {
            JsonNode content = firstChoiceDeltaContent(root);
            if (content == null) return Optional.empty();
            return Optional.of(new StreamChunk.TextDelta(content.asText()));
        }
    }

    /**
     * 解析 choices[0].delta.tool_calls[0] → ToolCallStart / ToolCallDelta
     *
     * <p>兼容两种上游格式：
     *
     * <ul>
     *   <li>一次性完整参数：首 chunk 携带 id + name + 完整 arguments（DeepSeek 默认），生成
     *       {@link ToolCallStart}（arguments 作为增量随 Start 携带）
     *   <li>OpenAI 标准增量流：首 chunk 只有 id + name，后续 chunk 无 id、仅携带 arguments
     *       增量，生成 {@link ToolCallDelta}；由 finish_reason=tool_calls 收尾
     * </ul>
     */
    static final class ChoiceToolCallParser implements SsePayloadParser {
        @Override
        public Optional<StreamChunk> parse(JsonNode root) {
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return Optional.empty();
            JsonNode delta = choices.get(0).path("delta");
            if (!delta.has("tool_calls")) return Optional.empty();
            JsonNode tc = delta.get("tool_calls").get(0);
            String id = tc.path("id").asText("");
            String name = tc.path("function").path("name").asText("");
            String args = tc.path("function").path("arguments").asText("");
            if (id.isEmpty() && args.isEmpty()) return Optional.empty();
            if (!id.isEmpty()) {
                // 首 chunk：携带 id（可能同时带 name 与首个参数增量）
                return Optional.of(new StreamChunk.ToolCallStart(id, name, args.isEmpty() ? null : args));
            }
            // 后续增量 chunk（OpenAI 标准无 id，仅 index + arguments 增量）
            return Optional.of(new StreamChunk.ToolCallDelta("", args));
        }
    }

    /**
     * 解析 choices[0].finish_reason + usage → Finished
     */
    static final class ChoiceFinishReasonParser implements SsePayloadParser {
        @Override
        public Optional<StreamChunk> parse(JsonNode root) {
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return Optional.empty();
            JsonNode choice0 = choices.get(0);
            JsonNode fr = choice0.path("finish_reason");
            if (fr.isNull() || fr.asText().isEmpty()) return Optional.empty();
            return Optional.of(
                    new StreamChunk.Finished(toFinishReason(fr.asText()), parseUsage(root)));
        }
    }

    /**
     * 顶层 usage（choices 为空但 SSE 流最后一块仍带 usage）
     */
    static final class TopLevelUsageParser implements SsePayloadParser {
        @Override
        public Optional<StreamChunk> parse(JsonNode root) {
            StreamChunk.Usage usage = parseUsage(root);
            return usage == null
                    ? Optional.empty()
                    : Optional.of(
                    new StreamChunk.Usage(usage.promptTokens(), usage.completionTokens()));
        }
    }

    /**
     * 提取 choices[0].delta.content（非 null 时返回；否则 null）
     */
    private static JsonNode firstChoiceDeltaContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return null;
        JsonNode delta = choices.get(0).path("delta");
        if (delta.has("content") && !delta.get("content").isNull()) {
            return delta.get("content");
        }
        return null;
    }

    /**
     * 从 root 节点读 usage 字段
     */
    private static StreamChunk.Usage parseUsage(JsonNode root) {
        if (!root.has("usage") || root.get("usage").isNull()) return null;
        JsonNode u = root.get("usage");
        return new StreamChunk.Usage(
                u.path("prompt_tokens").asInt(0), u.path("completion_tokens").asInt(0));
    }

    /**
     * wire format finish_reason 字符串 → 内部枚举
     */
    private static FinishReason toFinishReason(String s) {
        return switch (s) {
            case "stop" -> FinishReason.STOP;
            case "tool_calls" -> FinishReason.TOOL_CALLS;
            case "length" -> FinishReason.LENGTH;
            default -> FinishReason.ERROR;
        };
    }
}
