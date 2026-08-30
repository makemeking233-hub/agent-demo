package com.example.agent.skill;

import java.nio.file.Path;

/**
 * 一个技能（Skill）：由 {@code SKILL.md} 定义，可被模型作为工具调用。
 *
 * @param name        技能名（frontmatter 的 name，kebab-case）
 * @param description 技能描述（frontmatter 的 description，模型据此判断何时调用）
 * @param content     技能正文（{@code ---} frontmatter 之后的内容）
 * @param dir         技能所在目录（{@code <root>/<name>/}）
 */
public record Skill(String name, String description, String content, Path dir) {}
