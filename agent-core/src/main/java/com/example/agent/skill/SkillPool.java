package com.example.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 技能索引池（refactor-skill-index-layer T4.3 引入；单例，跨 session 共享）。
 *
 * <p>持有全部 {@link SkillMeta}（元数据，不含正文）+ per-scope {@link InvertedIndex}。为什么是单例：
 * skill 是**全局资源**（与 Memory 的 USER scope 同理），不该每 session 重建索引——10000 条的重建
 * 代价不可接受。
 *
 * <p>为什么 per-scope 分开：与 {@code MemoryRetriever} 一致，PROJECT 与 USER 不混合检索。
 *
 * <p>检索链路（{@link #search}）当前实现为**纯倒排**；embedding 粗排 + KNN 精排由
 * add-skill-demand-loading 接入（{@link #search} 的接口保持不变，内部替换实现）。
 */
public final class SkillPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SkillPool.class);

    /** 全局单例；未安装时 {@code instance()} 返回空池（不抛异常，避免装配顺序问题）。 */
    private static volatile SkillPool installed;

    /** skillName -> 包装后的 Skill（元数据 + 懒加载正文 + mtime 缓存） */
    private final Map<String, Skill> byName = new LinkedHashMap<>();

    /** scope -> 倒排索引 */
    private final Map<SkillScope, InvertedIndex> inverted = new LinkedHashMap<>();

    /** skillName -> 预计算 embedding（add-skill-demand-loading T3；embedding 不可用时为空）。 */
    private final Map<String, float[]> embeddings = new LinkedHashMap<>();

    /** embedding 提供者（可空 = 不启用 embedding 精排层）。 */
    private final com.example.agent.memory.embedding.EmbeddingProvider embedder;

    private SkillPool(List<SkillMeta> metas) {
        this(metas, null);
    }

    private SkillPool(
            List<SkillMeta> metas, com.example.agent.memory.embedding.EmbeddingProvider embedder) {
        this.embedder = embedder;
        for (SkillScope scope : SkillScope.values()) {
            inverted.put(scope, new InvertedIndex(List.of()));
        }
        for (SkillMeta meta : metas) {
            if (meta == null) continue;
            // 同名覆盖：后写入者生效（调用方 SkillCatalog 已按根目录顺序做过一次去重）
            byName.put(meta.name(), new Skill(meta));
        }
        // 按 scope 重建倒排
        for (SkillScope scope : SkillScope.values()) {
            inverted.put(
                    scope,
                    new InvertedIndex(
                            byName.values().stream()
                                    .map(Skill::meta)
                                    .filter(m -> m.scope() == scope)
                                    .toList()));
        }
        buildEmbeddings();
    }

    /**
     * 预计算每个 skill 的 embedding（name + description）。
     *
     * <p>严格降级（Memory 侧 T8b.0 教训的延伸）：{@code isReady()=false} 时**完全不建**索引，
     * 而不是建一堆零向量——零向量与所有 query 的 cosine 都是 0，会让排序退化为任意顺序，
     * 反而比纯倒排更差。
     */
    private void buildEmbeddings() {
        if (embedder == null || !embedder.isReady()) {
            return;
        }
        try {
            for (Skill skill : byName.values()) {
                String text = skill.name() + " " + skill.description();
                embeddings.put(skill.name(), embedder.embed(text));
            }
            log.info("SkillPool embedding 索引完成: {} 条", embeddings.size());
        } catch (RuntimeException e) {
            // 任何推理异常都清空索引并降级（不让坏掉的 embedding 污染召回）
            log.warn("SkillPool embedding 构建失败，降级为纯倒排: {}", e.toString());
            embeddings.clear();
        }
    }

    /**
     * 扫描技能根目录并建池（不启用 embedding，纯倒排）。
     *
     * @param roots 技能根目录列表
     * @return 新的池实例
     */
    public static SkillPool bootstrap(List<java.nio.file.Path> roots) {
        return bootstrap(roots, null);
    }

    /**
     * 扫描技能根目录并建池（启用 embedding 精排层）。
     *
     * @param roots    技能根目录列表（顺序即覆盖优先级：后出现的覆盖同名）
     * @param embedder embedding 提供者（{@code null} 或 {@code isReady()=false} → 纯倒排）
     * @return 新的池实例（**不**安装为单例）
     */
    public static SkillPool bootstrap(
            List<java.nio.file.Path> roots,
            com.example.agent.memory.embedding.EmbeddingProvider embedder) {
        // 按根目录逐个发现，这样 scope 归属天然正确（后出现的根覆盖同名，其 scope 也随之替换）
        List<SkillMeta> metas = new ArrayList<>();
        if (roots != null) {
            for (java.nio.file.Path root : roots) {
                if (root == null || !java.nio.file.Files.isDirectory(root)) continue;
                SkillScope scope = scopeForRoot(roots, root);
                for (SkillMeta m : SkillCatalog.discoverMeta(List.of(root))) {
                    metas.add(new SkillMeta(m.name(), m.description(), scope, m.dir()));
                }
            }
        }
        log.info("SkillPool bootstrap: {} 个技能 (embedding={})",
                metas.size(), embedder != null && embedder.isReady());
        return new SkillPool(metas, embedder);
    }

    /**
     * 推断某根目录的 scope：约定 {@code roots = [USER 根, PROJECT 根]}，非首个有效根视为 PROJECT。
     */
    private static SkillScope scopeForRoot(List<java.nio.file.Path> roots, java.nio.file.Path root) {
        java.nio.file.Path firstValid = null;
        for (java.nio.file.Path r : roots) {
            if (r != null && java.nio.file.Files.isDirectory(r)) {
                firstValid = r;
                break;
            }
        }
        return (firstValid != null && !firstValid.equals(root)) ? SkillScope.PROJECT : SkillScope.USER;
    }

    /**
     * 安装为全局单例（由 {@code AgentLoopFactory.buildTools} 在启动期调用）。
     *
     * <p>幂等：重复安装以最后一次为准；{@link #resetForTest()} 可清空。
     *
     * @param pool 池实例（{@code null} 等价于清空）
     */
    public static void install(SkillPool pool) {
        installed = pool;
    }

    /** 清空全局单例（仅测试用）。 */
    public static void resetForTest() {
        installed = null;
    }

    /**
     * 取全局单例；未安装时返回一个空池（保证调用方不必判空）。
     *
     * @return 单例（可能为空池）
     */
    public static SkillPool instance() {
        SkillPool local = installed;
        return local != null ? local : new SkillPool(List.of());
    }

    /** @return 全部技能（按发现顺序） */
    public List<SkillMeta> all() {
        return byName.values().stream().map(Skill::meta).toList();
    }

    /**
     * 取指定 scope 的技能。
     *
     * @param scope 作用域
     * @return 该 scope 的元数据列表（无则空）
     */
    public List<SkillMeta> allOf(SkillScope scope) {
        return byName.values().stream().map(Skill::meta).filter(m -> m.scope() == scope).toList();
    }

    /**
     * 按名取技能（含懒加载正文能力）。
     *
     * @param name 技能名
     * @return 技能；未知名称返回 {@link Optional#empty()}
     */
    public Optional<Skill> load(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(byName.get(name));
    }

    /**
     * 按关键词 + 语义检索技能（T3：倒排预筛 → embedding 精排 → 降级纯倒排）。
     *
     * <p>链路：
     *
     * <ol>
     *   <li><b>倒排预筛</b>：query 分词后查 per-scope 倒排索引得候选集（10000 → 通常几十~几百）
     *   <li><b>embedding 精排</b>：候选集非空且 embedder 就绪时，对候选集内每个 skill 算
     *       cosine 相似度并按降序取 top-k
     *   <li><b>降级</b>：候选集为空 → 直接返回空（**不回退全量 KNN**，query 与所有 skill
     *       都不沾边时全量 KNN 只会返回噪声）；embedder 不可用 → 纯倒排顺序
     * </ol>
     *
     * @param query 查询串（{@code null} / 空白返回空列表）
     * @param k 返回条数上限（{@code <= 0} 返回空列表）
     * @return 命中的元数据列表（最多 k 条）
     */
    public List<SkillMeta> search(String query, int k) {
        if (query == null || query.isBlank() || k <= 0) return List.of();
        Set<String> candidates = new LinkedHashSet<>();
        for (InvertedIndex idx : inverted.values()) {
            candidates.addAll(idx.lookupAll(tokenize(query)));
        }
        if (candidates.isEmpty()) {
            // 倒排无命中时不回退全量 KNN：query 与技能库无关键词交集，全量 cosine 只会
            // 返回一批「相似度都差不多」的无关条目，噪声大于收益
            return List.of();
        }
        List<SkillMeta> hits = new ArrayList<>();
        if (embedder != null && embedder.isReady() && !embeddings.isEmpty()) {
            float[] qv = embedder.embed(query);
            hits.addAll(rankByCosine(qv, candidates, k));
            if (!hits.isEmpty()) return hits;
            // 精排无结果（如维度异常）→ 退回纯倒排
        }
        for (String name : candidates) {
            Skill s = byName.get(name);
            if (s != null) hits.add(s.meta());
            if (hits.size() >= k) break;
        }
        return hits;
    }

    /**
     * 在候选集内按 cosine 相似度降序取 top-k。
     *
     * <p>为什么不用 {@code VectorIndex.search}：后者不支持「只在候选集内检索」，而本层的价值
     * 恰是把 10000 条压到 ~200 条后再精排。候选集规模下暴力 cosine（200×512）远快于维护索引。
     *
     * @param qv         查询向量
     * @param candidates 候选 skill 名
     * @param k          返回条数上限
     * @return 按相似度降序的元数据列表
     */
    private List<SkillMeta> rankByCosine(float[] qv, Set<String> candidates, int k) {
        List<java.util.Map.Entry<String, Double>> scored = new ArrayList<>();
        for (String name : candidates) {
            float[] v = embeddings.get(name);
            if (v == null || v.length != qv.length) continue;
            scored.add(java.util.Map.entry(name, (double) cosine(qv, v)));
        }
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<SkillMeta> out = new ArrayList<>();
        for (var e : scored) {
            Skill s = byName.get(e.getKey());
            if (s != null) out.add(s.meta());
            if (out.size() >= k) break;
        }
        return out;
    }

    /** 两个已归一化向量的 cosine（输入未归一化时自动补除）。 */
    private static float cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0f;
        return (float) (dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }

    /** @return 已建 embedding 的条数（可观测 / 测试用） */
    public int embeddingCount() {
        return embeddings.size();
    }

    /** @return 索引中的关键词总数（可观测 / 测试用） */
    public int keywordCount() {
        return inverted.values().stream().mapToInt(InvertedIndex::keywordCount).sum();
    }

    /** 分词（复用 Memory 侧规则，与 {@link InvertedIndex} 一致）。 */
    private static Set<String> tokenize(String s) {
        return new com.example.agent.memory.MemoryRecall().tokenize(s);
    }

    /**
     * 释放资源（T4.8）。
     *
     * <p>当前实现中元数据全在内存、无外部句柄，close 仅清空索引引用以便 GC；已持有的
     * {@link Skill} 引用仍可继续 {@code body()}（幂等读盘）。
     */
    @Override
    public void close() {
        inverted.clear();
    }
}