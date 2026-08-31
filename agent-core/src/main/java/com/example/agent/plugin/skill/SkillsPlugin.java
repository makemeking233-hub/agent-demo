package com.example.agent.plugin.skill;

import com.example.agent.plugin.ExtensionPoints;
import com.example.agent.plugin.Plugin;
import com.example.agent.plugin.PluginContext;
import com.example.agent.skill.Skill;
import com.example.agent.skill.SkillCatalog;
import com.example.agent.skill.SkillTool;
import com.example.agent.tools.Tool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 技能插件（add-plugin-system v1.0）：扫技能目录（用户级 + 项目级）发现技能, tools() 返回
 * {@link SkillTool} 列表, fragment() 返回技能列表摘要（拼到 system prompt 尾部）。
 *
 * <p>技能根目录与 {@code AgentLoopFactory.buildTools} 保持一致：{@code ~/.agent-demo/skills} +
 * {@code <cwd>/.agent-demo/skills}。无技能时 fragment 为空串（不影响主提示词）。
 *
 * <p>老 {@code ToolRegistry.registerSkillTools} 保留为 deprecated wrapper（见 T4.2）。
 */
public class SkillsPlugin implements Plugin, ExtensionPoints.ToolProvider, ExtensionPoints.SystemPromptFragment {

    private final List<Path> roots;
    private final List<Skill> skills = new ArrayList<>();

    /** 默认构造：init 时用默认技能根目录（用户级 + 项目级）。 */
    public SkillsPlugin() {
        this.roots = null;
    }

    /** 测试构造：注入指定技能根目录。 */
    public SkillsPlugin(List<Path> roots) {
        this.roots = roots;
    }

    @Override
    public void init(PluginContext ctx) {
        List<Path> r = roots != null ? roots : defaultRoots();
        skills.addAll(SkillCatalog.discover(r));
    }

    @Override
    public List<Tool<?, ?>> tools() {
        List<Tool<?, ?>> out = new ArrayList<>();
        for (Skill s : skills) {
            out.add(new SkillTool(s));
        }
        return out;
    }

    @Override
    public String fragment() {
        if (skills.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("# 可用技能\n");
        for (Skill s : skills) {
            sb.append("- `").append(s.name()).append("`：").append(s.description()).append("\n");
        }
        sb.append("\n调用对应技能工具以加载其内容后执行。");
        return sb.toString();
    }

    /** 默认技能根目录（与 buildTools 一致）。 */
    public static List<Path> defaultRoots() {
        String userHome = System.getenv("AGENT_DEMO_HOME") != null
                        && !System.getenv("AGENT_DEMO_HOME").isBlank()
                ? System.getenv("AGENT_DEMO_HOME")
                : System.getProperty("user.home");
        String cwd = System.getProperty("user.dir");
        return List.of(
                Paths.get(userHome, ".agent-demo", "skills"),
                Paths.get(cwd, ".agent-demo", "skills"));
    }
}
