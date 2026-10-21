# refactor-skill-index-layer Specification

> 本 change 为**纯重构**：对外可观测行为不变。以下 Requirement 把「行为不变」这一约束显式固化为
> 可测试的契约，防止后续改动意外破坏 Skill 的既有语义。

## ADDED Requirements

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