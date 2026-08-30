package com.example.agent.memory;

/**
 * 单条记忆 record（含作用域 scope）。
 *
 * <p>{@link MemoryScope} 标识该条记忆的来源层级：USER / PROJECT / LOCAL。跨 scope 的条目
 * 由 {@link MemoryIndex} / {@link MemoryRecall} 按 scope 区分处理。
 *
 * @param title       记忆标题（MEMORY.md 索引行 + 单条 .md 文件 H1）
 * @param description 一行描述（MEMORY.md 索引行用）
 * @param filename    文件名（相对 memory 目录，如 {@code "java17.md"}）
 * @param scope       记忆作用域（USER / PROJECT / LOCAL）
 */
public record MemoryEntry(String title, String description, String filename, MemoryScope scope) {

    /**
     * 以 USER scope 构造（默认全局记忆；用于未显式指定 scope 的简单场景）。
     *
     * @param title       记忆标题
     * @param description 一行描述
     * @param filename    文件名
     * @return USER scope 的 {@link MemoryEntry}
     */
    public static MemoryEntry of(String title, String description, String filename) {
        return new MemoryEntry(title, description, filename, MemoryScope.USER);
    }
}
