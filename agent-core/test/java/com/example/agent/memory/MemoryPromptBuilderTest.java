package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class MemoryPromptBuilderTest {
    @TempDir Path tmp;

    @Test
    void includesIndexContent() throws Exception {
        var dir = new MemoryDir(tmp.resolve("mem"));
        Files.writeString(dir.indexFile(), "# Memory Index\n\n- [Java 17](java17.md) — JDK 安装\n");
        var builder = new MemoryPromptBuilder(dir);
        String prompt = builder.build(null);
        assertTrue(prompt.contains("JDK 安装"));
        assertTrue(prompt.contains("Persistent Agent Memory"));
    }

    @Test
    void emptyIndexShowsMessage() {
        var dir = new MemoryDir(tmp.resolve("mem"));
        var builder = new MemoryPromptBuilder(dir);
        String prompt = builder.build(null);
        assertTrue(prompt.contains("currently empty"));
    }

    @Test
    void saveInstructionsUseActualMemoryDir() {
        var dir = new MemoryDir(tmp.resolve("mem"));
        var builder = new MemoryPromptBuilder(dir);
        String prompt = builder.build(null);
        assertFalse(prompt.contains("~/.agent-demo/memory/<name>.md"));
        assertTrue(prompt.contains(dir.dir().toString() + "/<name>.md"));
    }
}
