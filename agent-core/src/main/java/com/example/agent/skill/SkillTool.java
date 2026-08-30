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

    /**
     * 构造技能工具。
     *
     * @param skill 技能
     */
    public SkillTool(Skill skill) {
        this.skill = skill;
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
        return Mono.just(ToolResult.ok(skill.content()));
    }

    /** @return 源技能。 */
    public Skill skill() {
        return skill;
    }
}
