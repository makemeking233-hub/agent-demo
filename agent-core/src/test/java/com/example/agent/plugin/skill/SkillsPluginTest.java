package com.example.agent.plugin.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;
import com.example.agent.plugin.PluginManager;
import com.example.agent.skill.SkillPool;
import com.example.agent.skill.SkillSearchTool;
import com.example.agent.skill.SkillTool;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SkillsPlugin 单元测试（add-skill-demand-loading T6）。
 *
 * <p>核心变化：{@code tools()} 从「恒注册 N 个 SkillTool」改为按 {@code agent.skill.mode} 三分支。
 * 本测试按 T6.9 覆盖三档各自的 tools 形状 + fragment 静态说明。
 */
class SkillsPluginTest {

    @TempDir Path tmp;

    @AfterEach
    void resetPool() {
        SkillPool.resetForTest();
    }

    private Path writeSkill(Path root, String name, String desc) throws Exception {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("SKILL.md"), "---\nname: " + name + "\ndescription: " + desc + "\n---\n正文\n");
        return dir;
    }

    private PluginManager initWith(Path root, AgentConfig.Skill cfg) {
        SkillsPlugin plugin = new SkillsPlugin(List.of(root));
        plugin.withConfig(cfg, null);
        PluginManager pm =
                new PluginManager(List.of(plugin), AgentConfig.defaults(), new ToolRegistry());
        pm.init();
        return pm;
    }

    @Test
    void allModeRegistersEverySkillTool() throws Exception {
        Path root = tmp.resolve("skills");
        writeSkill(root, "alpha", "技能 A");
        writeSkill(root, "beta", "技能 B");

        var cfg = new AgentConfig.Skill(AgentConfig.SkillMode.ALL, 10, 6, 20, 0.05, 40);
        List<Tool<?, ?>> tools = initWith(root, cfg).collectTools();

        assertEquals(2, tools.size(), "ALL 档应注册 2 个 SkillTool");
        assertTrue(tools.stream().allMatch(t -> t instanceof SkillTool));
        assertTrue(tools.stream().anyMatch(t -> t.name().equals("alpha")));
        assertTrue(tools.stream().anyMatch(t -> t.name().equals("beta")));
    }

    @Test
    void searchModeRegistersOnlySearchTool() throws Exception {
        Path root = tmp.resolve("skills");
        writeSkill(root, "alpha", "技能 A");
        writeSkill(root, "beta", "技能 B");

        // 默认档即 search
        List<Tool<?, ?>> tools = initWith(root, AgentConfig.Skill.defaults()).collectTools();

        assertEquals(1, tools.size(), "SEARCH 档应只注册 1 个 skill_search");
        assertTrue(tools.get(0) instanceof SkillSearchTool);
        assertEquals("skill_search", tools.get(0).name());
    }

    @Test
    void hybridModeRegistersHotSkillsPlusSearch() throws Exception {
        Path root = tmp.resolve("skills");
        for (int i = 0; i < 15; i++) {
            writeSkill(root, "s" + i, "技能 " + i);
        }

        var cfg = new AgentConfig.Skill(AgentConfig.SkillMode.HYBRID, 5, 2, 20, 0.05, 40);
        List<Tool<?, ?>> tools = initWith(root, cfg).collectTools();

        long direct = tools.stream().filter(t -> t instanceof SkillTool).count();
        long search = tools.stream().filter(t -> t instanceof SkillSearchTool).count();
        assertEquals(5, direct, "HYBRID 档直露数应等于 hybridDirectLimit");
        assertEquals(1, search, "HYBRID 档应额外注册 1 个 skill_search");
        assertEquals(6, tools.size());
    }

    @Test
    void fragmentIsStaticUsageDocNotSkillList() throws Exception {
        Path root = tmp.resolve("skills");
        writeSkill(root, "alpha", "技能 A");
        writeSkill(root, "beta", "技能 B");

        String frag = initWith(root, AgentConfig.Skill.defaults()).collectSystemPromptFragment();

        assertTrue(frag.contains("~/.agent-demo/skills/") || frag.contains("skills/"), "应说明目录位置");
        assertTrue(frag.contains("skill_search"), "应说明检索用法");
        // T6.2 核心：fragment 不再罗列技能名（候选名单改由 AgentLoop 每轮渲染）
        assertTrue(!frag.contains("- `alpha`"), "fragment 不应再罗列具体技能: " + frag);
    }

    @Test
    void fragmentEmptyWhenNoSkills() {
        SkillsPlugin plugin = new SkillsPlugin(List.of(tmp.resolve("no-such-dir")));
        PluginManager pm =
                new PluginManager(List.of(plugin), AgentConfig.defaults(), new ToolRegistry());
        pm.init();
        assertEquals("", pm.collectSystemPromptFragment(), "无技能时 fragment 应为空");
        assertEquals(0, pm.collectTools().size());
    }
}