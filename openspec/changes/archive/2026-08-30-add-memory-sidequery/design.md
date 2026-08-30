## Context

Memory 召回链路（add-memory-three-scope 之后）：`MemoryPromptBuilder.build(List<MemoryDir>, extra)` 逐 scope 读 `MEMORY.md` 并把整个索引注入 system prompt。`MemoryRecall.recall(query, entries, maxRecall, minScore, scope)` 是纯函数（token 重叠评分），但**生产注入链路从未调用它**——召回结果对模型不可见。设计文档 memory-design.md §6.3 规划 sideQuery（轻量模型选择）替代字面重叠。

本 change 目标：① 让召回真正接入注入链路（召回结果而非全量索引注入）② 增加 sideQuery 语义补充。

## Goals / Non-Goals

**Goals:**
- 召回接入注入链路：memory 段由「全量索引」改为「召回条目」。
- sideQuery 语义补充：字面命中不足时用 provider 挑选补充，并集去重。
- sideQuery 保守触发：默认开启，仅在候选多且字面命中少时调用，控制成本/延迟。
- 可关闭：`memory.sideQuery.enabled=false` 完全禁用。

**Non-Goals:**
- 不做记忆正文文件级召回（仍基于索引 title/description 评分）。
- 不做多轮/会话级 sideQuery 缓存。
- 不改 memory 写入链路（Agent 写 memory 工具逻辑）。
- 不引入 embedding/向量（纯 LLM 选择，避免新依赖与索引基建）。

## Decisions

**D1: 新增 `MemoryRetriever` 编排召回 + sideQuery，`MemoryPromptBuilder` 只负责渲染。**
- `MemoryRetriever`：注入 `LlmProvider`（可空，null 则纯字面）+ `MemoryRecall` + `MemoryIndex` 工厂。
- `retrieve(query, List<MemoryDir>, k)`：逐 scope 用 `MemoryIndex` 解析 entry → `MemoryRecall` 字面召回（scope 限定）→ 若命中 `< k` 且候选 `>= minCandidates` 且 enable，调 `SideQuerySelector` 补充 → 并集去重 → 返回 `Map<MemoryScope, List<MemoryEntry>>`。
- `MemoryPromptBuilder` 改为接收 `MemoryRetriever`，用其召回结果渲染 scope 小节（不复读索引全文）。
- 备选：直接把 provider 塞进 `MemoryPromptBuilder`。否决——builder 是纯渲染，掺入 LLM 调用破坏单一职责。

**D2: `SideQuerySelector` 独立选择器。**
- `select(query, List<MemoryEntry> candidates, k)`：用 provider 调一次 `streamChat`，prompt 让模型从候选里按相关性挑 `<= k` 个，返回其文件名；超时/失败/解析失败 → 空列表（静默降级）。
- 候选传入前用 `maxCandidates`（默认 8）截断，控制 prompt 长度。
- 备选：把选择逻辑硬编码进 `MemoryRetriever`。否决——不可单测、职责混。

**D3: `AgentLoopFactory` 注入 provider 到 retriever。**
- `AgentLoopFactory` 在 `buildSystemPrompt` 构造 `MemoryRetriever` 时把 `LlmProvider`（从 `buildLoop` 传入）注入。
- `buildSystemPrompt` 增加一个 `LlmProvider` 参数（`buildLoop` 已有 provider，传入即可）。
- 备选：retriever 内部自建 provider。否决——provider 实例应由装配层持有，避免重复构造。

**D4: `AgentConfig` 加 `memory.sideQuery` 配置。**
- `Memory` record 增 `SideQuery(boolean enabled, int maxCandidates, int minCandidates)`，默认 `enabled=true, maxCandidates=8, minCandidates=3`。
- `ConfigLoader` 解析 `memory.sideQuery.*`；缺省用默认值。

## Risks / Trade-offs

- [sideQuery 调 LLM 增加延迟/成本] → 保守触发（候选 ≥ minCandidates 且字面命中 < k 才调）+ 单次、候选截断；WARN 兜底静默降级。
- [provider 检索时不可用（如未设 key）] → `MemoryRetriever` 遇 provider 为 null 或调用异常时回退纯字面召回，不阻断。
- [引入 provider 到 system prompt 组装，可能影响现有 `buildSystemPrompt` 调用] → 用重载保留无 provider 版本（纯字面），最小侵入。
- [MODIFIED delta spec 增多] → 严格按主 spec 的 Requirement 完整复制，archive 后保持一致。

## Migration Plan

1. `AgentConfig.Memory` 加 sideQuery 配置字段 + `ConfigLoader` 解析。
2. 新增 `SideQuerySelector`（含单测）。
3. 新增 `MemoryRetriever`（编排召回 + sideQuery，含单测）。
4. `MemoryPromptBuilder` 改为消费 retriever 召回结果渲染。
5. `AgentLoopFactory.buildSystemPrompt` 注入 provider 构造 retriever。
6. 扩充 `MemoryPromptBuilderTest` / `MemoryRecallTest` 适配召回结果注入。
7. `mvn verify` 全绿（agent-core jacoco 门禁达标）。

## Open Questions

- sideQuery prompt 的模型输出格式解析（返回文件名列表的 JSON/纯文本）——实施时用宽松解析（匹配 `filename` 子串），失败即静默降级。
