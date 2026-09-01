package com.example.agent.tools;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.websearch.Source;
import com.example.agent.tools.websearch.WebSearchProvider;
import com.example.agent.tools.websearch.WebSearchResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 内置联网搜索工具（add-web-search-tool change）。
 *
 * <p>把选中的 {@link WebSearchProvider} 暴露为模型可调用的 {@code web_search} 工具：模型传入
 * {@code query}（可选 {@code maxResults}），工具调用 provider 检索并把结构化结果渲染为可读文本
 * （标题 + URL + 摘要 + 日期）注入上下文。
 *
 * <p>只读、无本地副作用（{@code checkPermissions=allow}）；provider 无 key / 调用失败 / 超时时返回带
 * 指引的错误结果（Fail-Closed），不抛未捕获异常。
 */
public class WebSearchTool implements Tool<WebSearchTool.Input, String> {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebSearchProvider provider;
    private final int maxResults;
    private final long timeoutMs;

    /**
     * 构造工具。
     *
     * @param provider   选中的搜索 provider
     * @param maxResults 默认结果数（模型未传 {@code maxResults} 时用）
     * @param timeoutMs  搜索超时（毫秒）
     */
    public WebSearchTool(WebSearchProvider provider, int maxResults, int timeoutMs) {
        this.provider = provider;
        this.maxResults = maxResults;
        this.timeoutMs = timeoutMs;
    }

    /** 工具输入（{@code maxResults} 可空 = 用构造注入的默认值）。 */
    public record Input(String query, Integer maxResults) {}

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "联网搜索，返回结构化结果（标题 / URL / 摘要 / 发布日期）。"
                + "当需要最新信息、实时数据或网络检索时使用；输入 query 为查询词。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "query", Map.of("type", "string", "description", "要搜索的查询词"),
                        "maxResults",
                                Map.of(
                                        "type",
                                        "integer",
                                        "description",
                                        "期望的最大结果数（可选，默认取配置值）")),
                "required",
                List.of("query"));
    }

    @Override
    public boolean isReadOnly(Input input) {
        return true;
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.READ;
    }

    @Override
    public PermissionDecision checkPermissions(Input input, ToolContext ctx) {
        return PermissionDecision.allow();
    }

    @Override
    public String renderUse(Input input) {
        return "web_search(\"" + (input == null || input.query() == null ? "" : input.query()) + "\")";
    }

    @Override
    public String renderResult(String output) {
        return output;
    }

    @Override
    public Input parseArguments(String argumentsJson) {
        try {
            JsonNode node = JSON.readTree(argumentsJson);
            String query = node.path("query").asText(null);
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query 不能为空");
            }
            JsonNode mr = node.path("maxResults");
            Integer maxResults = mr.isNumber() ? mr.asInt() : null;
            return new Input(query, maxResults);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("参数 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Mono<ToolResult<String>> execute(Input input, ToolContext ctx) {
        return Mono.fromCallable(
                () -> {
                    String query = input == null ? null : input.query();
                    if (query == null || query.isBlank()) {
                        return ToolResult.<String>error("web_search 需要非空查询词（query）");
                    }
                    int effectiveMax =
                            input.maxResults() != null && input.maxResults() > 0
                                    ? input.maxResults()
                                    : maxResults;
                    try {
                        WebSearchResult result =
                                provider.search(query, effectiveMax, Duration.ofMillis(timeoutMs));
                        return ToolResult.<String>ok(renderText(result));
                    } catch (Exception e) {
                        return ToolResult.<String>error(
                                "web_search 失败: "
                                        + e.getMessage()
                                        + "（请检查搜索 provider 的 API key 配置与网络连接）");
                    }
                });
    }

    /** 把结构化结果渲染为可读文本（每条 标题/URL/摘要/日期）。 */
    private String renderText(WebSearchResult result) {
        if (result == null || result.sources() == null || result.sources().isEmpty()) {
            return "（未找到相关结果）";
        }
        StringBuilder sb = new StringBuilder();
        for (Source s : result.sources()) {
            String title = s.title() == null || s.title().isBlank() ? s.url() : s.title();
            sb.append("- [").append(title).append("](").append(s.url()).append(")\n");
            if (s.snippet() != null && !s.snippet().isBlank()) {
                sb.append("  摘要: ").append(s.snippet()).append("\n");
            }
            if (s.publishedAt() != null && !s.publishedAt().isBlank()) {
                sb.append("  日期: ").append(s.publishedAt()).append("\n");
            }
        }
        if (result.truncated()) {
            sb.append("（结果已截断）");
        }
        return sb.toString().stripTrailing();
    }
}
