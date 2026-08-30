## Why

当前 Memory 系统只支持单目录 `~/.agent-demo/memory/`（USER scope），所有会话共享同一份长期记忆，无法区分「跨项目全局」与「当前项目专属」的知识。`MemoryScope` 枚举虽已定义 USER/PROJECT/LOCAL 三类，但零引用、完全未实现。用户希望记忆能按作用域分层：全局约定进 USER，项目踩坑进 PROJECT，且当前场景（agent 主流程用 MiniMax 开发本项目）经常需要「仅本会话有效」的临时记忆（LOCAL）。

## What Changes

- 引入 **scope 维度**到 Memory 的路径解析、索引解析、召回与 system prompt 注入链路。
- **USER scope**：`~/.agent-demo/memory/`（跨项目全局，磁盘持久化）。
- **PROJECT scope**：随项目仓库 `.agent-demo/memory/`（项目专属，磁盘持久化）。
- **LOCAL scope**：仅当前会话（内存/一次性，不入磁盘，随会话结束丢弃）。
- `MemoryEntry` 增加 `scope` 字段；`MemoryIndex` 解析/写入时区分 scope；`MemoryPromptBuilder` 按 scope 合并多份索引与召回结果注入 system prompt。
- `AgentLoopFactory` 根据工作目录解析 PROJECT 路径，与 USER/LOCAL 一同组装 memory section。

## Capabilities

### New Capabilities
- `memory`: 分层长期记忆（USER/PROJECT/LOCAL 三 scope 的路径解析、索引、召回与注入）。

### Modified Capabilities
- （无：`openspec/specs/` 下没有既有 memory spec；本次属新增 memory capability）

## Impact

- 受影响类：`agent-core/.../memory/MemoryDir`、`MemoryEntry`、`MemoryIndex`、`MemoryPromptBuilder`、`MemoryRecall`、`MemoryScope`。
- 受影响装配：`agent-core/.../core/AgentLoopFactory.buildSystemPrompt`（解析工作目录 → PROJECT scope → 多 scope 合并注入）。
- 测试：新增 `MemoryThreeScopeTest`/扩充 `MemoryPromptBuilderTest`（现 3 个测试需保持通过）；`AcceptanceTestSuite` 中 Memory 相关断言需兼容。
- 无外部依赖变更（纯内存/文件）。
- 无 API 破坏性变更（`MemoryEntry` 从 3 字段 record 变为 4 字段，需同步更新所有构造点）。
