package com.example.agent.memory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * v0.1 简化召回：token 重叠评分（query 与 entry title/description 的交集 / entry 总 token）。 语义召回（sideQuery +
 * embedding）见 design.md v0.3 升级路径。
 */
public class MemoryRecall {

    /**
     * 按 token 重叠评分召回最相关的 memory entry。
     *
     * @param query 用户查询字符串
     * @param entries 候选 memory entry 列表
     * @param maxRecall 最多返回条数
     * @param minScore 评分阈值（{@code 0~1}；详见 design.md §9 {@code recallMinScore}，默认 0.3）
     * @return 评分 >= minScore 的 entry，按评分降序，最多 maxRecall 条
     */
    public List<MemoryEntry> recall(
            String query, List<MemoryEntry> entries, int maxRecall, double minScore) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) return List.of();

        Map<MemoryEntry, Double> scored = new LinkedHashMap<>();
        for (MemoryEntry e : entries) {
            Set<String> entryTokens = tokenize(e.title() + " " + e.description());
            if (entryTokens.isEmpty()) continue;
            Set<String> intersection = new HashSet<>(queryTokens);
            intersection.retainAll(entryTokens);
            double score = (double) intersection.size() / entryTokens.size();
            if (score >= minScore) scored.put(e, score);
        }
        return scored.entrySet().stream()
                .sorted(Map.Entry.<MemoryEntry, Double>comparingByValue().reversed())
                .limit(maxRecall)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 简单分词：去除停用标点 + 长度 {@code < 2} 的词丢弃。
     *
     * @param s 原始文本
     * @return token 集合
     */
    public Set<String> tokenize(String s) {
        if (s == null) return Set.of();
        return Arrays.stream(s.toLowerCase().split("[\\s,;.!?，。；！？、]+"))
                .filter(t -> t.length() >= 2)
                .collect(Collectors.toSet());
    }
}
