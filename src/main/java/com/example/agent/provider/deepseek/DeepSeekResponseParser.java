package com.example.agent.provider.deepseek;

import com.example.agent.llm.FinishReason;
import com.example.agent.llm.StreamChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;

/**
 * DeepSeek SSE 响应解析（OpenAI 兼容协议，pipeline 模式）。
 *
 * <p>职责单一：把单行 SSE payload 解析为一个 {@link StreamChunk}。 请求体构造由 {@link DeepSeekRequestMapper} 负责。
 *
 * <p>采用 pipeline 模式：每种 chunk 类型由独立的 {@link SsePayloadParser} 解析； parseSseLine 按优先级顺序遍历，第一个非 empty
 * 结果胜出。新增 chunk 类型只需新增一个 parser。
 */
public class DeepSeekResponseParser {
  /** JSON 解析器（SSE payload → JsonNode） */
  private final ObjectMapper json = new ObjectMapper();

  /** Parser pipeline（按优先级排序；第一个命中者胜出） */
  private final List<SsePayloadParser> parsers =
      List.of(
          new ChoiceContentParser(),
          new ChoiceToolCallParser(),
          new ChoiceFinishReasonParser(),
          new TopLevelUsageParser());

  /**
   * 解析单行 SSE data。
   *
   * @param line "data: {...}" 或 "data: [DONE]" 行
   * @return 解析出的 chunk，{@code [DONE]} / 空行 / 非 data 行返回 empty
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

  /** Parser 单元接口（pipeline 节点） */
  interface SsePayloadParser {
    /**
     * 尝试从 root 解析一个 chunk；不适用时返回 empty（pipeline 继续尝试下一个 parser）。
     *
     * @param root 已解析的 JSON 根节点
     * @return 解析出的 chunk；不适用时返回 empty
     */
    Optional<StreamChunk> parse(JsonNode root);
  }

  /** 解析 choices[0].delta.content → TextDelta */
  static final class ChoiceContentParser implements SsePayloadParser {
    @Override
    public Optional<StreamChunk> parse(JsonNode root) {
      JsonNode content = firstChoiceDeltaContent(root);
      if (content == null) return Optional.empty();
      return Optional.of(new StreamChunk.TextDelta(content.asText()));
    }
  }

  /** 解析 choices[0].delta.tool_calls[0] → ToolCallEnd */
  static final class ChoiceToolCallParser implements SsePayloadParser {
    @Override
    public Optional<StreamChunk> parse(JsonNode root) {
      JsonNode choices = root.path("choices");
      if (!choices.isArray() || choices.isEmpty()) return Optional.empty();
      JsonNode delta = choices.get(0).path("delta");
      if (!delta.has("tool_calls")) return Optional.empty();
      JsonNode tc = delta.get("tool_calls").get(0);
      return Optional.of(
          new StreamChunk.ToolCallEnd(
              tc.path("id").asText(),
              tc.path("function").path("name").asText(),
              tc.path("function").path("arguments").asText("{}")));
    }
  }

  /** 解析 choices[0].finish_reason + usage → Finished */
  static final class ChoiceFinishReasonParser implements SsePayloadParser {
    @Override
    public Optional<StreamChunk> parse(JsonNode root) {
      JsonNode choices = root.path("choices");
      if (!choices.isArray() || choices.isEmpty()) return Optional.empty();
      JsonNode choice0 = choices.get(0);
      JsonNode fr = choice0.path("finish_reason");
      if (fr.isNull() || fr.asText().isEmpty()) return Optional.empty();
      return Optional.of(new StreamChunk.Finished(toFinishReason(fr.asText()), parseUsage(root)));
    }
  }

  /** 顶层 usage（choices 为空但 SSE 流最后一块仍带 usage） */
  static final class TopLevelUsageParser implements SsePayloadParser {
    @Override
    public Optional<StreamChunk> parse(JsonNode root) {
      StreamChunk.Usage usage = parseUsage(root);
      return usage == null
          ? Optional.empty()
          : Optional.of(new StreamChunk.Usage(usage.promptTokens(), usage.completionTokens()));
    }
  }

  /** 提取 choices[0].delta.content（非 null 时返回；否则 null） */
  private static JsonNode firstChoiceDeltaContent(JsonNode root) {
    JsonNode choices = root.path("choices");
    if (!choices.isArray() || choices.isEmpty()) return null;
    JsonNode delta = choices.get(0).path("delta");
    if (delta.has("content") && !delta.get("content").isNull()) {
      return delta.get("content");
    }
    return null;
  }

  /** 共用：从 root 节点读 usage 字段 */
  private static StreamChunk.Usage parseUsage(JsonNode root) {
    if (!root.has("usage") || root.get("usage").isNull()) return null;
    JsonNode u = root.get("usage");
    return new StreamChunk.Usage(
        u.path("prompt_tokens").asInt(0), u.path("completion_tokens").asInt(0));
  }

  /** wire format finish_reason 字符串 → 内部枚举 */
  private static FinishReason toFinishReason(String s) {
    return switch (s) {
      case "stop" -> FinishReason.STOP;
      case "tool_calls" -> FinishReason.TOOL_CALLS;
      case "length" -> FinishReason.LENGTH;
      default -> FinishReason.ERROR;
    };
  }
}