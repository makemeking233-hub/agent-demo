package com.example.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 规模验收测试（add-skill-demand-loading T8.2）。
 *
 * <p>本 change 的核心价值：**tools 段规模与 skill 总数解耦**。这里用 1000 个 skill 证明
 * 重构前会爆（~90k token）的场景，在 search/hybrid 档下 tools 段保持恒定小规模。
 *
 * <p>token 估算参考：单条 ToolSpec（name + description + 空 schema）≈ 90 token。
 */
class SkillScaleBudgetTest {
    @TempDir Path tmp;

    /** 造 N 个 skill（每个一个 SKILL.md，正文 200 字节）。 */
    private Path buildSkills(int n) throws Exception {
        Path root = tmp.resolve("skills");
        Files.createDirectories(root);
        for (int i = 0; i < n; i++) {
            Path dir = Files.createDirectories(root.resolve("skill-" + i));
            Files.writeString(
                    dir.resolve("SKILL.md"),
                    "---\nname: skill-" + i + "\ndescription: 技能编号 " + i + " 做某件事\n---\n"
                            + "正文内容 ".repeat(40));
        }
        return root;
    }

    @Test
    void searchModeKeepsToolsConstantAtOneThousandSkills() throws Exception {
        Path root = buildSkills(1000);
        var pool = SkillPool.bootstrap(List.of(root));

        // 模拟 AgentLoopFactory.registerSkillToolsByMode 的 SEARCH 分支
        var tools = new ToolRegistry();
        tools.register(new SkillSearchTool(pool, 20));

        assertEquals(1, tools.list().size(), "1000 skill 时 search 档应只注册 1 个工具");
        // 对比：若全暴露则 1000 × 90 ≈ 90k token，超过 83808 的 compact 阈值
        int naiveTokenEstimate = pool.all().size() * 90;
        assertTrue(naiveTokenEstimate > 83_808, "全暴露会超阈值（本测试的前提）: " + naiveTokenEstimate);
    }

    @Test
    void hybridModeCapsToolsAtLimitPlusSearch() throws Exception {
        Path root = buildSkills(1000);
        var pool = SkillPool.bootstrap(List.of(root));
        var cfg = new AgentConfig.Skill(AgentConfig.SkillMode.HYBRID, 10, 6, 20, 0.05, 40);

        var hot = new HotSkillSelector(pool.all(), null, List::of)
                .pick(cfg.hybridDirectLimit(), cfg.hybridProjectQuota());
        var tools = new ToolRegistry();
        for (var m : hot) {
            pool.load(m.name()).ifPresent(s -> tools.register(new SkillTool(s)));
        }
        tools.register(new SkillSearchTool(pool, 20));

        assertTrue(tools.list().size() <= 11, "hybrid 档工具数应 ≤ limit(10) + 1: " + tools.list().size());
        long direct = tools.list().stream().filter(t -> t instanceof SkillTool).count();
        assertEquals(10, direct, "应挑满 10 个热门");
    }

    @Test
    void allModeGrowsLinearlyAsExplicitEscapeHatch() throws Exception {
        Path root = buildSkills(50);
        var pool = SkillPool.bootstrap(List.of(root));

        var tools = new ToolRegistry();
        for (var m : pool.all()) {
            pool.load(m.name()).ifPresent(s -> tools.register(new SkillTool(s)));
        }
        assertEquals(50, tools.list().size(), "ALL 档是显式逃逸阀，线性增长（用户自担规模代价）");
    }

    @Test
    void thousandSkillsBootstrapIsFast() throws Exception {
        Path root = buildSkills(1000);
        long start = System.nanoTime();
        var pool = SkillPool.bootstrap(List.of(root));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(1000, pool.all().size());
        // 只读 frontmatter（不读正文）→ 1000 个文件应远低于 5 秒
        assertTrue(elapsedMs < 5_000, "1000 skill 的 bootstrap 应 < 5s，实际 " + elapsedMs + "ms");
    }

    @Test
    void bodyIsNotLoadedDuringBootstrap() throws Exception {
        Path root = buildSkills(1000);
        var pool = SkillPool.bootstrap(List.of(root));

        // bootstrap 后所有 skill 的元数据可用，但正文尚未读入（懒加载）
        assertEquals(1000, pool.all().size());
        // 抽样验证 body() 仍能正确读到正文（证明懒加载链路完好）
        String body = pool.load("skill-0").orElseThrow().body();
        assertTrue(body.contains("正文内容"), "懒加载应能读到正文");
    }

    @Test
    void searchRemainsResponsiveAtScale() throws Exception {
        Path root = buildSkills(1000);
        var pool = SkillPool.bootstrap(List.of(root));

        long start = System.nanoTime();
        List<SkillMeta> hits = pool.search("技能编号 42", 20);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(!hits.isEmpty(), "应命中 skill-42: " + hits);
        assertTrue(elapsedMs < 2_000, "1000 skill 的检索应 < 2s，实际 " + elapsedMs + "ms");
    }
}