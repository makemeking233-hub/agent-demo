package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class MemoryDirTest {
    @TempDir Path tmp;

    @Test
    void createsDirectory() throws Exception {
        var dir = new MemoryDir(tmp.resolve("mem"));
        assertTrue(Files.isDirectory(dir.dir()));
    }

    @Test
    void truncatesIndexByLines() {
        var dir = new MemoryDir(tmp.resolve("mem"));
        String big = "x\n".repeat(300);
        String truncated = dir.truncateIndex(big);
        assertTrue(truncated.lines().count() <= 210, "应 ≤ 210 行（含截断标记 + 换行）");
        assertTrue(truncated.contains("truncated"));
    }

    @Test
    void truncatesIndexByBytes() {
        var dir = new MemoryDir(tmp.resolve("mem"));
        // 每行 200 字节，共 300 行 ≈ 60KB
        String big = "x".repeat(200) + "\n";
        String content = big.repeat(300);
        String truncated = dir.truncateIndex(content);
        assertTrue(truncated.getBytes().length <= 25000 + 100, "应接近 25KB 截断");
    }
}
