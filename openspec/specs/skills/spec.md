# skills Specification

## Purpose
TBD - created by archiving change add-skills-system. Update Purpose after archive.
## Requirements
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

### Requirement: Skill 发现结果与重构前一致

系统 SHALL 保持既有两级目录发现规则与覆盖优先级不变：`~/.agent-demo/skills/`（用户级）与 `<cwd>/.agent-demo/skills/`（项目级），一级深度（`<root>/<name>/SKILL.md` 或 `<root>/<name>.md`），嵌套子目录不识别；同名 skill 以**后发现的根目录覆盖**先发现的（项目级覆盖用户级）。

#### Scenario: 两级目录均存在同名 skill

- **GIVEN** `~/.agent-demo/skills/my-skill/SKILL.md` 与 `<cwd>/.agent-demo/skills/my-skill/SKILL.md` 同时存在
- **WHEN** 系统发现技能
- **THEN** 结果中 `my-skill` 只有一个条目
- **AND** 其 `description` 来自项目级（后发现的根目录）

#### Scenario: 嵌套子目录不被识别

- **GIVEN** `~/.agent-demo/skills/outer/inner/SKILL.md`（两级嵌套）
- **WHEN** 系统发现技能
- **THEN** `inner` 不在发现结果中

#### Scenario: frontmatter 缺失时跳过并记 WARN

- **GIVEN** `~/.agent-demo/skills/bad-skill/SKILL.md` 顶部无 `---` 包裹的 frontmatter
- **WHEN** 系统发现技能
- **THEN** `bad-skill` 被跳过，不出现在结果中
- **AND** 记录 WARN 日志

### Requirement: SkillTool 对外契约不变

系统 SHALL 保持 `SkillTool` 的对外语义不变：工具名等于 skill 名；`description()` 等于固定前缀加 frontmatter description；`inputSchema()` 为空参数对象；`isReadOnly()` 返回 `true`；`checkPermissions()` 返回 `allow`；`execute()` 返回该 skill 的正文内容。

#### Scenario: 工具名与描述

- **GIVEN** frontmatter 为 `name: my-skill` + `description: 做某事`
- **WHEN** 系统构建该 skill 的 SkillTool
- **THEN** `name()` 返回 `my-skill`
- **AND** `description()` 返回包含 `做某事` 的文本

#### Scenario: 调用工具返回正文

- **WHEN** 模型调用该 skill 工具
- **THEN** 工具结果返回该 skill 的 `SKILL.md` 正文（frontmatter 之后的部分）

#### Scenario: 权限与只读属性不变

- **WHEN** 系统查询该工具的权限属性
- **THEN** `isReadOnly()` 为 `true`
- **AND** `checkPermissions()` 返回 `allow`

### Requirement: 正文按需加载而非启动期常驻

系统 SHALL 在启动期**只读取 frontmatter**（解析 name 与 description 所需的部分），不把 `SKILL.md` 正文读入常驻内存结构；正文 SHALL 在工具 `execute()` 被调用时才从磁盘读取。

#### Scenario: 启动期不读正文

- **GIVEN** 一个正文 100KB 的 `SKILL.md`
- **WHEN** 系统完成技能发现并构建索引
- **THEN** 常驻索引结构中不包含该 100KB 正文
- **AND** 索引中仅含 name 与 description 等元数据

#### Scenario: 调用时才读正文

- **WHEN** 模型调用该 skill 工具
- **THEN** 系统在此刻读取 `SKILL.md` 正文
- **AND** 返回正文内容作为工具结果

### Requirement: 正文缓存随文件变更失效

系统 SHALL 缓存已读正文，并以 `SKILL.md` 的最后修改时间（mtime）作为有效性判据；文件 mtime 变化后 SHALL 重新读盘，不得返回过期正文。

#### Scenario: 文件未变更时复用缓存

- **GIVEN** skill 正文已被读取一次并缓存
- **WHEN** 再次调用该 skill 工具且文件 mtime 未变
- **THEN** 返回缓存正文，不重复读盘

#### Scenario: 文件变更后重新读取

- **GIVEN** skill 正文已被读取一次并缓存
- **AND** 用户修改了该 `SKILL.md`（mtime 变化）
- **WHEN** 再次调用该 skill 工具
- **THEN** 返回修改后的正文
- **AND** 缓存被更新为新正文

### Requirement: 索引构建失败不阻断主流程

系统 SHALL 在索引构建（倒排索引 / embedding 索引）遇到任何失败时降级为可用状态，不抛异常中断技能发现或主对话。

#### Scenario: embedding 模型缺失

- **GIVEN** 本地 embedding 模型文件不存在
- **WHEN** 系统构建技能索引
- **THEN** embedding 索引构建被跳过
- **AND** 倒排索引仍可用
- **AND** 技能发现正常完成，不抛异常

#### Scenario: 正文读取失败

- **GIVEN** 某 skill 的 `SKILL.md` 在调用时被删除
- **WHEN** 模型调用该 skill 工具
- **THEN** 工具返回错误说明文本
- **AND** 不抛异常中断对话

