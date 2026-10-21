package com.example.agent.plugin.skill;

import com.example.agent.config.AgentConfig;
import com.example.agent.plugin.ExtensionPoints;
import com.example.agent.plugin.Plugin;
import com.example.agent.plugin.PluginContext;
import com.example.agent.skill.HotSkillSelector;
import com.example.agent.skill.SkillMeta;
import com.example.agent.skill.SkillPool;
import com.example.agent.skill.SkillSearchTool;
import com.example.agent.skill.SkillTool;
import com.example.agent.skill.SkillUsageStore;
import com.example.agent.tools.Tool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 技能插件（add-plugin-system v1.0 / add-skill-demand-loading T6 改造）。
 *
 * <p><b>tools() 按 {@code agent.skill.mode} 三分支</b>：
 *
 * <ul>
 *   <li>{@code ALL}：全部 skill 直露为 {@link SkillTool}（不限制，等价重构前行为），不注册检索工具
 *   <li>{@code SEARCH}（默认）：不直露任何 skill，仅注册 {@link SkillSearchTool}
 *   <li>{@code HYBRID}：按 {@link HotSkillSelector} 挑至多 {@code hybridDirectLimit} 个直露
 *       + 注册 {@link SkillSearchTool}
 * </ul>
 *
 * <p><b>fragment() 改静态说明</b>（T6.2）：Plugin 接口的 {@code fragment()} 是启动期常量，无法承载
 * 每轮变化的候选名单。故此处只输出目录位置 + 用法说明；当轮候选名单由 {@code AgentLoop} 通过
 * {@link com.example.agent.skill.SkillSectionProvider} 每轮渲染到 system prompt 末尾。
 *
 * <p><b>为什么 tools() 只在启动期算一次</b>：{@code PluginManager.init()} 只调一次。HYBRID 档的
 * 「会话内最近调用」会随时间变化，那部分由 {@code AgentLoop.toRequest()} 每轮动态追加，不在此处。
 */
public class SkillsPlugin
        implements Plugin, ExtensionPoints.ToolProvider, ExtensionPoints.SystemPromptFragment {

    private final List<Path> roots;

    /** 技能池（init 后可用；默认取全局单例）。 */
    private SkillPool pool = SkillPool.instance();

    /** 可选的频次存储（HYBRID 档热门挑选用；null = 无频次信息）。 */
    private SkillUsageStore usageStore;

    /** 可选配置（null = 用默认 search 档）。 */
    private AgentConfig.Skill config = AgentConfig.Skill.defaults();

    /** 默认构造：init 时用默认技能根目录（用户级 + 项目级）。 */
    public SkillsPlugin() {
        this.roots = null;
    }

    /** 测试构造：注入指定技能根目录。 */
    public SkillsPlugin(List<Path> roots) {
        this.roots = roots;
    }

    /**
     * 注入配置与频次存储（生产路径由装配方调用；不调则用默认 search 档、无频次）。
     *
     * @param config     技能配置（{@code null} 用默认）
     * @param usageStore 频次存储（可空）
     * @return this（便于链式调用）
     */
    public SkillsPlugin withConfig(AgentConfig.Skill config, SkillUsageStore usageStore) {
        this.config = config != null ? config : AgentConfig.Skill.defaults();
        this.usageStore = usageStore;
        return this;
    }

    @Override
    public void init(PluginContext ctx) {
        List<Path> r = roots != null ? roots : defaultRoots();
        SkillPool current = SkillPool.instance();
        // 单例为空（AgentLoopFactory 未跑 / 测试环境）时本地 bootstrap；
        // 非空则复用，避免双份扫描
        if (current.all().isEmpty()) {
            current = SkillPool.bootstrap(r);
        }
        this.pool = current;
    }

    @Override
    public List<Tool<?, ?>> tools() {
        List<Tool<?, ?>> out = new ArrayList<>();
        if (pool == null || pool.all().isEmpty()) return out;
        switch (config.mode()) {
            case ALL -> {
                for (SkillMeta meta : pool.all()) {
                    pool.load(meta.name()).ifPresent(s -> out.add(new SkillTool(s)));
                }
            }
            case SEARCH -> out.add(new SkillSearchTool(pool, config.recallK()));
            case HYBRID -> {
                List<SkillMeta> hot =
                        new HotSkillSelector(pool.all(), usageStore, List::of)
                                .pick(config.hybridDirectLimit(), config.hybridProjectQuota());
                for (SkillMeta meta : hot) {
                    pool.load(meta.name()).ifPresent(s -> out.add(new SkillTool(s)));
                }
                out.add(new SkillSearchTool(pool, config.recallK()));
            }
        }
        return out;
    }

    @Override
    public String fragment() {
        // T6.2：静态说明（候选名单由 AgentLoop 的 SkillSectionProvider 每轮渲染）
        if (pool == null || pool.all().isEmpty()) return "";
        return "# 技能（Skills）\n"
                + "- USER：`~/.agent-demo/skills/`（跨项目）\n"
                + "- PROJECT：`<cwd>/.agent-demo/skills/`（随项目）\n\n"
                + "技能以工具形式暴露；长尾技能可通过 `skill_search` 按关键词检索，"
                + "找到后用同名工具加载完整指令。";
    }

    /** 默认技能根目录（与 buildTools 一致）。 */
    public static List<Path> defaultRoots() {
        // fix-agent-home-isolation：基目录统一走 AgentPaths（原先不认系统属性 agent.demo.home）
        String userHome = com.example.agent.config.AgentPaths.homeBase();
        String cwd = System.getProperty("user.dir");
        return List.of(
                Paths.get(userHome, ".agent-demo", "skills"),
                Paths.get(cwd, ".agent-demo", "skills"));
    }
}