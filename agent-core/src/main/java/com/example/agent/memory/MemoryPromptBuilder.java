package com.example.agent.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 Memory 拼到 system prompt（详见 design.md §5.4 与 add-memory-three-scope change）。
 *
 * <p>支持多 scope 注入：对每个 {@link MemoryDir}（USER / PROJECT / LOCAL）分别读取索引、按 scope
 * 限定召回，并拼成带 scope 标注的记忆段，逐 scope 展示实际存放路径。LOCAL scope 无磁盘文件，
 * 其条目由会话内部提供（本 change 提供最小空实现，完整写入链路见后续 change）。
 *
 * <p>模板从 {@code /prompts/memory-system.txt} 加载，含占位符：{@code {scopeSections}} /
 * {@code {extraGuidelines}}。
 */
public class MemoryPromptBuilder {
    /** 模板资源路径（classpath） */
    private static final String TEMPLATE_PATH = "/prompts/memory-system.txt";

    /** memory 目录管理器（USER 兼容单 scope 场景） */
    private final MemoryDir dir;

    /**
     * 构造 memory prompt 构建器（USER scope 便捷包装）。
     *
     * @param dir memory 目录管理器
     */
    public MemoryPromptBuilder(MemoryDir dir) {
        this.dir = dir;
    }

    /**
     * 构造 memory 部分的 system prompt（USER scope 便捷版本）。
     *
     * @param extraGuidelines 额外附加的 memory 指引（可空）
     * @return 完整 system prompt 片段
     */
    public String build(String extraGuidelines) {
        return build(List.of(dir), extraGuidelines);
    }

    /**
     * 构造 memory 部分的 system prompt（多 scope）。
     *
     * @param dirs 参与注入的 memory 目录（USER / PROJECT / LOCAL；空列表返回空串）
     * @param extraGuidelines 额外附加的 memory 指引（可空）
     * @return 完整 system prompt 片段
     */
    public String build(List<MemoryDir> dirs, String extraGuidelines) {
        if (dirs == null || dirs.isEmpty()) return "";
        String sections = buildScopeSections(dirs);
        String extra = extraGuidelines != null && !extraGuidelines.isBlank() ? extraGuidelines : "";
        String template = loadTemplate();
        return template.replace("{scopeSections}", sections).replace("{extraGuidelines}", extra);
    }

    /**
     * 逐 scope 生成小节：标题（scope 名）+ 实际路径 + 索引内容。
     *
     * @param dirs memory 目录列表
     * @return 拼接后的 scope 小节文本（空 scope 跳过）
     */
    private String buildScopeSections(List<MemoryDir> dirs) {
        StringBuilder sb = new StringBuilder();
        for (MemoryDir d : dirs) {
            if (d == null || d.dir() == null) continue; // LOCAL 无磁盘，跳过索引读取
            String index = truncate(readIndex(d));
            String path = d.dir().toString();
            sb.append("\n### ").append(d.scope().name()).append(" Scope (").append(path).append(")\n\n");
            if (index.isEmpty()) {
                sb.append("(empty)\n");
            } else {
                sb.append(index).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 从 classpath 加载 memory prompt 模板（缺失时回退到内置最小模板）。
     *
     * @return 模板字符串
     */
    private String loadTemplate() {
        return com.example.agent.util.PromptLoader.loadOrFallback(
                TEMPLATE_PATH, "# Persistent Agent Memory\n{scopeSections}\n{extraGuidelines}");
    }

    /**
     * 读取 MEMORY.md 索引内容并截断（不存在或读取失败返回空串）。
     *
     * @param d memory 目录
     * @return 截断后的索引文本
     */
    private String readIndex(MemoryDir d) {
        try {
            if (d.indexFile() == null || Files.notExists(d.indexFile())) return "";
            return Files.readString(d.indexFile());
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 截断索引内容（复用目录截断规则 200 行/25KB）。
     *
     * @param content 原始索引文本
     * @return 截断后的文本
     */
    private String truncate(String content) {
        return dir.truncateIndex(content);
    }
}
