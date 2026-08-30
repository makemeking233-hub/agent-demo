package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * Memory 三 scope 专项测试（openspec add-memory-three-scope）。
 *
 * <p>验证 {@link MemoryDir#forScope} 的路径解析（USER / PROJECT / LOCAL）与 scope 语义：USER 落在
 * 用户根、PROJECT 落在工作目录、LOCAL 无磁盘路径。
 */
class MemoryThreeScopeTest {
    @TempDir Path tmp;

    @Test
    void userScopeUnderBaseDir() {
        String base = tmp.resolve("home").toString();
        String cwd = tmp.resolve("proj").toString();
        MemoryDir dir = MemoryDir.forScope(MemoryScope.USER, base, cwd);
        assertTrue(dir.dir().toString().replace("\\", "/").endsWith("home/.agent-demo/memory"));
        assertEquals(MemoryScope.USER, dir.scope());
        assertFalse(dir.dir().toString().contains("proj"), "USER 不应落到工作目录");
    }

    @Test
    void projectScopeUnderCwd() {
        String base = tmp.resolve("home").toString();
        String cwd = tmp.resolve("proj").toString();
        MemoryDir dir = MemoryDir.forScope(MemoryScope.PROJECT, base, cwd);
        assertTrue(dir.dir().toString().replace("\\", "/").endsWith("proj/.agent-demo/memory"));
        assertEquals(MemoryScope.PROJECT, dir.scope());
        assertFalse(dir.dir().toString().contains("home"), "PROJECT 不应落到用户根");
    }

    @Test
    void localScopeHasNoDiskDir() {
        String base = tmp.resolve("home").toString();
        String cwd = tmp.resolve("proj").toString();
        MemoryDir dir = MemoryDir.forScope(MemoryScope.LOCAL, base, cwd);
        assertNull(dir.dir(), "LOCAL 不应有磁盘路径");
        assertNull(dir.indexFile());
        assertEquals(MemoryScope.LOCAL, dir.scope());
    }

    @Test
    void userAndProjectDirsAreDistinct() {
        String base = tmp.resolve("home").toString();
        String cwd = tmp.resolve("proj").toString();
        MemoryDir user = MemoryDir.forScope(MemoryScope.USER, base, cwd);
        MemoryDir proj = MemoryDir.forScope(MemoryScope.PROJECT, base, cwd);
        assertFalse(user.dir().equals(proj.dir()), "USER 与 PROJECT 路径必须区分");
    }
}
