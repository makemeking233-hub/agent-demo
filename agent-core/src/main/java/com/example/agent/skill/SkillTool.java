package com.example.agent.skill;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolCategory;
import com.example.agent.tools.ToolResult;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 技能工具（add-skills-system change）：把 {@link Skill} 暴露为模型可调用的 {@link Tool}。
 *
 * <p>工具名 = skill 名，描述 = frontmatter description；模型调用时 {@link #execute} 返回技能正文，
 * 供模型基于技能内容行动。只读、无副作用（checkPermissions = allow）。
 */
public class SkillTool implements Tool<String, String> {
    private final Skill skill;

    /** 频次记录器（可空 = 不记录；add-skill-demand-loading T6.8）。 */
    private final SkillUsageStore usageStore;

    /**
     * 构造技能工具（不记录频次）。
     *
     * @param skill 技能
     */
    public SkillTool(Skill skill) {
        this(skill, null);
    }

    /**
     * 构造技能工具（成功调用时记录频次）。
     *
     * @param skill      技能
     * @param usageStore 频次存储（可空）
     */
    public SkillTool(Skill skill, SkillUsageStore usageStore) {
        this.skill = skill;
        this.usageStore = usageStore;
    }

    @Override
    public String name() {
        return skill.name();
    }

    @Override
    public String description() {
        return "技能：调用以加载技能内容。" + skill.description();
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(),
                "required",
                List.of());
    }

    @Override
    public boolean isReadOnly(String input) {
        return true;
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.READ;
    }

    @Override
    public PermissionDecision checkPermissions(String input, Tool.ToolContext ctx) {
        return PermissionDecision.allow();
    }

    @Override
    public String renderUse(String input) {
        return "Skill(" + skill.name() + ")";
    }

    @Override
    public String renderResult(String output) {
        return output;
    }

    @Override
    public String parseArguments(String argumentsJson) {
        return argumentsJson;
    }

    @Override
    public Mono<ToolResult<String>> execute(String input, Tool.ToolContext ctx) {
        // T2 重构：正文改为调用时按需读盘（skill.body()），不再由构造期常驻内存
        return Mono.fromSupplier(
                () -> {
                    String body = skill.body();
                    // T6.8：成功加载才计入频次（读失败不计，避免把错误调用当成热门依据）
                    if (usageStore != null && body != null && !body.startsWith("[skill ")) {
                        usageStore.record(skill.name());
                    }
                    return ToolResult.ok(body);
                });
    }

    /** @return 源技能。 */
    public Skill skill() {
        return skill;
    }
}
