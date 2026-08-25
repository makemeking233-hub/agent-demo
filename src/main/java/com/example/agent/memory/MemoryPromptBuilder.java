package com.example.agent.memory;

import java.io.IOException;
import java.nio.file.Files;

/**
 * 把 Memory 拼到 system prompt（详见 design.md §5.4）。
 *
 * <p>v0.1 简化：只拼 MEMORY.md 索引内容到 system prompt；不注入文件级内容。
 */
public class MemoryPromptBuilder {
    private final MemoryDir dir;

    public MemoryPromptBuilder(MemoryDir dir) { this.dir = dir; }

    public String build(String extraGuidelines) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Persistent Agent Memory\n\n");
        sb.append("You have a persistent, file-based memory system at ").append(dir.dir()).append(".\n\n");
        sb.append("## How to Save Memory\n");
        sb.append("1. Create a topic file `~/.agent-demo/memory/<name>.md`\n");
        sb.append("2. Update MEMORY.md index with `- [Title](filename) — description`\n\n");
        sb.append("## What Not to Save\n");
        sb.append("- Code-derived knowledge (read the code)\n");
        sb.append("- Duplicate entries\n\n");
        if (extraGuidelines != null && !extraGuidelines.isBlank()) {
            sb.append(extraGuidelines).append("\n\n");
        }

        String indexContent = readIndex();
        sb.append("## MEMORY.md\n\n").append(indexContent.isEmpty()
            ? "Your MEMORY.md is currently empty."
            : indexContent);
        return sb.toString();
    }

    private String readIndex() {
        try {
            if (Files.notExists(dir.indexFile())) return "";
            return dir.truncateIndex(Files.readString(dir.indexFile()));
        } catch (IOException e) {
            return "";
        }
    }
}