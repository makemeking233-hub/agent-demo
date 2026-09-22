package com.example.agent.memory;

import com.example.agent.config.AgentConfig;
import com.example.agent.llm.LlmProvider;
import com.example.agent.memory.embedding.EmbeddingProvider;
import com.example.agent.memory.embedding.VectorIndex;
import com.example.agent.memory.embedding.VectorIndexStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory 召回检索器（详见 add-memory-sidequery / add-embedding-rag change）。
 *
 * <p>对每个 {@link MemoryDir}（按 scope）执行**三层召回**：
 *
 * <ol>
 *   <li><b>字面层</b>：用 {@link MemoryRecall} 做 token 重叠召回（scope 限定）
 *   <li><b>embedding 粗排层</b>（add-embedding-rag）：字面不足且 {@link EmbeddingProvider}
 *       就绪时，用向量索引 KNN 检索补足并集去重——这是解决「sideQuery 候选被截断到 8」的层
 *   <li><b>sideQuery 精排层</b>：前两层并集仍不足且候选足够时，复用 LLM 精排
 * </ol>
 *
 * <p>每层独立短路与降级：任一层失败只记 WARN，不影响其余层与最终输出。
 *
 * <p>返回 {@code Map<MemoryScope, List<MemoryEntry>>}（仅保留命中的 scope）。provider 为 null、
 * embedding 不可用或 sideQuery 故障时逐层退化，不抛异常。
 */
public class MemoryRetriever {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetriever.class);

    /** embedding 粗排的默认 top-k（比最终 k 大，留出并集去重余量）。 */
    public static final int DEFAULT_K_EMBED = 10;

    private final LlmProvider provider;
    private final String model;
    private final MemoryRecall recall;
    private final AgentConfig.SideQuery sideQuery;

    /** embedding 提供者（可空 = 不启用 embedding 粗排层）。 */
    private final EmbeddingProvider embeddingProvider;

    /** 向量索引存储（可空 = 不启用 embedding 粗排层）。 */
    private final VectorIndexStore indexStore;

    /** embedding 粗排 top-k。 */
    private final int kEmbed;

    /**
     * 构造两层检索器（字面 + sideQuery；向后兼容 v0.4 行为）。
     *
     * @param provider  LLM provider（可空；null 时仅字面召回）
     * @param model     sideQuery 使用的模型名
     * @param recall    字面召回器
     * @param sideQuery sideQuery 配置
     */
    public MemoryRetriever(
            LlmProvider provider, String model, MemoryRecall recall, AgentConfig.SideQuery sideQuery) {
        this(provider, model, recall, sideQuery, null, null, DEFAULT_K_EMBED);
    }

    /**
     * 构造三层检索器（字面 + embedding 粗排 + sideQuery 精排；add-embedding-rag T5）。
     *
     * @param provider          LLM provider（可空；null 时不调 sideQuery）
     * @param model             sideQuery 使用的模型名
     * @param recall            字面召回器
     * @param sideQuery         sideQuery 配置（可空）
     * @param embeddingProvider embedding 提供者（可空 = 跳过 embedding 层）
     * @param indexStore        向量索引存储（可空 = 跳过 embedding 层）
     * @param kEmbed            embedding 粗排 top-k（{@code <= 0} 时用默认值）
     */
    public MemoryRetriever(
            LlmProvider provider,
            String model,
            MemoryRecall recall,
            AgentConfig.SideQuery sideQuery,
            EmbeddingProvider embeddingProvider,
            VectorIndexStore indexStore,
            int kEmbed) {
        this.provider = provider;
        this.model = model;
        this.recall = recall;
        this.sideQuery = sideQuery;
        this.embeddingProvider = embeddingProvider;
        this.indexStore = indexStore;
        this.kEmbed = kEmbed > 0 ? kEmbed : DEFAULT_K_EMBED;
    }

    /**
     * 检索各 scope 的相关记忆条目（三层架构）。
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

            // 阶段 1：字面召回
            List<MemoryEntry> hit = new ArrayList<>(recall.recall(query, entries, k, 0.3, d.scope()));

            // 阶段 2：embedding 粗排（字面已满则短路，省去 embed + KNN 开销）
            if (hit.size() < k && embeddingAvailable()) {
                List<String> embedded = embeddingFilenames(query, d, entries);
                if (!embedded.isEmpty()) {
                    hit = mergeByFilename(hit, entries, embedded, k);
                }
            }

            // 阶段 3：sideQuery 精排（前两层并集仍不足且候选足够时）
            if (selector != null
                    && sideQuery != null
                    && sideQuery.enabled()
                    && hit.size() < k
                    && entries.size() >= sideQuery.minCandidates()) {
                List<String> extra = selector.select(query, cap(entries, sideQuery.maxCandidates()), k);
                hit = mergeByFilename(hit, entries, extra, k);
            }
            result.put(d.scope(), hit);
        }
        return result;
    }

    /** embedding 层是否可用（provider 与 store 都存在且 provider 就绪）。 */
    private boolean embeddingAvailable() {
        return embeddingProvider != null && indexStore != null && embeddingProvider.isReady();
    }

    /**
     * 用 embedding 检索该 scope 的相关条目（返回 filename 列表，按相似度降序）。
     *
     * <p>任何异常（embed 失败 / 索引加载失败 / KNN 异常）都记 WARN 并返回空列表——embedding 是
     * 增益层，其故障不应影响字面与 sideQuery 两层。
     *
     * @param query   当前查询
     * @param dir     该 scope 的 memory 目录
     * @param entries 该 scope 的候选条目
     * @return 命中的 filename 列表；失败时为空列表
     */
    private List<String> embeddingFilenames(String query, MemoryDir dir, List<MemoryEntry> entries) {
        try {
            VectorIndex idx = indexStore.indexFor(dir.scope(), dir, entries);
            if (idx == null || idx.size() == 0) return List.of();
            float[] queryVector = embeddingProvider.embed(query);
            List<VectorIndex.ScoredItem> scored = idx.search(queryVector, kEmbed);
            List<String> ids = new ArrayList<>(scored.size());
            for (VectorIndex.ScoredItem item : scored) {
                ids.add(item.id());
            }
            return ids;
        } catch (RuntimeException e) {
            log.warn("embedding 召回失败（本轮跳过该层）: {}", e.toString());
            return List.of();
        }
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
