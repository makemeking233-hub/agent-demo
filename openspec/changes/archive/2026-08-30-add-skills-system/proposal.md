## Why

agent-demo 目前没有 Skills 系统——模型只能调用固定工具集（ReadFile/WriteFile/EditFile/Ls/Shell），无法发现并使用「技能」（可复用的指令/流程卡片）。DSH 生态已有成熟的 skill 约定（`<root>/<name>/SKILL.md`，一级深度，frontmatter 含 name + description）。v0.4 规划明确列出 Skills 系统。

## What Changes

- **Skill 发现**：从 `~/.agent-demo/skills/<name>/SKILL.md` 与项目 `.agent-demo/skills/<name>/SKILL.md` 两个目录（一级深度）发现技能；`<name>/SKILL.md` 或 `<name>.md` 均可。
- **frontmatter 解析**：解析 `SKILL.md` 头部 frontmatter（`name`、`description` 必填），正文作为技能内容。
- **Skill 作为工具**：每个发现的 skill 注册为一个 `Tool`（name = skill 名，description = frontmatter 描述），`execute` 返回 skill 正文（供模型基于技能内容行动）。
- **注册进 ToolRegistry**：`AgentLoopFactory.buildTools` 调用 `ToolRegistry.registerSkillTools` 把发现的 skill 注册为工具。

## Capabilities

### New Capabilities
- `skills`: 技能发现与执行（`SKILL.md` 发现、frontmatter 解析、skill 作为工具暴露给模型）。

### Modified Capabilities
- （无：`openspec/specs/` 下没有既有 skills spec；本次新增）

## Impact

- 受影响类：`agent-core/.../tools/ToolRegistry`（加 `registerSkillTools`）、`agent-core/.../core/AgentLoopFactory`（buildTools 调注册）。
- 新增类：`agent-core/.../skill/Skill`、`SkillCatalog`（发现+解析）、`SkillTool`（实现 Tool）。
- 无外部依赖变更（纯本地文件/SKILL.md；不引入 YAML 库——frontmatter 手写解析，避免新依赖）。
- 测试：新增 `SkillCatalogTest` / `SkillToolTest`；`ToolRegistryTest` 兼容。
