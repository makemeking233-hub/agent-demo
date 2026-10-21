package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * HotSkillSelector 单元测试（add-skill-demand-loading T4）。
 *
 * <p>覆盖：PROJECT 优先占配额、不足时频次补位、频次排序、最近调用合并、去重截断。
 */
class HotSkillSelectorTest {
    @TempDir Path tmp;

    private static SkillMeta meta(String name, SkillScope scope) {
        return new SkillMeta(name, "desc of " + name, scope, Path.of("/tmp/" + name));
    }

    private static HotSkillSelector selectorWith(
            List<SkillMeta> all, SkillUsageStore usage, List<String> recentlyUsed) {
        return new HotSkillSelector(all, usage, () -> recentlyUsed);
    }

    @Test
    void projectSkillsTakeQuotaFirst() {
        List<SkillMeta> all =
                List.of(
                        meta("u1", SkillScope.USER),
                        meta("u2", SkillScope.USER),
                        meta("p1", SkillScope.PROJECT),
                        meta("p2", SkillScope.PROJECT));
        var sel = selectorWith(all, new SkillUsageStore(tmp.resolve("u.json")), List.of());
        List<SkillMeta> picked = sel.pick(4, 2);

        long projectCount = picked.stream().filter(m -> m.scope() == SkillScope.PROJECT).count();
        assertTrue(projectCount >= 2, "PROJECT 应至少占 2 个名额: " + picked);
    }

    @Test
    void projectShortageFilledByFrequency() {
        List<SkillMeta> all =
                List.of(meta("p1", SkillScope.PROJECT), meta("u1", SkillScope.USER), meta("u2", SkillScope.USER));
        var usage = new SkillUsageStore(tmp.resolve("u.json"));
        usage.record("u1");
        usage.record("u1");
        var sel = selectorWith(all, usage, List.of());
        List<SkillMeta> picked = sel.pick(3, 2);

        assertEquals(3, picked.size(), "PROJECT 不足时应用频次补满配额");
        assertTrue(picked.stream().anyMatch(m -> m.name().equals("p1")), "PROJECT 必入选");
        assertTrue(picked.stream().anyMatch(m -> m.name().equals("u1")), "高频用户级应补位");
    }

    @Test
    void highFrequencyRankedFirstAmongUserSkills() {
        List<SkillMeta> all =
                List.of(meta("low", SkillScope.USER), meta("high", SkillScope.USER));
        var usage = new SkillUsageStore(tmp.resolve("u.json"));
        for (int i = 0; i < 5; i++) usage.record("high");
        usage.record("low");
        var sel = selectorWith(all, usage, List.of());
        List<SkillMeta> picked = sel.pick(1, 0);

        assertEquals(1, picked.size());
        assertEquals("high", picked.get(0).name(), "高频应优先");
    }

    @Test
    void recentlyUsedAreIncluded() {
        List<SkillMeta> all =
                List.of(meta("a", SkillScope.USER), meta("b", SkillScope.USER), meta("c", SkillScope.USER));
        var sel = selectorWith(all, new SkillUsageStore(tmp.resolve("u.json")), List.of("c"));
        List<SkillMeta> picked = sel.pick(2, 0);

        assertTrue(picked.stream().anyMatch(m -> m.name().equals("c")), "最近用过的应入选: " + picked);
    }

    @Test
    void noDuplicatesWhenOverlapping() {
        List<SkillMeta> all = List.of(meta("a", SkillScope.PROJECT));
        var usage = new SkillUsageStore(tmp.resolve("u.json"));
        usage.record("a");
        var sel = selectorWith(all, usage, List.of("a"));
        List<SkillMeta> picked = sel.pick(5, 3);

        assertEquals(1, picked.size(), "同一 skill 不应重复出现");
    }

    @Test
    void truncatesToLimit() {
        List<SkillMeta> all = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            all.add(meta("s" + i, SkillScope.USER));
        }
        var sel = selectorWith(all, new SkillUsageStore(tmp.resolve("u.json")), List.of());
        assertEquals(5, sel.pick(5, 3).size(), "应截断到 limit");
    }

    @Test
    void emptyPoolReturnsEmpty() {
        var sel = selectorWith(List.of(), new SkillUsageStore(tmp.resolve("u.json")), List.of());
        assertTrue(sel.pick(10, 6).isEmpty());
    }

    @Test
    void limitZeroReturnsEmpty() {
        List<SkillMeta> all = List.of(meta("a", SkillScope.USER));
        var sel = selectorWith(all, new SkillUsageStore(tmp.resolve("u.json")), List.of());
        assertTrue(sel.pick(0, 0).isEmpty());
    }

    @Test
    void nullUsageStoreIsTolerated() {
        List<SkillMeta> all = List.of(meta("p1", SkillScope.PROJECT), meta("u1", SkillScope.USER));
        var sel = new HotSkillSelector(all, null, () -> List.of());
        List<SkillMeta> picked = sel.pick(2, 1);
        assertEquals(2, picked.size(), "usage 为 null 时应仍能按 PROJECT 优先挑选");
    }

    @Test
    void nullRecentlyUsedSupplierIsTolerated() {
        List<SkillMeta> all = List.of(meta("a", SkillScope.USER));
        var sel = new HotSkillSelector(all, new SkillUsageStore(tmp.resolve("u.json")), null);
        assertEquals(1, sel.pick(1, 0).size());
    }
}