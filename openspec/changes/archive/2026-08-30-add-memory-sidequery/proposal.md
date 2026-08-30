## Why

当前 Memory 召回（`MemoryRecall`）只有 token 字面重叠评分，且**从未接入生产注入链路**——`MemoryPromptBuilder.build()` 只把整份 `MEMORY.md` 索引注入 system prompt，`recall()` 只在测试中被调用。这导致：同义改写（「代码规范」vs「编码风格」）会漏召回，且召回结果对模型不可见。设计文档 memory-design.md §6.3 明确记录此局限，并规划升级：**sideQuery（用轻量模型做选择）替代字面重叠**。

## What Changes

- **召回接入注入链路**：`MemoryPromptBuilder` 解析各 scope 的索引为 `MemoryEntry` 列表，用 `MemoryRecall`（含 scope 限定）召回当前查询相关的 K 条，并把召回结果（而非全量索引）注入 memory 段。
- **SideQuery 补充**：新增 `SideQuerySelector`，在字面重叠命中不足时，复用当前 `LlmProvider` 发起一次轻量模型调用，从候选里挑选最相关的 K 条作为补充（不替换字面结果，二者并集去重）。
- **保守触发**：默认开启，但仅当候选条目数超过阈值且字面命中数低于下限时才调用 provider，控制成本与延迟。
- **记忆段包含召回条目**：注入的 memory 段列出召回的条目（标题 + 描述 + scope + 路径），供模型参考。

## Capabilities

### New Capabilities
- `memory`（已有，本 change 修改其召回行为）：更新 memory 索引注入为"召回结果注入"，并提供 sideQuery 语义补充。

### Modified Capabilities
- `memory`：修改既有 `Requirement: Memory 作用域索引与召回`，新增 sideQuery 语义召回的 SHALL 行为。

## Impact

- 受影响类（agent-core memory 包）：`MemoryRecall`、`MemoryPromptBuilder`；新增 `SideQuerySelector`。
- 受影响装配：`agent-core/.../core/AgentLoopFactory`（把 `LlmProvider` 传给 memory 注入链路）。
- 受影响配置：`AgentConfig`/`ConfigLoader` 新增 `memory.sideQuery.enabled`（允许关闭）。
- 依赖 `LlmProvider.streamChat`（现有，无新依赖）。
- 测试：新增 `SideQuerySelectorTest`；扩充 `MemoryPromptBuilderTest`/`MemoryRecallTest` 适配召回结果注入。
