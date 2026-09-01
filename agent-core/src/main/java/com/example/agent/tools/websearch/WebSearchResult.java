package com.example.agent.tools.websearch;

import java.util.List;

/**
 * 网络搜索结果（add-web-search-tool change）。
 *
 * @param sources   结构化来源列表（按 url 去重）
 * @param truncated 结果是否被截断（超过 maxResults 时）
 */
public record WebSearchResult(List<Source> sources, boolean truncated) {
}
