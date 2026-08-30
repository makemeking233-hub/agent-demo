## Context

当前 Memory 系统（`agent-core/.../memory/`）只支持单一目录 `~/.agent-demo/memory/`（USER scope）。`MemoryScope` 枚举（USER/PROJECT/LOCAL）已定义但零引用。核心链路：

- `AgentLoopFactory.buildSystemPrompt()` 用 `new MemoryDir(~/.agent-demo/memory)` 构造 `MemoryPromptBuilder`，把单一 `MEMORY.md` 索引注入 system prompt。
- `MemoryIndex` 解析/写入 `MEMORY.md` 索引（`MemoryEntry[title, description, filename]`）。
- `MemoryRecall` 是孤立类（token 重叠评分），当前未被任何代码调用。
- `MemoryEntry` 是 3 字段 record，无 scope 字段；构造点分布在 `MemoryIndex.parse()` 和 `AcceptanceTestSuite`。

## Goals / Non-Goals

**Goals:**
- 让 Memory 支持 USER / PROJECT / LOCAL 三 scope。
- 三 scope 的路径解析、索引解析、召回、注入链路全部区分 scope。
- 保持现有测试（`MemoryPromptBuilderTest` / `MemoryDirTest` / `MemoryIndexTest` / `AcceptanceTestSuite`）不破坏，或同步更新以适配新结构。

**Non-Goals:**
- 不做记忆正文文件的 per-scope 复杂分层（仍沿用单 `MEMORY.md` 索引 + 正文 .md 文件模型）。
- 不做 SideQuery 语义召回（保持 token 重叠评分算法，仅按 scope 限定候选集）。
- 不做 User 主动配置 memory 目录（PROJECT 固定 `.agent-demo/memory/`，USER 固定 `~/.agent-demo/memory/`）。

## Decisions

**D1: scope 用 `MemoryScope` 枚举贯穿，不引入新类型。**
沿用已定义的 `MemoryScope { USER, PROJECT, LOCAL }`，作为 `MemoryEntry` 的新字段与 `MemoryDir`/`MemoryPromptBuilder` 的入参。避免另造 scope 抽象，贴合现有模型。

**D2: `MemoryEntry` 增加 `scope` 字段（4 字段 record）。**
`record MemoryEntry(String scope, String title, String description, String filename)`。scope 用 `MemoryScope` 枚举。这是唯一破坏性变更，需同步 `MemoryIndex.parse()`、`AcceptanceTestSuite` 等构造点。
- 备选：不改 record，用 `Map` 或 scope 参数拼接路径。否决——丢失类型安全，且 `MemoryIndex` 解析时无法区分来源。

**D3: `MemoryDir` 支持 scope 感知的路径解析。**
`MemoryDir` 增加静态工厂/构造，按 scope 解析：
- USER → `~/.agent-demo/memory/`
- PROJECT → `<cwd>/.agent-demo/memory/`
- LOCAL → 无磁盘路径（内存）
`MemoryDir` 保留目录创建/权限/索引截断逻辑，被多 scope 复用。
- 备选：为每个 scope 各建独立类。否决——重复逻辑多。

**D4: `MemoryPromptBuilder` 合并多 scope 注入。**
`MemoryPromptBuilder.build()` 接收一个 `List<MemoryDir>`（或 scope→MemoryDir 映射），逐 scope 读取索引、执行 scope 限定召回、并拼成带 scope 标注的 memory section。LOCAL scope 无磁盘文件，其条目由会话内注入（本 change 先用内存空实现，LOCAL 条目当前由 Agent 直接写入内存，后续可扩展）。
- 备选：`build()` 只接单 scope，由 `AgentLoopFactory` 多次调用后拼接。否决——scope 边界和切分逻辑散落在装配层，`SystemPromptBuilder` 收到的是一整段记忆，无法区分结构。

**D5: `AgentLoopFactory` 注入 scope 解析与合并。**
`buildSystemPrompt()` 依据 `System.getProperty("user.dir")` 解析 PROJECT 路径，组装 USER + PROJECT（+ LOCAL 内存空）三个 `MemoryDir`，统一交给 `MemoryPromptBuilder`。
- 备选：把 PROJECT 解析放进 `MemoryDir` 内部。否决——cwd 属于运行上下文，应由装配层注入，保持 `MemoryDir` 纯路径工具。

## Risks / Trade-offs

- [PROJECT 目录会随仓库提交] → 设计上明确 `.agent-demo/memory/` 应加入 `.gitignore`（v0.2 已约定项目级 `.agent-demo/memory/` 不入盘泄漏敏感记忆）；本项目 change 不改 gitignore（属运行约定），在文档标注。
- [LOCAL 无磁盘，会话结束即丢] → 这是设计意图（一次性记忆）；暂用内存空实现，避免引入会话级存储复杂度。
- [`MemoryEntry` 破坏性变更] → 仅影响 2 个构造点，测试用 @TempDir 为主，改造成本低；先同步更新再跑全量。
- [多 scope 合并可能使记忆段过长] → 复用现有 `MemoryDir.truncateIndex`（200 行/25KB）逐 scope 限制；`SystemPromptBuilder.buildMemoryBlock` 已做空值省略。

## Migration Plan

1. 改 `MemoryEntry`/`MemoryIndex` 支持 scope → 同步全部构造点。
2. 改 `MemoryDir` 加 scope 解析 → 新增 `MemoryDir.forScope(scope, cwd/base)`。
3. 改 `MemoryPromptBuilder` 合并多 scope → 新增 scope 限定召回。
4. 改 `AgentLoopFactory.buildSystemPrompt` 解析 PROJECT 并合并三 scope。
5. 新增 `MemoryThreeScopeTest`（多 scope 注入/召回/路径）。
6. `mvn verify` 全绿（jacoco 门禁）。

## Open Questions

- LOCAL scope 的"会话内注入"具体载体（Agent 写入 memory 工具的落地方式）：本 change 先做最小可用（内存空 + 预留接口），具体 Agent 写入链路见后续 change。如需完整体验可在实施时补充。
