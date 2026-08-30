## Context

agent-demo 目前只有固定工具集，无 Skills 系统。DSH 生态已有成熟 skill 约定（`<root>/<name>/SKILL.md`、一级深度、frontmatter name+description）。v0.4 规划 Skills。本 change 实现「Skill 作为工具」：发现 SKILL.md → 解析 frontmatter → 每个技能注册为 Tool，模型可调用以获取技能正文。

## Goals / Non-Goals

**Goals:**
- 从 `~/.agent-demo/skills/` 与项目 `.agent-demo/skills/` 发现技能（一级深度）。
- 解析 frontmatter（name/description），正文保留。
- 每个技能注册为一个 `Tool`，`execute` 返回正文。
- 无技能目录时优雅降级（空技能列表，不影响主流程）。

**Non-Goals:**
- 不做 skill 变更监听/watch（启动时一次发现即可，v0.2+ 可加 chokidar 式监听）。
- 不执行 skill 内脚本/命令（只返回正文内容供模型参考；脚本执行属后续 change）。
- 不做 skill 的 UI/安装市场。
- 不引入 YAML 依赖库（frontmatter 用简单文本解析，避免离线依赖）。

## Decisions

**D1: `Skill` record 承载技能元数据 + 正文。**
- `record Skill(String name, String description, String content, Path dir)`。
- frontmatter 解析出 name/description；content 为 `---` 之后正文。

**D2: `SkillCatalog.discover(List<Path> roots)` 扫描技能。**
- 对每个 root，遍历一级子目录：存在 `<name>/SKILL.md` 或 `<name>.md` 则读取；无 frontmatter 或 name/description 缺失时跳过（WARN）。
- 返回 `List<Skill>`。
- 备选：递归扫描。否决——一级深度是 DSH 约定，避免误发现嵌套资源文件。

**D3: `SkillTool implements Tool<String, String>`：skill 作为工具。**
- `name()` = skill.name()；`description()` = skill.description()（前置「调用以加载技能内容」提示）。
- `inputSchema()` 空 object（无参）；`parseArguments` 接受 `{}`。
- `execute` 返回 `ToolResult.ok(skill.content(), toolCallId)`。
- `isReadOnly` = true；`checkPermissions` = allow（只读加载技能内容，无副作用）。
- 备选：skill 内容直接注入 system prompt（不注册工具）。否决——用户要求"Skill 作为工具"，注册工具让模型显式调用。

**D4: `ToolRegistry.registerSkillTools(ToolRegistry, List<Skill>)`。**
- 逐个 `registry.register(new SkillTool(skill))`。
- `AgentLoopFactory.buildTools` 调用 `ToolRegistry.registerSkillTools(tools, SkillCatalog.discover(roots))`。

**D5: frontmatter 用 `---` 分隔 + 简单 key: value 解析。**
- 首行 `---`，到下一个 `---` 为 frontmatter；逐行 `key: value`。不引入 snakeyaml（虽然项目有，但 frontmatter 有跨平台/注释兼容考量，用简单解析更可控）。

## Risks / Trade-offs

- [frontmatter 解析简单，不支持 YAML 复杂结构] → 本 change 只需 name/description 两个标量 key，够用；复杂 frontmatter 后续切换解析器。
- [skill 内容可能很大] → 工具返回正文整体给模型；单项过大时后续加截断（对齐 resultMaxChars）。
- [两目录同名 skill 冲突] → 项目级覆盖用户级（或保留两者）；本 change 用「后者覆盖前者」策略，文档标注。
- [无 YAML 依赖 → 兼容性] → 解析只处理 `---` 包裹 + `key: value`，符合项目「不引入不必要依赖」约定。

## Migration Plan

1. 新增 `Skill` / `SkillCatalog` / `SkillTool`。
2. `ToolRegistry` 加 `registerSkillTools`。
3. `AgentLoopFactory.buildTools` 接入 skill 发现与注册。
4. 新增 `SkillCatalogTest` / `SkillToolTest`。
5. `mvn -pl agent-core verify` 全绿（jacoco 门禁达标）。

## Open Questions

- skill 根目录的 PROJECT 位置：用项目 `.agent-demo/skills/`（与 memory 的 PROJECT scope 同约定），或 `cwd/.dsh/skills/`？本 change 用 `.agent-demo/skills/`，后续可对齐。
