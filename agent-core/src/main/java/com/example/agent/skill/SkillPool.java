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

    private SkillPool(List<SkillMeta> metas) {
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
    }

    /**
     * 扫描技能根目录并建池。
     *
     * @param roots 技能根目录列表（顺序即覆盖优先级：后出现的覆盖同名）。
     *               约定为 {@code [USER 根, PROJECT 根]}（与 {@code AgentLoopFactory.buildTools} 一致），
     *               长度为 1 时全部标 USER。
     * @return 新的池实例（**不**安装为单例；单例由 {@link #installForTest} 或调用方显式安装）
     */
    public static SkillPool bootstrap(List<java.nio.file.Path> roots) {
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
        log.info("SkillPool bootstrap: {} 个技能", metas.size());
        return new SkillPool(metas);
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

    /** 安装为全局单例（供 AgentLoopFactory 与测试使用）。 */
    public static void installForTest(SkillPool pool) {
        installed = pool;
    }

    /** 清空全局单例（测试用）。 */
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
     * 按关键词检索技能（当前为**纯倒排**实现）。
     *
     * <p>add-skill-demand-loading 会把内部替换为「倒排预筛 → embedding KNN 精排」；对外签名不变。
     *
     * @param query 查询串（{@code null} / 空白返回空列表）
     * @param k 返回条数上限（{@code <= 0} 视为 1）
     * @return 命中的元数据列表（最多 k 条）
     */
    public List<SkillMeta> search(String query, int k) {
        if (query == null || query.isBlank() || k <= 0) return List.of();
        int limit = k <= 0 ? 1 : k;
        Set<String> candidates = new LinkedHashSet<>();
        for (InvertedIndex idx : inverted.values()) {
            candidates.addAll(idx.lookupAll(tokenize(query)));
        }
        if (candidates.isEmpty()) return List.of();
        List<SkillMeta> hits = new ArrayList<>();
        for (String name : candidates) {
            Skill s = byName.get(name);
            if (s != null) hits.add(s.meta());
            if (hits.size() >= limit) break;
        }
        return hits;
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