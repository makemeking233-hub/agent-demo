package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

/**
 * MemorySectionProvider 单元测试（fix-memory-recall-wiring T3）。
 *
 * <p>覆盖：按 query 产出带 {@code (relevant)} 指纹的召回段；空 query / 空目录 / null 目录返回空串
 * （即「本轮不注入记忆段」，而非报错）。
 */
class MemorySectionProviderTest {
    @TempDir Path tmp;

    private AgentConfig.SideQuery sq() {
        return new AgentConfig.SideQuery(true, 8, 3);
    }

    private MemoryRetriever retriever() {
        return new MemoryRetriever(null, "deepseek-chat", new MemoryRecall(), sq());
    }

    /** 建一个 USER scope 目录并写入一条索引条目。 */
    private MemoryDir userDirWith(MemoryEntry... entries) throws Exception {
        Path base = tmp.resolve("base-" + System.nanoTime());
        var dir = new MemoryDir(base.resolve("mem"), MemoryScope.USER);
        new MemoryIndex(dir.indexFile(), MemoryScope.USER).write(List.of(entries));
        return dir;
    }

    @Test
    void sectionForRelevantQueryContainsRetrievedEntries() throws Exception {
        var dir =
                userDirWith(
                        new MemoryEntry("Java 17 安装", "JDK 安装步骤", "java17.md", MemoryScope.USER));
        var provider = new MemorySectionProvider(retriever(), List.of(dir), "", 5);

        String section = provider.sectionFor("安装 Java");

        assertTrue(section.contains("(relevant)"), "召回格式应带 (relevant) 指纹");
        assertTrue(section.contains("java17.md"), "应含命中条目的文件名");
        assertTrue(section.contains("Persistent Agent Memory"), "应含记忆段模板标题");
    }

    @Test
    void sectionForBlankQueryReturnsEmpty() throws Exception {
        var dir = userDirWith(new MemoryEntry("Java 17", "JDK", "java17.md", MemoryScope.USER));
        var provider = new MemorySectionProvider(retriever(), List.of(dir), "", 5);

        assertEquals("", provider.sectionFor(""));
        assertEquals("", provider.sectionFor("   "));
        assertEquals("", provider.sectionFor(null));
    }

    @Test
    void sectionForEmptyDirsReturnsEmpty() {
        var provider = new MemorySectionProvider(retriever(), List.of(), "", 5);
        assertEquals("", provider.sectionFor("anything"));
    }

    @Test
    void sectionForNullDirsReturnsEmpty() {
        var provider = new MemorySectionProvider(retriever(), null, "", 5);
        assertEquals("", provider.sectionFor("anything"));
    }

    @Test
    void sectionForNullFirstDirReturnsEmpty() {
        var provider =
                new MemorySectionProvider(retriever(), java.util.Arrays.asList((MemoryDir) null), "", 5);
        assertEquals("", provider.sectionFor("anything"));
    }
}
