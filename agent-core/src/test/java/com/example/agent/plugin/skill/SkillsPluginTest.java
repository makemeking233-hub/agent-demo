package com.example.agent.plugin.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.config.AgentConfig;
import com.example.agent.plugin.PluginManager;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillsPluginTest {

    @TempDir Path tmp;

    private Path writeSkill(Path root, String name, String content) throws Exception {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), content);
        return dir;
    }

    @Test
    void registersTwoSkillTools_andFragmentContainsBothNames() throws Exception {
        Path root = tmp.resolve("skills");
        writeSkill(root, "alpha", "---\nname: alpha\ndescription: 技能 A\n---\n步骤 A1\n");
        writeSkill(root, "beta", "---\nname: beta\ndescription: 技能 B\n---\n步骤 B1\n");

        SkillsPlugin plugin = new SkillsPlugin(List.of(root));
        PluginManager pm = new PluginManager(List.of(plugin), AgentConfig.defaults(), new ToolRegistry());
        pm.init();

        List<Tool<?, ?>> tools = pm.collectTools();
        assertEquals(2, tools.size(), "应从技能目录注册 2 个 SkillTool");
        assertTrue(tools.stream().anyMatch(t -> t.name().equals("alpha")));
        assertTrue(tools.stream().anyMatch(t -> t.name().equals("beta")));

        String frag = pm.collectSystemPromptFragment();
        assertTrue(frag.contains("alpha"), "fragment 应含技能名 alpha");
        assertTrue(frag.contains("beta"), "fragment 应含技能名 beta");
    }

    @Test
    void fragmentEmptyWhenNoSkills() {
        SkillsPlugin plugin = new SkillsPlugin(List.of(tmp.resolve("no-such-dir")));
        PluginManager pm = new PluginManager(List.of(plugin), AgentConfig.defaults(), new ToolRegistry());
        pm.init();
        assertEquals("", pm.collectSystemPromptFragment(), "无技能时 fragment 应为空");
        assertEquals(0, pm.collectTools().size());
    }
}
