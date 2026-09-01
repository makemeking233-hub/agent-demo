package com.example.agent.tools.websearch;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tavily 检索端点 provider（add-web-search-tool change）。
 *
 * <p>WebClient POST {@code https://api.tavily.com/search}，body {@code {api_key, query, max_results}}，
 * 解析 {@code results[]}（title / url / content→snippet / score）为来源，结果数受 {@code maxResults}
 * 控制。key 用 {@code TAVILY_API_KEY}；无 key / HTTP 失败时抛 {@link IllegalStateException}（上层
 * {@code WebSearchTool} 捕获转错误结果）。
 */
public class TavilyWebSearchProvider implements WebSearchProvider {
    private static final String DEFAULT_ENDPOINT = "https://api.tavily.com/search";

    private final String apiKey;
    private final WebClient client;
    private final URI endpoint;

    /** 默认端点构造。 */
    public TavilyWebSearchProvider(String apiKey) {
        this(apiKey, DEFAULT_ENDPOINT);
    }

    /** 指定端点构造（测试 / 自部署用）。 */
    public TavilyWebSearchProvider(String apiKey, String endpoint) {
        this.apiKey = apiKey;
        this.endpoint = URI.create((endpoint == null || endpoint.isBlank()) ? DEFAULT_ENDPOINT : endpoint);
        this.client = WebClient.builder().build();
    }

    @Override
    public WebSearchResult search(String query, int maxResults, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Tavily 搜索缺少 API key：请设置环境变量 TAVILY_API_KEY");
        }

        int max = maxResults > 0 ? maxResults : 5;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", apiKey);
        body.put("query", query);
        body.put("max_results", max);

        Mono<JsonNode> mono =
                client.post()
                        .uri(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class);
        if (timeout != null) mono = mono.timeout(timeout);
        JsonNode resp = mono.block();
        if (resp == null) {
            throw new IllegalStateException("Tavily 搜索无响应");
        }
        return mapResponse(resp, max);
    }

    /** 映射 {@code results[]} 到来源列表，并截断到 {@code maxResults}（超出时 {@code truncated=true}）。 */
    private WebSearchResult mapResponse(JsonNode resp, int maxResults) {
        List<Source> sources = new ArrayList<>();
        boolean truncated = false;
        JsonNode results = resp.path("results");
        if (results.isArray()) {
            for (JsonNode r : results) {
                if (maxResults > 0 && sources.size() >= maxResults) {
                    truncated = true;
                    break;
                }
                String url = r.path("url").asText("");
                if (url.isBlank()) continue;
                String title = r.path("title").asText("");
                String content = r.path("content").asText("");
                sources.add(new Source(url, title, content, ""));
            }
        }
        return new WebSearchResult(sources, truncated);
    }
}
