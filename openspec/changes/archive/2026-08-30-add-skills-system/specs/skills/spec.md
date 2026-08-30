# skills Specification (delta)

> 本文件是 `add-skills-system` 的 delta spec。在 archive 时合并到 `openspec/specs/skills/spec.md`。

## ADDED Requirements

### Requirement: Skill 发现

系统 SHALL 从两个根目录发现技能：`~/.agent-demo/skills/`（用户级）与项目 `.agent-demo/skills/`（项目级）。发现规则为一级深度：`<root>/<name>/SKILL.md` 或 `<root>/<name>.md`（`<name>` 为技能目录/文件名），嵌套子目录不识别。

#### Scenario: 用户级 skill 被发现

- **WHEN** `~/.agent-demo/skills/my-skill/SKILL.md` 存在
- **THEN** 该系统发现并加载 `my-skill` 技能

#### Scenario: 项目级 skill 被发现

- **WHEN** 当前工作目录下 `.agent-demo/skills/another-skill/SKILL.md` 存在
- **THEN** 系统同时发现并加载 `another-skill` 技能

#### Scenario: 无 skill 目录不报错

- **WHEN** 两个 skill 根目录都不存在
- **THEN** 技能列表为空，不抛异常，不影响主流程

### Requirement: Skill frontmatter 解析

系统 SHALL 解析 `SKILL.md` 顶部 frontmatter（由 `---` 包裹），读取必填的 `name`（kebab-case）与 `description`；缺失或格式错误时跳过该技能（记录 WARN），正文作为技能内容保留。

#### Scenario: frontmatter 含 name 与 description

- **WHEN** `SKILL.md` 顶部是 frontmatter 且含 `name`/`description`
- **THEN** 系统解析出技能名与描述，正文作为技能内容

#### Scenario: frontmatter 缺失则跳过

- **WHEN** `SKILL.md` 无有效 frontmatter
- **THEN** 该技能被跳过（不暴露给模型），记录 WARN

### Requirement: Skill 作为工具

系统 SHALL 把每个发现的技能注册为一个 `Tool`：工具名 = skill 名，描述 = frontmatter description；模型调用时 `execute` 返回技能正文内容。

#### Scenario: 技能暴露为可调用工具

- **WHEN** 至少发现一个技能
- **THEN** 这些技能作为工具出现在工具列表中，模型可发起调用

#### Scenario: 调用技能返回正文

- **WHEN** 模型调用某技能工具
- **THEN** 工具结果返回该技能的正文内容，供模型基于技能内容行动

#### Scenario: 无技能时不注册任何 skill 工具

- **WHEN** 未发现任何技能
- **THEN** 工具表中不含 skill 工具（仅原有工具），主流程正常
