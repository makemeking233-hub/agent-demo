package com.example.agent.skill;

import java.nio.file.Path;

/**
 * 技能元数据（refactor-skill-index-layer T2.1 引入；常驻索引结构，不含正文）。
 *
 * <p>启动期只读 {@code SKILL.md} 的 frontmatter 得到本结构；正文由 {@link Skill#body()} 按需读盘。
 * 这样 10000 个 skill 的常驻堆从「元数据 + 全部正文」降到「仅元数据」。
 *
 * @param name        技能名（frontmatter 的 name，kebab-case）
 * @param description 技能描述（frontmatter 的 description，模型据此判断何时调用）
 * @param scope       技能作用域（USER 跨项目 / PROJECT 随工作目录；{@code null} 视为 {@link SkillScope#USER}）
 * @param dir         技能所在目录（{@code <root>/<name>/}）
 */
public record SkillMeta(String name, String description, SkillScope scope, Path dir) {

    /** 技能文件名常量（放在技能目录下）。 */
    public static final String SKILL_FILE = "SKILL.md";

    /** 紧凑构造：校验 name / description 非空白、dir 非空，并把 {@code null} scope 归一为 USER。 */
    public SkillMeta {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("skill name 不可为空");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("skill description 不可为空");
        }
        if (dir == null) {
            throw new IllegalArgumentException("skill dir 不可为空");
        }
        if (scope == null) {
            scope = SkillScope.USER;
        }
    }

    /**
     * 该技能的 {@code SKILL.md} 路径。
     *
     * @return {@code <dir>/SKILL.md}
     */
    public Path skillFilePath() {
        return dir.resolve(SKILL_FILE);
    }
}