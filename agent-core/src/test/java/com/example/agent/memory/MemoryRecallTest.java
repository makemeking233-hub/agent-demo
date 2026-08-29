package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class MemoryRecallTest {
    @Test
    void recallsByTokenOverlap() {
        var entries =
                List.of(
                        new MemoryEntry("Java 17 安装", "如何使用 Homebrew 安装 JDK 17", "java17.md"),
                        new MemoryEntry("Python 装饰器", "functools wraps 用法", "py-decor.md"));
        var recall = new MemoryRecall();
        var result = recall.recall("如何安装 Java 17", entries, 5, 0.3);
        assertEquals(1, result.size());
        assertEquals("java17.md", result.get(0).filename());
    }

    @Test
    void excludesBelowThreshold() {
        var entries =
                List.of(new MemoryEntry("Rust 所有权", "Rust ownership borrow checker", "rust.md"));
        var recall = new MemoryRecall();
        var result = recall.recall("Java 多线程", entries, 5, 0.3);
        assertTrue(result.isEmpty());
    }

    @Test
    void capsAtMaxRecall() {
        var entries =
                List.of(
                        new MemoryEntry("Java A", "Java 内容", "a.md"),
                        new MemoryEntry("Java B", "Java 内容", "b.md"),
                        new MemoryEntry("Java C", "Java 内容", "c.md"));
        var recall = new MemoryRecall();
        var result = recall.recall("Java", entries, 2, 0.3);
        assertEquals(2, result.size());
    }
}
