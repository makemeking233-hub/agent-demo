package com.example.agent.tools.websearch;

import java.time.Duration;

/**
 * 网络搜索 provider 接口（可插拔，add-web-search-tool change）。
 *
 * <p>实现（DeepSeek 原生搜索 / Tavily 检索端点）由 {@link WebSearchProviderFactory} 按配置选择。
 * 失败（无 key / HTTP 失败 / 超时 / 无结构化结果）时抛 {@link IllegalStateException}（或其子类），
 * 由上层 {@code WebSearchTool} 捕获并转为 {@code ToolResult.error}（Fail-Closed，不抛未捕获异常）。
 */
public interface WebSearchProvider {
    /**
     * 执行一次网络搜索。
     *
     * @param query      查询词（非空）
     * @param maxResults 期望的最大结果数（provider 可能截断）
     * @param timeout    超时（可为 {@code null} 表示不设显式超时）
     * @return 结构化搜索结果（按 url 去重）
     * @throws IllegalStateException 无凭据 / 调用失败 / 超时 / 未返回结构化结果时抛出
     */
    WebSearchResult search(String query, int maxResults, Duration timeout);
}
