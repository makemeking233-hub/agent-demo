package com.example.agent.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * 把 Memory 拼到 system prompt（详见 design.md §5.4、add-memory-three-scope、add-memory-sidequery）。
 *
 * <p>支持多 scope 注入：优先用 {@link MemoryRetriever} 按查询召回各 scope 的条目并渲染，避免把全量
 * 索引灌进 system prompt。旧版（无 retriever）降级为渲染各 scope 索引全文，保持向后兼容。
 *
 * <p>模板从 {@code /prompts/memory-system.txt} 加载：{@code {scopeSections}} /
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
     * 构造 memory 部分的 system prompt（USER scope 便捷版本，降级渲染索引全文）。
     *
     * @param extraGuidelines 额外附加的 memory 指引（可空）
     * @return 完整 system prompt 片段
     */
    public String build(String extraGuidelines) {
        return build(List.of(dir), extraGuidelines);
    }

    /**
     * 构造 memory 部分的 system prompt（多 scope，降级渲染索引全文）。
     *
     * @param dirs 参与注入的 memory 目录（USER / PROJECT / LOCAL；空列表返回空串）
     * @param extraGuidelines 额外附加的 memory 指引（可空）
     * @return 完整 system prompt 片段
     */
    public String build(List<MemoryDir> dirs, String extraGuidelines) {
        if (dirs == null || dirs.isEmpty()) return "";
        String sections = buildSections(dirs);
        String extra = extraGuidelines != null && !extraGuidelines.isBlank() ? extraGuidelines : "";
        String template = loadTemplate();
        return template.replace("{scopeSections}", sections).replace("{extraGuidelines}", extra);
    }

    /**
     * 构造 memory 部分的 system prompt（用检索器按查询召回渲染，替代全量索引）。
     *
     * @param query 当前用户查询
     * @param dirs 参与注入的 memory 目录
     * @param retriever 检索器（可空；null 时降级为索引全文）
     * @param extraGuidelines 额外附加的 memory 指引（可空）
     * @param k 每 scope 召回条数上限
     * @return 完整 system prompt 片段
     */
    public String build(
            String query,
            List<MemoryDir> dirs,
            MemoryRetriever retriever,
            String extraGuidelines,
            int k) {
        if (dirs == null || dirs.isEmpty()) return "";
        String sections;
        if (retriever != null && query != null && !query.isBlank()) {
            sections = buildRetrievedSections(retriever.retrieve(query, dirs, k));
        } else {
            sections = buildSections(dirs);
        }
        String extra = extraGuidelines != null && !extraGuidelines.isBlank() ? extraGuidelines : "";
        String template = loadTemplate();
        return template.replace("{scopeSections}", sections).replace("{extraGuidelines}", extra);
    }

    /** 渲染召回结果：scope 小节列出命中的条目（标题/描述/路径）。 */
    private String buildRetrievedSections(Map<MemoryScope, List<MemoryEntry>> retrieved) {
        if (retrieved == null || retrieved.isEmpty()) {
            return "\n### USER Scope\n\n(no relevant memories)\n";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<MemoryScope, List<MemoryEntry>> e : retrieved.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            sb.append("\n### ").append(e.getKey().name()).append(" Scope (relevant)\n\n");
            for (MemoryEntry entry : e.getValue()) {
                sb.append("- ").append(entry.title()).append(" (").append(entry.filename()).append(") — ")
                        .append(entry.description()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 逐 scope 生成小节：标题 + 实际路径 + 全量索引内容（降级路径）。 */
    private String buildSections(List<MemoryDir> dirs) {
        StringBuilder sb = new StringBuilder();
        for (MemoryDir d : dirs) {
            if (d == null || d.dir() == null) continue;
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
