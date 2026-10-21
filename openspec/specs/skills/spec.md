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

系统 SHALL 按配置的 `agent.skill.mode` 决定哪些 skill 以 `Tool` 形式暴露给模型：`all` 时全部 skill 注册为工具（工具名 = skill 名，描述 = 固定前缀加 frontmatter description，调用返回技能正文）；`search` 时不注册任何 skill 工具，仅注册 `skill_search`；`hybrid` 时注册至多 `hybrid-direct-limit` 个热门 skill 工具与 `skill_search`。三种档位下 skill 的工具属性契约（工具名、描述前缀、空参数 schema、只读、免确认）保持不变。

#### Scenario: all 档全部注册为工具

- **GIVEN** `agent.skill.mode` 为 `all` 且发现 5 个 skill
- **WHEN** 系统构建工具集
- **THEN** 5 个 skill 各注册一个同名工具
- **AND** 不注册 `skill_search`

#### Scenario: search 档不注册 skill 工具

- **GIVEN** `agent.skill.mode` 为 `search`（缺省）且发现 5 个 skill
- **WHEN** 系统构建工具集
- **THEN** 不注册任何 skill 工具
- **AND** 注册恰好一个 `skill_search` 工具

#### Scenario: hybrid 档只注册热门 skill

- **GIVEN** `agent.skill.mode` 为 `hybrid`、`hybrid-direct-limit` 为 10，且发现 500 个 skill
- **WHEN** 系统构建工具集
- **THEN** 注册的 skill 工具数量不超过 10
- **AND** 另注册一个 `skill_search` 工具

#### Scenario: 调用 skill 工具返回正文

- **WHEN** 模型调用某个已注册的 skill 工具
- **THEN** 工具结果返回该 skill 的正文内容

#### Scenario: skill 工具属性不变

- **WHEN** 系统查询任一 skill 工具的权限属性
- **THEN** `isReadOnly()` 为 `true`
- **AND** `checkPermissions()` 返回 `allow`
- **AND** `inputSchema()` 为空参数对象

#### Scenario: 无技能时不注册 skill 工具

- **WHEN** 未发现任何技能
- **THEN** 不注册任何 skill 工具，也不注册 `skill_search`
- **AND** 主流程正常

### Requirement: SkillCatalog 作为 Plugin

系统 SHALL 把技能索引包装为 Plugin（实现 `Plugin` + `ToolProvider` + `SystemPromptFragment`），由 Plugin 提供的 `tools()` 按 `agent.skill.mode` 返回该档位对应的工具集合，行为与直接调用技能目录保持一致。

#### Scenario: 两级技能目录均被扫描

- **GIVEN** 用户级或项目级技能目录存在技能
- **WHEN** Plugin 初始化
- **THEN** 技能索引包含两级目录发现的全部 skill

#### Scenario: 无技能目录不报错

- **GIVEN** 两级技能目录都不存在
- **WHEN** Plugin 初始化
- **THEN** 不注册任何 skill 相关工具
- **AND** 主流程正常

### Requirement: Skill 工具注册与摘要注入

系统 SHALL 通过 `SystemPromptFragment.fragment()` 向 system prompt 注入**静态的技能使用说明**（三 scope 目录位置 + 技能以工具暴露、长尾用 `skill_search` 检索），而非全量技能列表；当轮可能相关的技能候选名单 SHALL 由对话循环按当轮用户提问动态渲染，并以「技能名 + 截断描述」列表追加到 system prompt 末尾。

#### Scenario: fragment 输出静态说明

- **WHEN** 系统渲染 Plugin 的 system prompt 片段
- **THEN** 输出包含 USER / PROJECT / LOCAL 三 scope 的目录位置说明
- **AND** 说明技能以工具形式暴露、长尾可通过 `skill_search` 检索
- **AND** 不输出全量技能列表

#### Scenario: 无技能时片段为空

- **WHEN** 未发现任何技能
- **THEN** 片段返回空字符串，不注入空说明

#### Scenario: 候选名单按当轮提问渲染

- **GIVEN** 发现若干 skill，当轮用户提问为「帮我填 PDF 表单」
- **WHEN** 系统组装本轮 system prompt
- **THEN** prompt 末尾包含「可能相关的技能」小节
- **AND** 该小节列出与提问匹配的技能名与截断描述
- **AND** 描述长度不超过配置的截断字符数

#### Scenario: 无候选时不注入小节

- **GIVEN** 当轮提问与任何 skill 都无关键词 / 语义关联
- **WHEN** 系统组装本轮 system prompt
- **THEN** 不注入「可能相关的技能」小节

#### Scenario: 提问为空时不注入小节

- **GIVEN** 本轮对话历史中没有用户消息
- **WHEN** 系统组装本轮 system prompt
- **THEN** 不注入「可能相关的技能」小节

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

### Requirement: skill_search 检索工具

系统 SHALL 提供 `skill_search` 工具，接受一个字符串参数 `query`，按关键词与语义检索可用 skill，返回匹配技能的「技能名 + 描述」列表；该工具在 `all` 档不注册，在 `search` 与 `hybrid` 档注册。检索失败（索引不可用）时 SHALL 返回空列表而不抛异常。

#### Scenario: 检索命中返回候选

- **GIVEN** 存在名为 `pdf-form-filler` 的 skill，其描述含「填充 PDF 表单」
- **WHEN** 模型调用 `skill_search` 且 `query` 为 `pdf 表单`
- **THEN** 返回结果中包含 `pdf-form-filler`
- **AND** 每条结果含技能名与描述

#### Scenario: 可多次调用精化检索

- **WHEN** 模型连续两次调用 `skill_search`，使用不同 `query`
- **THEN** 两次分别按各自 `query` 返回候选
- **AND** 第二次的候选可与第一次不同

#### Scenario: 无命中返回空列表

- **GIVEN** 无任何 skill 与 `query` 匹配
- **WHEN** 模型调用 `skill_search`
- **THEN** 返回空列表文本
- **AND** 不抛异常

#### Scenario: 索引不可用时降级

- **GIVEN** 向量索引与倒排索引均不可用
- **WHEN** 模型调用 `skill_search`
- **THEN** 返回空列表文本
- **AND** 不抛异常，主流程继续

### Requirement: 技能按需暴露三档模式

系统 SHALL 支持 `agent.skill.mode` 三档配置：`all`（全部 skill 直露为工具，不限制数量）、`search`（不直露任何 skill，仅 `skill_search`）、`hybrid`（按热门规则直露至多 `hybrid-direct-limit` 个 + `skill_search`）。缺省为 `search`。非法档位值 SHALL 回退到 `search` 并记录 WARN，不阻断启动。

#### Scenario: 缺省为 search 档

- **GIVEN** 配置中未显式设置 `agent.skill.mode`
- **WHEN** 系统读取有效模式
- **THEN** 生效模式为 `search`

#### Scenario: 非法档位回退

- **GIVEN** `agent.skill.mode` 为 `bogus`
- **WHEN** 系统读取有效模式
- **THEN** 生效模式为 `search`
- **AND** 记录 WARN 日志说明非法值与回退结果

#### Scenario: 数值参数非法用默认值

- **GIVEN** `agent.skill.recall-k` 为 0 或负数
- **WHEN** 系统读取有效参数
- **THEN** 该参数取默认值 20
- **AND** 记录 WARN 日志

### Requirement: 热门技能选择与频次持久化

系统 SHALL 在 `hybrid` 档按固定配额挑选热门 skill 直露：`hybrid-project-quota` 个名额优先给项目级（PROJECT scope）skill，其余名额（`hybrid-direct-limit` 减去项目级名额）按使用频次指数衰减得分排序填充，再与会话内最近调用过的 skill 合并去重。频次使用频次 SHALL 持久化到 `~/.agent-demo/skill-usage.json`（原子写），得分 SHALL 为 `count × exp(-decayLambda × 距上次使用天数)`，缺省 `decay-lambda` 为 0.05。

#### Scenario: 项目级优先占配额

- **GIVEN** `hybrid-direct-limit` 为 10、`hybrid-project-quota` 为 6，存在 8 个项目级 skill 与若干用户级 skill
- **WHEN** 系统挑选热门 skill
- **THEN** 至少 6 个项目级 skill 入选（若存在）
- **AND** 直露总数不超过 10

#### Scenario: 项目级不足时由频次补位

- **GIVEN** `hybrid-project-quota` 为 6 但仅存在 2 个项目级 skill
- **WHEN** 系统挑选热门 skill
- **THEN** 项目级 skill 全部入选
- **AND** 剩余名额由频次得分最高的用户级 skill 填充

#### Scenario: 频次得分随时间衰减

- **GIVEN** skill A 调用 10 次且上次使用在 90 天前，skill B 调用 3 次且上周使用
- **WHEN** 系统按频次得分排序
- **THEN** B 的得分高于 A
- **AND** 排序结果中 B 先于 A

#### Scenario: 使用频次被持久化

- **GIVEN** 模型调用了 skill `my-skill`
- **WHEN** 该调用成功返回正文
- **THEN** `~/.agent-demo/skill-usage.json` 中 `my-skill` 的计数增加
- **AND** 文件写入为原子替换

#### Scenario: 频次文件缺失视为空

- **GIVEN** `~/.agent-demo/skill-usage.json` 不存在
- **WHEN** 系统读取频次
- **THEN** 视为全部 skill 得分 0，不抛异常

#### Scenario: 全部档位不挑热门

- **GIVEN** `agent.skill.mode` 为 `all`
- **WHEN** 系统构建工具集
- **THEN** 不执行热门挑选，不读取频次文件

