package com.example.agent.tools.websearch;

/**
 * 单条检索来源（add-web-search-tool change）。
 *
 * @param url         来源 URL
 * @param title       标题（可能为空）
 * @param snippet     摘要（可能为空）
 * @param publishedAt 发布日期（可能为空）
 */
public record Source(String url, String title, String snippet, String publishedAt) {
}
