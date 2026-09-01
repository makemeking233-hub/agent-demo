# skills Specification (delta)

> 本文件是 `add-plugin-system` 的 delta spec。archive 时合并到 `openspec/specs/skills/spec.md`。

## ADDED Requirements

### Requirement: SkillCatalog 作为 Plugin

系统 SHALL 把 `SkillCatalog` 包装为 `SkillsPlugin`（implements `Plugin` + `ToolProvider` + `SystemPromptFragment`），扫描技能目录（用户级 `~/.agent-demo/skills/` 与项目级 `.agent-demo/skills/`），行为与直接调用 SkillCatalog 保持一致。

#### Scenario: 扫描两级技能目录

- **WHEN** 用户级或项目级技能目录存在技能
- **THEN** SkillsPlugin 发现这些技能并注册为工具

#### Scenario: 无技能目录不报错

- **WHEN** 两级技能目录都不存在
- **THEN** SkillsPlugin 不注册任何 skill 工具，主流程正常

### Requirement: Skill 工具注册与摘要注入

系统 SHALL 在 `SkillsPlugin.tools()` 返回 `SkillTool`（name = skill 名，description = frontmatter description），并通过 `SystemPromptFragment.fragment()` 返回技能列表摘要注入 system prompt。

#### Scenario: 技能注册为工具

- **WHEN** 至少发现一个技能
- **THEN** SkillsPlugin.tools() 返回对应的 SkillTool，系统注册进 ToolRegistry

#### Scenario: 技能列表摘要注入 system prompt

- **WHEN** 发现若干技能
- **THEN** SkillsPlugin.fragment() 返回包含技能名与描述的列表摘要，并追加进 system prompt

#### Scenario: 无技能时摘要为空

- **WHEN** 未发现任何技能
- **THEN** SkillsPlugin.fragment() 返回空字符串，不注入空摘要
