## Why

2026-08-30 归档的 `add-memory-sidequery` change 目标是把 Memory 召回「接入注入链路」（其 proposal 原文即以此为首要目标），但实现后链路**仍未接通**：`AgentLoopFactory:199` 传给 `MemoryPromptBuilder.build` 的 query 是空字符串，`MemoryPromptBuilder:76` 的守卫 `!query.isBlank()` 因此为假，代码走 else 分支渲染 `MEMORY.md` 全量索引。`MemoryRetriever.retrieve()` 与 `SideQuerySelector.select()` 在生产路径上不可达。

更根本的是时机问题：`AgentLoop.systemPrompt` 是启动期一次性生成的 final 字段，每轮 `toRequest()` 直接复用。**只要注入发生在启动期，就不存在「按当轮提问召回」的输入**——这不是传对 query 就能修的，必须把召回挪到每轮。

后果：`openspec/specs/memory/spec.md` 中 `Requirement: sideQuery 语义召回补充` 规定的 SHALL 行为实际未发生；CLI 与 Web 两条路径均如此。

## What Changes

- **注入时机改为每轮**：memory 段不再随 system prompt 在启动期定稿，改为每轮按当前用户消息作为 query 动态召回后拼装。
- **拆分 system prompt 组装**：把「基础段」（身份 / 行为规范 / 运行时存储）与「memory 段」分离——前者仍在启动期生成一次并可缓存，后者每轮生成。
- **`AgentLoop` 承担动态召回**：持有 retriever + memory dirs，在 `toRequest()` 时按当轮 query 生成 memory 段。
- **清理死代码**：`ChatCommand:150` 的 `buildSystemPrompt` 返回值被赋值后从未使用（`buildLoop` 内部会重新生成），予以移除。
- **补齐测试空白**：新增端到端装配测试，断言 `buildSystemPrompt` / 每轮拼装产物中**包含召回结果**（以 `### X Scope (relevant)` 为可观测指纹），而非仅测 `MemoryRetriever` 单类。
- **可回退**：新增配置开关，关闭时回退到 v0.1 行为（全量索引注入），保证行为变更可控。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `memory`: 修改 `Requirement: Memory 作用域索引与召回` 的注入时机（启动期一次性 → 每轮按 query 动态）；修正 `Requirement: sideQuery 语义召回补充` 使其真实生效（补 SHALL 行为的可验证场景）；新增「动态召回可回退」场景。

## Impact

- 受影响类：`core/AgentLoop`（新增 retriever/dirs 字段 + `toRequest()` 动态拼装）、`core/AgentLoopFactory`（装配与签名）、`prompt/SystemPromptBuilder`（拆分基础段与 memory 段）、`cli/ChatCommand`（清理死代码）。
- 受影响装配路径：CLI（`ChatCommand`）、Web（`WebAgentRuntime:212`）均经 `buildLoop`，同步生效。
- 受影响配置：`AgentConfig.Memory` 新增动态召回开关（缺省开启，可关闭回退）。
- 无新外部依赖（复用现有 `LlmProvider` / `MemoryRecall` 链路）。
- 测试：新增端到端装配测试；扩充 `AgentLoopTest` / `SystemPromptBuilderTest`。
- 兼容性：行为变更（system prompt 内容每轮变化）。关闭开关后与当前行为一致。
