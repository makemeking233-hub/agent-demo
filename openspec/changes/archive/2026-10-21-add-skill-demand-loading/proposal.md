## Why

`refactor-skill-index-layer` 已把「正文常驻」改成「元数据常驻 + 正文按需」，但 tools 段仍是 **1 skill = 1 ToolTool**：10000 个 skill 会在每轮 `ChatRequest.tools` 里塞进 10000 条 `ToolSpec`（name + description + 空 schema ≈ 90 token/条，合计 ≈ 900k token），是 83808 compact 阈值的 **1073%**——即使有索引也永远发不出去。且工具数超过 ~100 时模型的工具选择准确率会显著退化。

本 change 把「暴露」也改成按需：tools 段恒定只放基础工具 + ≤10 个热门 skill + 1 个 `skill_search`，其余 skill 通过候选名单 + `skill_search` 按需触达。

## What Changes

- **新增三档模式配置** `skill.mode`：`all`（全暴露，现状）/ `search`（0 直露，纯检索，**默认**）/ `hybrid`（≤10 直露 + 检索）。
- **每轮检索接线**：`AgentLoop.toRequest()` 在 `history` 有最近 user 消息时调 `SkillPool.search(query, k=20)`，把候选渲染成「name + 截断 40 字描述」名单追加到 system prompt 末尾。
- **新增 `skill_search` 工具**：`input = {query: string}`，返回 top-k 候选的 name + 描述，让模型能**多次换关键词精化检索**。
- **新增热门选择器**（仅 `hybrid`）：`PROJECT` scope 配额 6 + 频次指数衰减配额 2 + 会话内最近调用配额 2，去重后截断 ≤10，产出真实的 `SkillTool` 进 tools。
- **新增频次持久化** `~/.agent-demo/skill-usage.json`：记录 `{name: {count, lastUsedMs}}`，原子写；热门排序用 `score = count × exp(-λ × daysSinceUsed)`，λ 默认 0.05（半衰期 ≈ 14 天）。
- **embedding 索引接入**：`SkillPool` 复用 `memory/embedding` 的 `OnnxEmbeddingProvider` + `LuceneVectorIndex`，检索链路为「倒排预筛（10000→~200）→ embedding KNN（200→top-k）」；embedding 不可用时退化为纯倒排。
- 新增 `agent.skill` config 段（`mode` / `hybridDirectLimit` / `hybridProjectQuota` / `recallK` / `decayLambda` / `descriptionTruncateChars`）。

## Capabilities

### New Capabilities

（无——检索与频次统计是 skills capability 的内部机制，不单列 capability）

### Modified Capabilities

- `skills`：修改「Skill 作为工具」与「Skill 工具注册与摘要注入」两条 Requirement——前者从「1 skill = 1 Tool 全量注册」改为按 `skill.mode` 分档；后者从「全量列表摘要」改为「当轮候选名单」。另 ADDED `skill_search` 工具、按需暴露三档模式、热门选择与频次持久化三条 Requirement。

## Impact

- 受影响类：`skill/SkillTool`（新增 `skill_search` 同包类）、`skill/SkillPool`（加 search/hot-pick API）、`core/AgentLoop`（`toRequest()` 加候选注入）、`core/AgentLoopFactory`（注入 mode）、`plugin/skill/SkillsPlugin`（按 mode 决定 tools() 返回值）
- 新增类：`skill/SkillSearchTool`、`skill/HotSkillSelector`、`skill/SkillUsageStore`
- 配置：`config/AgentConfig` 加 `Skill` record（mode + 5 个数值参数），`config.yaml` 加 `agent.skill` 段
- 复用基建：`memory/embedding/OnnxEmbeddingProvider`、`memory/embedding/LuceneVectorIndex`、`memory/MemoryRecall.tokenize`
- 测试：新增 `SkillSearchToolTest` / `HotSkillSelectorTest` / `SkillUsageStoreTest` / `SkillModeWiringTest`（三档各自的 tools 形状）
- 依赖本 change 生效前提：`refactor-skill-index-layer` 已合入 main（提供 `SkillPool`）
- 不涉及前端 UI（tool 名与 desc 变化对模型透明）、不涉及 `MEMORY.md` 格式