package com.example.agent.skill;

/**
 * 技能作用域（refactor-skill-index-layer T2.1 引入）。
 *
 * <p>与长期记忆的 {@code MemoryScope} 同构：USER 为跨项目持久，PROJECT 随当前工作目录。
 */
public enum SkillScope {
    /** 跨项目持久，目录 {@code ~/.agent-demo/skills/}。 */
    USER,
    /** 随项目工作目录，目录 {@code <cwd>/.agent-demo/skills/}。 */
    PROJECT
}