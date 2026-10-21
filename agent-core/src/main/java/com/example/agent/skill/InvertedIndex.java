package com.example.agent.skill;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 技能倒排索引（refactor-skill-index-layer T4.1 引入）。
 *
 * <p>结构：{@code keyword -> Set<skillName>}。索引字段为 skill 的 {@code name + description}
 * （不索引正文——正文懒加载，索引期不可得）。
 *
 * <p>分词规则复用 {@link com.example.agent.memory.MemoryRecall#tokenize}（长度 ≥ 2、过滤中英文标点），
 * 保证 skill 检索与 memory 检索的分词行为一致。
 *
 * <p>用途：为 {@link SkillPool#search} 提供 {@code O(1)} 级 keyword 预筛——10000 个 skill 里先用
 * 倒排把候选压到几百条，再交给 embedding KNN 精排（见 add-skill-demand-loading）。
 */
public final class InvertedIndex {

    /** 分词器（复用 Memory 侧规则，保证两套检索一致） */
    private static final com.example.agent.memory.MemoryRecall TOKENIZER =
            new com.example.agent.memory.MemoryRecall();

    /** keyword -> skillName 集合（TreeMap 让 iteration 顺序稳定，便于测试与调试） */
    private final Map<String, Set<String>> keywordToSkills = new TreeMap<>();

    /**
     * 构造倒排索引。
     *
     * @param metas 技能元数据列表（{@code null} 元素被跳过；可为 {@code null} 列表）
     */
    public InvertedIndex(List<SkillMeta> metas) {
        if (metas == null) return;
        for (SkillMeta meta : metas) {
            if (meta == null) continue;
            String field = meta.name() + " " + meta.description();
            for (String token : TOKENIZER.tokenize(field)) {
                keywordToSkills
                        .computeIfAbsent(token, k -> new LinkedHashSet<>())
                        .add(meta.name());
            }
        }
    }

    /**
     * 单 keyword 精确查找。
     *
     * @param keyword 关键词
     * @return 命中的 skillName 集合（无则空集，不可为 {@code null}）
     */
    public Set<String> lookup(String keyword) {
        if (keyword == null || keyword.isBlank()) return Set.of();
        return keywordToSkills.getOrDefault(keyword.toLowerCase(java.util.Locale.ROOT), Set.of());
    }

    /**
     * 多 keyword 并集查找（任一 keyword 命中即入候选集）。
     *
     * @param keywords 关键词集合（{@code List} / {@code Set} 均可；空或 {@code null} 返回空集）
     * @return 命中的 skillName 并集（无则空集，不可为 {@code null}）
     */
    public Set<String> lookupAll(java.util.Collection<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String kw : keywords) {
            result.addAll(lookup(kw));
        }
        return result;
    }

    /** @return 索引中的 keyword 总数（测试与可观测用） */
    public int keywordCount() {
        return keywordToSkills.size();
    }
}