package com.example.agent.memory;

import com.example.agent.config.AgentConfig;
import com.example.agent.llm.LlmProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory 召回检索器（详见 add-memory-sidequery change）。
 *
 * <p>对每个 {@link MemoryDir}（按 scope）：
 *
 * <ol>
 *   <li>用 {@link MemoryIndex} 解析该 scope 的全部 {@link MemoryEntry}
 *   <li>用 {@link MemoryRecall} 做字面 token 重叠召回（scope 限定）
 *   <li>若字面命中不足且候选足够且 sideQuery 启用，用 {@link SideQuerySelector} 语义补充，并集去重
 * </ol>
 *
 * <p>返回 {@code Map<MemoryScope, List<MemoryEntry>>}（仅保留命中的 scope）。provider 为 null 或
 * sideQuery 故障时纯字面降级，不抛异常。
 */
public class MemoryRetriever {

    private final LlmProvider provider;
    private final String model;
    private final MemoryRecall recall;
    private final AgentConfig.SideQuery sideQuery;

    /**
     * 构造检索器。
     *
     * @param provider  LLM provider（可空；null 时仅字面召回）
     * @param model     sideQuery 使用的模型名
     * @param recall    字面召回器
     * @param sideQuery sideQuery 配置
     */
    public MemoryRetriever(
            LlmProvider provider, String model, MemoryRecall recall, AgentConfig.SideQuery sideQuery) {
        this.provider = provider;
        this.model = model;
        this.recall = recall;
        this.sideQuery = sideQuery;
    }

    /**
     * 检索各 scope 的相关记忆条目。
     *
     * @param query  当前查询
     * @param dirs   参与检索的 memory 目录（USER / PROJECT / LOCAL）
     * @param k      每 scope 最多返回条数
     * @return scope → 命中条目列表；无命中的 scope 不出现
     */
    public Map<MemoryScope, List<MemoryEntry>> retrieve(String query, List<MemoryDir> dirs, int k) {
        Map<MemoryScope, List<MemoryEntry>> result = new LinkedHashMap<>();
        if (query == null || query.isBlank() || dirs == null) return result;
        SideQuerySelector selector = (provider != null) ? new SideQuerySelector(provider, model) : null;

        for (MemoryDir d : dirs) {
            if (d == null || d.scope() == MemoryScope.LOCAL || d.dir() == null) continue;
            List<MemoryEntry> entries = parseEntries(d);
            if (entries.isEmpty()) continue;
            List<MemoryEntry> hit = new ArrayList<>(recall.recall(query, entries, k, 0.3, d.scope()));
            // sideQuery 补充：字面命中不足且候选足够时
            if (selector != null && sideQuery != null && sideQuery.enabled()
                    && hit.size() < k && entries.size() >= sideQuery.minCandidates()) {
                List<String> extra = selector.select(query, cap(entries, sideQuery.maxCandidates()), k);
                List<MemoryEntry> merged = mergeByFilename(hit, entries, extra, k);
                result.put(d.scope(), merged);
            } else {
                result.put(d.scope(), hit);
            }
        }
        return result;
    }

    /** 解析某 memory 目录的全部 entry（失败返回空列表，不抛）。 */
    private List<MemoryEntry> parseEntries(MemoryDir d) {
        try {
            return new MemoryIndex(d.indexFile(), d.scope()).parse();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 候选截断到上限（控制 sideQuery prompt 长度）。 */
    private List<MemoryEntry> cap(List<MemoryEntry> list, int max) {
        return list.size() <= max ? list : list.subList(0, max);
    }

    /** 字面命中 + sideQuery 结果按 filename 并集去重，留前 k 条（字面优先）。 */
    private List<MemoryEntry> mergeByFilename(
            List<MemoryEntry> hit, List<MemoryEntry> all, List<String> extra, int k) {
        List<MemoryEntry> merged = new ArrayList<>(hit);
        if (hit.size() >= k) return merged.subList(0, k);
        for (String filename : extra) {
            if (merged.size() >= k) break;
            all.stream()
                    .filter(e -> e.filename().equals(filename))
                    .findFirst()
                    .ifPresent(e -> {
                        if (merged.stream().noneMatch(m -> m.filename().equals(e.filename()))) {
                            merged.add(e);
                        }
                    });
        }
        return merged.size() > k ? merged.subList(0, k) : merged;
    }
}
