package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        // 单 scope 空索引时，scope 小节仍展示路径并标空
        assertTrue(prompt.contains("USER Scope"));
        assertTrue(prompt.contains("(empty)"));
    }

    @Test
    void saveInstructionsUseActualMemoryDir() {
        var dir = new MemoryDir(tmp.resolve("mem"));
        var builder = new MemoryPromptBuilder(dir);
        String prompt = builder.build(null);
        // 记忆段应包含实际目录路径（供模型写入时使用）
        assertTrue(prompt.contains(dir.dir().toString()));
        assertTrue(prompt.contains(dir.dir().toString() + "/<name>.md")
                || prompt.contains("<name>.md"));
    }

    @Test
    void multiScopeBuildMergesEachScopeWithPath() throws Exception {
        Path userDir = tmp.resolve("user");
        Path projDir = tmp.resolve("proj");
        var userMem = new MemoryDir(userDir.resolve(".agent-demo").resolve("memory"));
        var projMem = new MemoryDir(projDir.resolve(".agent-demo").resolve("memory"));
        Files.writeString(userMem.indexFile(), "- [全局 v1](g.md) — 通用约定\n");
        Files.writeString(projMem.indexFile(), "- [项目 v2](p.md) — 项目踩坑\n");
        var builder = new MemoryPromptBuilder(userMem);
        String prompt =
                builder.build(
                        List.of(
                                MemoryDir.forScope(MemoryScope.USER, userDir.toString(), projDir.toString()),
                                MemoryDir.forScope(MemoryScope.PROJECT, userDir.toString(), projDir.toString())),
                        null);
        assertTrue(prompt.contains("USER Scope"));
        assertTrue(prompt.contains("PROJECT Scope"));
        assertTrue(prompt.contains("通用约定"));
        assertTrue(prompt.contains("项目踩坑"));
        assertTrue(prompt.contains(userMem.dir().toString()));
        assertTrue(prompt.contains(projMem.dir().toString()));
    }

    @Test
    void localScopeHasNoDiskContent() throws Exception {
        var userBase = tmp.resolve("base");
        var builder = new MemoryPromptBuilder(
                MemoryDir.forScope(MemoryScope.USER, userBase.toString(), userBase.toString()));
        String prompt =
                builder.build(
                        List.of(
                                MemoryDir.forScope(MemoryScope.USER, userBase.toString(), userBase.toString()),
                                MemoryDir.forScope(MemoryScope.LOCAL, userBase.toString(), userBase.toString())),
                        null);
        // LOCAL 无磁盘：不产生空目录/不注入 LOCAL 索引段（仅 USER 段存在）
        assertTrue(prompt.contains("USER Scope"));
        assertFalse(prompt.contains("LOCAL Scope ("));
    }
}
