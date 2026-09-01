package com.example.agent.tools.websearch;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DeepSeek 原生搜索 provider（add-web-search-tool change，复刻 DSH 实现）。
 *
 * <p>用 WebFlux WebClient POST {@code {baseURL}/messages}（Anthropic 兼容 Messages API），携带原生
 * {@code web_search_20250305} 服务器工具，由服务端执行搜索并返回结构化 {@code web_search_tool_result}
 * 块。基址默认 {@code https://api.deepseek.com/anthropic/v1}，<strong>不</strong>复用 LLM 的
 * chat-completions 基址（{@code https://api.deepseek.com}），仅复用 {@code DEEPSEEK_API_KEY}。
 *
 * <p><strong>严格模式</strong>：响应不含 {@code web_search_tool_result} 块时抛
 * {@link IllegalStateException}，不降级为文本抓取（不吃生成文本当答案）。
 */
public class DeepSeekWebSearchProvider implements WebSearchProvider {
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/anthropic/v1";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final WebClient client;
    private final URI endpoint;

    /** 便捷构造（默认基址 / 模型 / maxTokens）。 */
    public DeepSeekWebSearchProvider(String apiKey) {
        this(apiKey, null, null, -1);
    }

    /** 指定基址（测试 / 自部署用），模型与 maxTokens 取默认。 */
    public DeepSeekWebSearchProvider(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, null, -1);
    }

    /** 全参构造。 */
    public DeepSeekWebSearchProvider(String apiKey, String baseUrl, String model, int maxTokens) {
        this.apiKey = apiKey;
        String base = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        this.endpoint = URI.create(base + "/messages");
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.maxTokens = maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;
        this.client = WebClient.builder().build();
    }

    @Override
    public WebSearchResult search(String query, int maxResults, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek 搜索缺少 API key：请设置环境变量 DEEPSEEK_API_KEY");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", "Perform a web search for the query: " + query);
        body.put("messages", List.of(Map.of("role", "user", "content", query)));
        body.put("tools", List.of(Map.of("type", "web_search_20250305")));

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", API_VERSION);

        Mono<JsonNode> mono =
                client.post()
                        .uri(endpoint)
                        .headers(h -> h.putAll(headers))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class);
        if (timeout != null) mono = mono.timeout(timeout);
        JsonNode resp = mono.block();
        if (resp == null) {
            throw new IllegalStateException("DeepSeek 搜索无响应");
        }
        return mapResponse(resp);
    }

    /**
     * 把 Anthropic Messages 响应映射为规范化的 {@link WebSearchResult}。
     *
     * <p>遍历顶层 {@code content[]}：{@code web_search_tool_result} 块下的 {@code web_search_result}
     * 项提供 url / title / page_age；{@code text} 块的 {@code citations[]} 按 url 关联 snippet（首现优先）。
     * 结果按 url 去重，{@code truncated} 恒为 {@code false}（DeepSeek 端已截断）。
     */
    private WebSearchResult mapResponse(JsonNode response) {
        JsonNode blocks = response.path("content");
        if (!blocks.isArray()) {
            throw new IllegalStateException("DeepSeek 未返回 web_search_tool_result 块（可能未触发原生搜索）");
        }

        List<JsonNode> resultBlocks = new ArrayList<>();
        Map<String, String> snippets = new LinkedHashMap<>();
        for (JsonNode block : blocks) {
            String type = block.path("type").asText("");
            if ("web_search_tool_result".equals(type)) {
                resultBlocks.add(block);
            } else if ("text".equals(type)) {
                JsonNode citations = block.path("citations");
                if (citations.isArray()) {
                    for (JsonNode cite : citations) {
                        String url = cite.path("url").asText("");
                        String citedText = cite.path("cited_text").asText("");
                        if (!url.isBlank() && !citedText.isBlank() && !snippets.containsKey(url)) {
                            snippets.put(url, citedText);
                        }
                    }
                }
            }
        }

        if (resultBlocks.isEmpty()) {
            throw new IllegalStateException("DeepSeek 未返回 web_search_tool_result 块（可能未触发原生搜索）");
        }

        List<Source> sources = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode block : resultBlocks) {
            JsonNode items = block.path("content");
            if (!items.isArray()) continue;
            for (JsonNode item : items) {
                if (!"web_search_result".equals(item.path("type").asText(""))) continue;
                String url = item.path("url").asText("");
                if (url.isBlank() || seen.contains(url)) continue;
                seen.add(url);
                String title = item.path("title").asText("");
                String pageAge = item.path("page_age").asText("");
                String snippet = snippets.getOrDefault(url, "");
                sources.add(new Source(url, title, snippet, pageAge));
            }
        }
        return new WebSearchResult(sources, false);
    }
}
