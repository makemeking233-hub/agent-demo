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
                        MemoryEntry.of("Java 17 安装", "如何使用 Homebrew 安装 JDK 17", "java17.md"),
                        MemoryEntry.of("Python 装饰器", "functools wraps 用法", "py-decor.md"));
        var recall = new MemoryRecall();
        var result = recall.recall("如何安装 Java 17", entries, 5, 0.3);
        assertEquals(1, result.size());
        assertEquals("java17.md", result.get(0).filename());
    }

    @Test
    void excludesBelowThreshold() {
        var entries = List.of(MemoryEntry.of("Rust 所有权", "Rust ownership borrow checker", "rust.md"));
        var recall = new MemoryRecall();
        var result = recall.recall("Java 多线程", entries, 5, 0.3);
        assertTrue(result.isEmpty());
    }

    @Test
    void capsAtMaxRecall() {
        var entries =
                List.of(
                        MemoryEntry.of("Java A", "Java 内容", "a.md"),
                        MemoryEntry.of("Java B", "Java 内容", "b.md"),
                        MemoryEntry.of("Java C", "Java 内容", "c.md"));
        var recall = new MemoryRecall();
        var result = recall.recall("Java", entries, 2, 0.3);
        assertEquals(2, result.size());
    }

    @Test
    void recallLimitedToScopeDoesNotMixAcrossScopes() {
        var userJava = new MemoryEntry("Java 全局", "Java 约定", "g.md", MemoryScope.USER);
        var projJava = new MemoryEntry("Java 项目", "项目 Java 踩坑", "p.md", MemoryScope.PROJECT);
        var recall = new MemoryRecall();
        // 限定 USER 召回：只命中 USER 条目
        var userOnly = recall.recall("Java", List.of(userJava, projJava), 5, 0.3, MemoryScope.USER);
        assertEquals(1, userOnly.size());
        assertEquals("g.md", userOnly.get(0).filename());
        // 限定 PROJECT 召回：只命中 PROJECT 条目
        var projOnly = recall.recall("Java", List.of(userJava, projJava), 5, 0.3, MemoryScope.PROJECT);
        assertEquals(1, projOnly.size());
        assertEquals("p.md", projOnly.get(0).filename());
    }

    @Test
    void recallWithoutScopeCoversAllScopes() {
        var userJava = new MemoryEntry("Java 全局", "Java 约定", "g.md", MemoryScope.USER);
        var projJava = new MemoryEntry("Java 项目", "项目 Java 踩坑", "p.md", MemoryScope.PROJECT);
        var recall = new MemoryRecall();
        var all = recall.recall("Java", List.of(userJava, projJava), 5, 0.3, null);
        assertEquals(2, all.size());
    }
}
