package com.example.agent.memory;

/**
 * 单条记忆 record。
 *
 * @param title 记忆标题（MEMORY.md 索引行 + 单条 .md 文件 H1）
 * @param description 一行描述（MEMORY.md 索引行用）
 * @param filename 文件名（相对 memory 目录，如 {@code "java17.md"}）
 */
public record MemoryEntry(String title, String description, String filename) {}
