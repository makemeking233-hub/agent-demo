package com.example.agent.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MEMORY.md 索引解析/序列化（含作用域 scope）。
 *
 * <p>每行格式：{@code - [标题](文件名) — 一行描述}。解析出的 {@link MemoryEntry} 携带该索引所属的
 * scope；写入/添加时同样以该 scope 关联。
 */
public class MemoryIndex {
    /**
     * 索引行匹配正则（捕获：标题 / 文件名 / 描述）
     */
    private static final Pattern ENTRY = Pattern.compile("^- \\[([^\\]]+)\\]\\(([^)]+)\\) — (.+)$");

    /**
     * 该索引所属的作用域
     */
    private final MemoryScope scope;

    /**
     * MEMORY.md 文件路径
     */
    private final Path file;

    /**
     * 构造索引解析器（USER scope）。
     *
     * @param file MEMORY.md 文件路径
     */
    public MemoryIndex(Path file) {
        this(file, MemoryScope.USER);
    }

    /**
     * 构造索引解析器（指定 scope）。
     *
     * @param file  MEMORY.md 文件路径
     * @param scope 该索引所属作用域
     */
    public MemoryIndex(Path file, MemoryScope scope) {
        this.file = file;
        this.scope = scope;
    }

    /**
     * 按 scope 构造索引解析器。
     *
     * @param scope 作用域
     * @param file  MEMORY.md 文件路径
     * @return 指定 scope 的 {@link MemoryIndex}
     */
    public static MemoryIndex forScope(MemoryScope scope, Path file) {
        return new MemoryIndex(file, scope);
    }

    /**
     * @return 索引所属作用域
     */
    public MemoryScope scope() {
        return scope;
    }

    /**
     * 解析 MEMORY.md 为 entry 列表（不存在返回空列表）。每条 entry 携带本索引的 scope。
     *
     * @return 解析出的 memory entry
     * @throws IOException 文件读取错误
     */
    public List<MemoryEntry> parse() throws IOException {
        if (Files.notExists(file)) return List.of();
        List<MemoryEntry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            Matcher m = ENTRY.matcher(line.trim());
            if (m.matches()) {
                entries.add(new MemoryEntry(m.group(1), m.group(3), m.group(2), scope));
            }
        }
        return entries;
    }

    /**
     * 序列化 entry 列表为 MEMORY.md 内容（覆盖写入）。
     *
     * @param entries 待写入的 entry 列表
     * @throws IOException 文件写入错误
     */
    public void write(List<MemoryEntry> entries) throws IOException {
        StringBuilder sb = new StringBuilder("# Memory Index\n\n");
        for (MemoryEntry e : entries) {
            sb.append("- [")
                    .append(e.title())
                    .append("](")
                    .append(e.filename())
                    .append(") — ")
                    .append(e.description())
                    .append("\n");
        }
        Files.writeString(file, sb.toString());
    }

    /**
     * 添加新 entry（同名 filename 覆盖）。
     *
     * @param e        新 entry
     * @param existing 已有 entry 列表
     * @throws IOException 文件写入错误
     */
    public void addEntry(MemoryEntry e, List<MemoryEntry> existing) throws IOException {
        List<MemoryEntry> updated = new ArrayList<>(existing);
        updated.removeIf(en -> en.filename().equals(e.filename()));
        updated.add(e);
        write(updated);
    }
}
