package com.example.agent.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.stream.Stream;

/**
 * Memory 目录管理（路径 + 权限 + 截断；详见 design.md §5.4 与 add-memory-three-scope change）。
 *
 * <p>权限：目录 0700（POSIX；Windows 跳过）。
 *
 * <p>索引截断：{@code MEMORY.md} 硬截断 200 行 / 25KB。
 *
 * <p>作用域感知（{@link #forScope}）：
 *
 * <ul>
 *   <li>USER → {@code <baseDir>/.agent-demo/memory/}（跨项目全局）
 *   <li>PROJECT → {@code <cwd>/.agent-demo/memory/}（项目专属）
 *   <li>LOCAL → 无磁盘路径（{@link #dir()} 为 {@code null}；一次性记忆，不入磁盘）
 * </ul>
 */
public class MemoryDir {
    public static final int MAX_INDEX_LINES = 200;
    public static final int MAX_INDEX_BYTES = 25_000;

    /** 相对目录名（USER / PROJECT 均为该名，区别在于所属根不同） */
    private static final String REL_DIR = ".agent-demo/memory";

    /**
     * memory 目录路径（LOCAL scope 时为 {@code null}）
     */
    private final Path dir;

    /**
     * 该目录所属的作用域
     */
    private final MemoryScope scope;

    /**
     * 构造 memory 目录（USER scope；不存在则创建；POSIX 设 0700，Windows 跳过）。
     *
     * @param dir memory 目录路径
     */
    public MemoryDir(Path dir) {
        this(dir, MemoryScope.USER);
    }

    /**
     * 构造 memory 目录（指定 scope；不存在则创建；POSIX 设 0700，Windows 跳过）。
     *
     * @param dir   memory 目录路径（LOCAL scope 可为 {@code null}）
     * @param scope 作用域
     */
    public MemoryDir(Path dir, MemoryScope scope) {
        this.dir = dir;
        this.scope = scope;
        if (dir != null && scope != MemoryScope.LOCAL) {
            try {
                Files.createDirectories(dir);
                try {
                    Files.setPosixFilePermissions(
                            dir,
                            EnumSet.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE,
                                    PosixFilePermission.OWNER_EXECUTE));
                } catch (UnsupportedOperationException ignored) {
                    /* Windows */
                }
            } catch (IOException e) {
                throw new RuntimeException("memory 目录创建失败: " + dir, e);
            }
        }
    }

    /**
     * 按 scope 与根目录解析 memory 目录。
     *
     * @param scope   作用域
     * @param baseDir USER 根的基准目录（通常为 {@code user.home}；LOCAL/PROJECT 忽略）
     * @param cwd     当前工作目录（PROJECT 用；其余忽略）
     * @return 对应 scope 的 {@link MemoryDir}
     */
    public static MemoryDir forScope(MemoryScope scope, String baseDir, String cwd) {
        return switch (scope) {
            case USER -> new MemoryDir(Path.of(baseDir, REL_DIR), MemoryScope.USER);
            case PROJECT -> new MemoryDir(Path.of(cwd, REL_DIR), MemoryScope.PROJECT);
            case LOCAL -> new MemoryDir(null, MemoryScope.LOCAL);
        };
    }

    /**
     * @return 目录路径；LOCAL scope 返回 {@code null}
     */
    public Path dir() {
        return dir;
    }

    /**
     * @return 作用域
     */
    public MemoryScope scope() {
        return scope;
    }

    public Path indexFile() {
        return dir == null ? null : dir.resolve("MEMORY.md");
    }

    public Path entryFile(String filename) {
        return dir == null ? null : dir.resolve(filename);
    }

    /**
     * 列出 memory 目录下的单条记忆文件（排除 MEMORY.md 索引文件）。LOCAL scope 返回空流。
     */
    public Stream<Path> listEntries() throws IOException {
        if (dir == null) return Stream.empty();
        return Files.list(dir).filter(p -> !p.getFileName().toString().equals("MEMORY.md"));
    }

    /**
     * 截断索引内容：保留前 200 行 / 25KB（详见 design.md §5.4）
     */
    public String truncateIndex(String content) {
        if (content == null) return "";
        String[] lines = content.split("\n", -1);
        if (lines.length <= MAX_INDEX_LINES && content.getBytes().length <= MAX_INDEX_BYTES)
            return content;
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
        return "MemoryDir{path="
                + dir
                + ", scope="
                + scope
                + ", maxIndexLines="
                + MAX_INDEX_LINES
                + ", maxIndexBytes="
                + MAX_INDEX_BYTES
                + "}";
    }
}
