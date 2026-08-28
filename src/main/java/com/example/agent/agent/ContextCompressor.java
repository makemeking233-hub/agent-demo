package com.example.agent.agent;

import com.example.agent.provider.ChatRequest;
import com.example.agent.provider.LlmProvider;
import com.example.agent.provider.StreamChunk;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 上下文压缩器（详见 design.md §8）。
 *
 * <p>触发：{@code hist.estimateTokens() >= threshold}（threshold = contextWindow - maxOutput - buffer）。
 *
 * <p>熔断：连续失败 {@code maxConsecutiveFailures} 次后抛 {@link CompactCircuitBrokenException}。
 *
 * <p>v0.1 简化：compact 返回 summary 文本并写入 history 头部（不真做消息坍缩；M4.2 是真坍缩）。 PTL fallback 在 v0.1 不实现。
 */
public class ContextCompressor {
  private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);

  /** summary 请求 temperature（低温度保证稳定摘要，详见 design.md §8.2） */
  private static final double SUMMARY_TEMPERATURE = 0.3;

  /** summary 输出 token 上限（与 summarize.txt 第 5 条约束一致） */
  private static final int SUMMARY_MAX_TOKENS = 2000;

  /** summary 失败的熔断阈值 */
  private static final int DEFAULT_MAX_FAILURES = 3;

  /** LLM Provider（调 summary 模型用） */
  private final LlmProvider provider;

  /** 提前压缩的 token buffer（threshold = contextWindow - maxOutput - buffer） */
  private final int autoCompactBuffer;

  /** 连续压缩失败熔断阈值（达到则抛 {@link CompactCircuitBrokenException}） */
  private final int maxConsecutiveFailures;

  /** summary 模型名（{@code null} 时复用主模型） */
  private final String summaryModel;

  /**
   * 构造上下文压缩器。
   *
   * @param provider LLM provider
   * @param autoCompactBuffer 提前压缩 buffer（tokens）
   * @param maxConsecutiveFailures 连续失败熔断阈值
   * @param summaryModel summary 模型名
   */
  public ContextCompressor(
      LlmProvider provider,
      int autoCompactBuffer,
      int maxConsecutiveFailures,
      String summaryModel) {
    this.provider = provider;
    this.autoCompactBuffer = autoCompactBuffer;
    this.maxConsecutiveFailures = maxConsecutiveFailures;
    this.summaryModel = summaryModel;
  }

  /**
   * 按需压缩：超过 threshold 触发 {@link #compact}；连续失败达阈值则熔断。
   *
   * @param hist 当前消息历史
   * @return 压缩后（或原样）的 {@link MessageHistory}
   */
  public Mono<MessageHistory> compactIfNeeded(MessageHistory hist) {
    int threshold = provider.contextWindow() - provider.maxOutputTokens() - autoCompactBuffer;
    if (hist.estimateTokens() < threshold) return Mono.just(hist);
    if (hist.consecutiveCompactFailures() >= maxConsecutiveFailures) {
      log.warn("compact circuit-broken after {} failures", hist.consecutiveCompactFailures());
      return Mono.error(new CompactCircuitBrokenException());
    }
    return compact(hist)
        .doOnSuccess(r -> hist.resetCompactFailures())
        .doOnError(
            e -> {
              hist.incrementCompactFailures();
              log.warn("compact failed", e);
            })
        .onErrorResume(this::ptlFallback);
  }

  /** 真坍缩：调 summary 模型，坍缩旧消息，重注入最近文件，保留 system + 最近 3 轮。 */
  private Mono<MessageHistory> compact(MessageHistory hist) {
    return requestSummary(hist)
        .map(summary -> collapseMessages(hist, summary))
        .doOnNext(
            compacted -> {
              compacted.prependSystemBoundaryMessage(summaryFrom(compacted));
              compacted.reinjectRecentFileContents(200);
            });
  }

  /**
   * 调 summary 模型生成历史摘要（流式拼接）。
   *
   * @param hist 待压缩的历史
   * @return summary 文本
   */
  private Mono<String> requestSummary(MessageHistory hist) {
    String prompt = loadPrompt().replace("[消息历史 JSONL]", serializeHistory(hist));
    ChatRequest req =
        new ChatRequest(
            summaryModel,
            prompt,
            List.of(),
            List.of(),
            SUMMARY_TEMPERATURE,
            SUMMARY_MAX_TOKENS,
            Map.of("stream_options", Map.of("include_usage", true)));

    return provider
        .streamChat(req)
        .filter(c -> c instanceof StreamChunk.TextDelta)
        .map(c -> ((StreamChunk.TextDelta) c).text())
        .collectList()
        .map(parts -> String.join("", parts));
  }

  /**
   * 从 classpath 加载 summary prompt 模板；缺失时回退到内置默认。
   *
   * @return prompt 模板（含 {@code [消息历史 JSONL]} 占位符）
   */
  private String loadPrompt() {
    String fallback = "请将以下对话压缩为摘要：\n\n[消息历史 JSONL]";
    String loaded = com.example.agent.util.PromptLoader.loadOrFallback("/prompts/summarize.txt", fallback);
    if (loaded.equals(fallback)) {
      log.debug("summary prompt 使用 fallback（classpath 资源不可用）");
    }
    return loaded;
  }

  /**
   * 把历史序列化为简单文本（{@code [role] content} 每行一条）。
   *
   * @param hist 待序列化的历史
   * @return 序列化字符串
   */
  private String serializeHistory(MessageHistory hist) {
    StringBuilder sb = new StringBuilder();
    for (var m : hist.all()) {
      sb.append("[")
          .append(m.role())
          .append("] ")
          .append(m.content() == null ? "" : m.content())
          .append("\n");
    }
    return sb.toString();
  }

  /**
   * 坍缩消息：保留 system + 最近 N 条，其余用 summary 替换。
   *
   * @param hist 原始历史
   * @param summary 摘要文本
   * @return 坍缩后的新历史
   */
  private MessageHistory collapseMessages(MessageHistory hist, String summary) {
    var all = hist.all();
    if (all.size() <= 6) return hist; // 不够多，不压缩
    var system = all.stream().filter(m -> m instanceof Message.System).toList();
    int recentStart = Math.max(0, all.size() - 6);
    var recent = all.subList(recentStart, all.size());

    List<Message> collapsed = new ArrayList<>();
    collapsed.add(new Message.System("[SUMMARY]\n" + summary));
    collapsed.addAll(system);
    for (var m : recent) {
      if (m instanceof Message.Assistant a && a.toolCalls() != null && !a.toolCalls().isEmpty()) {
        collapsed.add(new Message.Assistant("- **做了什么**: " + a.content(), a.toolCalls()));
      } else if (m instanceof Message.ToolResult tr) {
        collapsed.add(new Message.Assistant("- **结果**: " + tr.content(), null));
      } else {
        collapsed.add(m);
      }
    }

    MessageHistory newHist = new MessageHistory(new com.example.agent.provider.TokenEstimator());
    collapsed.forEach(newHist::append);
    return newHist;
  }

  /**
   * 从 history 头部提取以 {@code [SUMMARY]} 开头的 system 消息内容。
   *
   * @param hist 已坍缩的历史
   * @return summary 文本；未找到时返回空串
   */
  private String summaryFrom(MessageHistory hist) {
    Optional<Message> first =
        hist.all().stream()
            .filter(m -> m instanceof Message.System s && s.content().startsWith("[SUMMARY]"))
            .findFirst();
    return first.map(m -> ((Message.System) m).content()).orElse("");
  }

  /**
   * v0.1 PTL fallback 占位（仅记日志后继续抛原异常）。
   *
   * @param e 原始错误
   * @return 始终返回 {@code Mono.error(e)}
   */
  private Mono<MessageHistory> ptlFallback(Throwable e) {
    log.warn("PTL fallback not implemented in v0.1: {}", e.toString());
    return Mono.error(e);
  }

  /**
   * @return 调试用字符串（含 buffer / maxFailures / summaryModel）
   */
  @Override
  public String toString() {
    return "ContextCompressor{buffer="
        + autoCompactBuffer
        + ", maxFailures="
        + maxConsecutiveFailures
        + ", summaryModel="
        + summaryModel
        + "}";
  }
}
