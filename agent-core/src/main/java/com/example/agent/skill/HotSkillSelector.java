package com.example.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 热门技能选择器（add-skill-demand-loading T4）。
 *
 * <p>仅 {@code hybrid} 档使用：从全部 skill 里挑出至多 {@code limit} 个直露为真实工具。
 *
 * <p><b>三配额叠加</b>：
 *
 * <ol>
 *   <li><b>PROJECT scope 优先</b>：占 {@code projectQuota} 个名额（项目级 skill 是「当前项目专用」，
 *       用户装它就是因为现在要用）
 *   <li><b>频次衰减排序</b>：剩余名额按 {@link SkillUsageStore#score} 降序填充（用户级 skill）
 *   <li><b>会话内最近调用</b>：再补充，去重后截断
 * </ol>
 *
 * <p><b>为什么 PROJECT 也要卡配额</b>：monorepo / 巨型项目可能有几百个项目级 skill，
 * 不卡会独霸全部名额。配额由 {@code AgentConfig.Skill.hybridProjectQuota()} 给出（并 clamp 到 limit）。
 *
 * <p><b>为什么用「最近调用」而不是「最近曝光」</b>：后者会把用户从没主动调过的 skill 塞进 tools，
 * 白占位置。
 */
public final class HotSkillSelector {

    private static final Logger log = LoggerFactory.getLogger(HotSkillSelector.class);

    /** 全部候选（发现顺序）。 */
    private final List<SkillMeta> all;

    /** 频次存储（可空 = 无频次信息，仅按 PROJECT 优先 + 最近调用）。 */
    private final SkillUsageStore usage;

    /** 会话内最近调用过的 skill 名（可空 supplier）。 */
    private final Supplier<List<String>> recentlyUsed;

    /**
     * 构造选择器。
     *
     * @param all          全部 skill 元数据
     * @param usage        频次存储（可空）
     * @param recentlyUsed 会话内最近调用名 supplier（可空）
     */
    public HotSkillSelector(
            List<SkillMeta> all, SkillUsageStore usage, Supplier<List<String>> recentlyUsed) {
        this.all = all == null ? List.of() : new ArrayList<>(all);
        this.usage = usage;
        this.recentlyUsed = recentlyUsed;
    }

    /**
     * 按配额规则挑选热门技能。
     *
     * <p>**四阶段**（每阶段都有独立名额，互不抢占）：
     *
     * <ol>
     *   <li>PROJECT scope：最多 {@code projectQuota} 个
     *   <li>会话内最近调用：最多 {@link #RECENT_QUOTA} 个（从 limit 中预留，避免被频次抢光）
     *   <li>频次衰减排序：填剩余名额
     *   <li>发现顺序兜底：仍未满则按发现顺序补齐（尽量给满直露名额）
     * </ol>
     *
     * @param limit        直露总数上限（{@code <= 0} 返回空）
     * @param projectQuota PROJECT scope 名额（clamp 到 limit；{@code <= 0} 表示不预留）
     * @return 去重且截断后的元数据列表（保持挑选优先级顺序）
     */
    public List<SkillMeta> pick(int limit, int projectQuota) {
        if (limit <= 0 || all.isEmpty()) return List.of();
        int quota = Math.min(Math.max(projectQuota, 0), limit);
        Set<String> pickedNames = new LinkedHashSet<>();
        List<SkillMeta> picked = new ArrayList<>();

        // ① PROJECT scope 优先
        for (SkillMeta m : all) {
            if (picked.size() >= quota) break;
            if (m.scope() == SkillScope.PROJECT && pickedNames.add(m.name())) {
                picked.add(m);
            }
        }

        // ② 会话内最近调用（预留独立名额，避免被 ③ 的频次填充抢光）
        if (recentlyUsed != null) {
            List<String> recent;
            try {
                recent = recentlyUsed.get();
            } catch (RuntimeException e) {
                log.warn("读取最近调用技能失败，跳过该配额: {}", e.toString());
                recent = List.of();
            }
            if (recent != null) {
                int recentBudget = Math.min(RECENT_QUOTA, limit - picked.size());
                int added = 0;
                for (String name : recent) {
                    if (added >= recentBudget) break;
                    if (name == null || !pickedNames.add(name)) continue;
                    var found = all.stream().filter(m -> m.name().equals(name)).findFirst();
                    if (found.isPresent()) {
                        picked.add(found.get());
                        added++;
                    }
                }
            }
        }

        // ③ 频次衰减排序填充剩余名额
        if (picked.size() < limit && usage != null) {
            long now = System.currentTimeMillis();
            double lambda = com.example.agent.config.AgentConfig.Skill.DEFAULT_DECAY_LAMBDA;
            List<SkillMeta> byScore =
                    all.stream()
                            .filter(m -> !pickedNames.contains(m.name()))
                            .sorted(
                                    java.util.Comparator.comparingDouble(
                                            (SkillMeta m) -> -usage.score(m.name(), lambda, now)))
                            .toList();
            for (SkillMeta m : byScore) {
                if (picked.size() >= limit) break;
                if (pickedNames.add(m.name())) {
                    picked.add(m);
                }
            }
        }

        // ④ 兜底：按发现顺序补齐（尽量给满直露名额，避免 tools 段空着）
        if (picked.size() < limit) {
            for (SkillMeta m : all) {
                if (picked.size() >= limit) break;
                if (pickedNames.add(m.name())) {
                    picked.add(m);
                }
            }
        }

        return picked;
    }

    /** 会话内最近调用的默认预留名额。 */
    static final int RECENT_QUOTA = 2;
}