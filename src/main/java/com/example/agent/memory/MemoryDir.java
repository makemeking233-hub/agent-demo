package com.example.agent.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.stream.Stream;

/**
 * Memory 目录管理（路径 + 权限 + 截断；详见 design.md §5.4）。
 *
 * <p>权限：目录 0700（POSIX；Windows 跳过）。
 *
 * <p>索引截断：{@code MEMORY.md} 硬截断 200 行 / 25KB。
 */
public class MemoryDir {
    public static final int MAX_INDEX_LINES = 200;
    public static final int MAX_INDEX_BYTES = 25_000;

    private final Path dir;

    public MemoryDir(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
            try {
                Files.setPosixFilePermissions(dir, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
            } catch (UnsupportedOperationException ignored) { /* Windows */ }
        } catch (IOException e) {
            throw new RuntimeException("memory 目录创建失败: " + dir, e);
        }
    }

    public Path dir() { return dir; }
    public Path indexFile() { return dir.resolve("MEMORY.md"); }
    public Path entryFile(String filename) { return dir.resolve(filename); }

    /** 列出 memory 目录下的单条记忆文件（排除 MEMORY.md 索引文件）。 */
    public Stream<Path> listEntries() throws IOException {
        return Files.list(dir).filter(p -> !p.getFileName().toString().equals("MEMORY.md"));
    }

    /** 截断索引内容：保留前 200 行 / 25KB（详见 design.md §5.4） */
    public String truncateIndex(String content) {
        if (content == null) return "";
        String[] lines = content.split("\n", -1);
        if (lines.length <= MAX_INDEX_LINES && content.getBytes().length <= MAX_INDEX_BYTES) return content;
        StringBuilder sb = new StringBuilder();
        int bytes = 0;
        int linesUsed = 0;
        for (int i = 0; i < lines.length && linesUsed < MAX_INDEX_LINES; i++) {
            byte[] lineBytes = (lines[i] + "\n").getBytes();
            if (bytes + lineBytes.length > MAX_INDEX_BYTES) break;
            sb.append(lines[i]).append("\n");
            bytes += lineBytes.length;
            linesUsed++;
        }
        sb.append("\n[... index truncated ...]\n");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "MemoryDir{path=" + dir + ", maxIndexLines=" + MAX_INDEX_LINES
            + ", maxIndexBytes=" + MAX_INDEX_BYTES + "}";
    }
}