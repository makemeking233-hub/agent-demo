## Context

Memory 召回链路的当前状态（经代码审查确认，详见 `docs/design/memory-recall-deep-dive.md` §6.5）：

`MemoryRetriever`、`SideQuerySelector`、`MemoryRecall` 三个类均已实现且有单测，但**在生产路径上不可达**。根因有两层：

1. **表层**：`AgentLoopFactory:199` 调 `MemoryPromptBuilder.build("", ...)` 传入空 query，`MemoryPromptBuilder:76` 的守卫 `!query.isBlank()` 为假，走 else 分支 `buildSections(dirs)` 渲染全量索引。
2. **根层**：`AgentLoop.systemPrompt` 是构造期定稿的 `final` 字段（`AgentLoop.java:122`），每轮 `toRequest()` 直接复用。**启动期不存在「当前提问」这个输入**——即使把 query 传对，也没有正确的值可传。

约束：

- 不引入新外部依赖（纯 LLM 选择式，无 embedding）。
- `AgentLoop` 是被 CLI 与 Web 共用的核心类，构造器重载较多（8 个），改造须保持向后兼容。
- 需要控制每轮开销：字面召回是纯 CPU（可接受每轮做），sideQuery 会发起 LLM 调用（有延迟与 token 成本）。
- 失败必须静默降级，绝不阻断主对话（既有 spec 要求）。

## Goals / Non-Goals

**Goals:**

- 让 memory 段按**当轮用户提问**动态召回，而非启动期全量索引。
- 使既有的 `MemoryRetriever` / `SideQuerySelector` 在生产路径上**真正可达**。
- 每轮开销可控：同一轮内的多次 `toRequest()`（工具迭代）不重复召回。
- 提供配置开关，可回退到 v0.1 行为（全量索引注入）。
- 补齐端到端装配测试，消除「单类测试绿但链路未通」的盲区。

**Non-Goals:**

- 不引入 embedding / 向量库（留待后续 change）。
- 不改 `MemoryRecall` 的评分算法、不改 `SideQuerySelector` 的选择逻辑。
- 不改 memory 的写入链路（模型写 `.md` + 更新 `MEMORY.md` 的工具）。
- 不做跨轮召回缓存 / 会话级 LRU。

## Decisions

### D1: 把 system prompt 拆成「基础段」与「memory 段」，基础段启动期生成、memory 段每轮生成

`SystemPromptBuilder` 新增 `buildBase(...)`，返回**不含 memory 段**的完整 prompt（模板中 `{memoryBlock}` 替换为空串）。每轮由 `AgentLoop` 把 `basePrompt + "\n\n" + memorySection` 拼成最终 system prompt。

理由：`memory-system.txt` 模板的产物自带 `# Persistent Agent Memory` 标题，直接拼接语义完整，无需为动态段在模板里留占位符（避免模板里出现只在运行时有意义的 `{memorySection}` 空占位）。

备选：在模板里保留 `{memoryBlock}` 占位符，每轮做一次全量 `replace`。否决——每轮重复替换 providerName / storageBlock 等静态段是无谓开销，且把「哪些段是动态的」这一信息埋进字符串操作里。

### D2: `AgentLoop` 持有 memory 段提供者，在 `toRequest()` 时按当前 query 生成

新增一个轻量接口（包内可见）承载「按 query 产出 memory 段」这一职责：

- `AgentLoop` 新增可选字段 `MemorySectionProvider`（可空；为空时 system prompt 完全由构造参数决定，行为与改造前一致）。
- `toRequest()` 在构造 `ChatRequest` 前，用 `history` 中**最近一条 user 消息**作为 query 调 provider 取 memory 段。

理由：`toRequest()` 是唯一的请求组装点，在此处介入能天然覆盖「每轮」与「工具迭代中的每次重发」。

备选：在 `AgentLoopFactory` 里预渲染、把整份 prompt 传进 `AgentLoop`。否决——这正是当前断点的成因（启动期定稿）。

### D3: 轮内缓存，避免工具迭代重复召回

`AgentLoop` 记录 `lastQuery` 与 `lastMemorySection`（`toRequest()` 可能因工具调用被调用多次，但同一轮 query 不变）。query 未变时直接复用上次结果。

理由：一次用户提问可能触发最多 `maxToolIterations=25` 次 `toRequest()`；不缓存会让字面召回重复 25 次，更重要的是会让 sideQuery 重复发起 LLM 调用。

备选：不缓存。否决——成本与延迟不可接受。

### D4: 保留现有构造器重载，新增带 provider 的重载

既有 8 个 `AgentLoop` 构造器全部保留（`memoryProvider = null`，行为不变）。新增一个接收 `baseSystemPrompt + memoryProvider` 的构造器供 `AgentLoopFactory` 使用。

理由：`AgentLoop` 被大量测试与两个装配点使用，改签名会制造大面积无意义 churn。

### D5: 配置开关 `memory.dynamicRetrieval`，缺省开启

`AgentConfig.Memory` 增加 `boolean dynamicRetrieval`（默认 `true`）。关闭时 `AgentLoopFactory` 不注入 provider，回退为启动期全量索引注入（v0.1 行为）。

理由：行为变更需要可回退开关；同时给「索引很小、不需要召回」的场景留一条零开销路径。

### D6: sideQuery 保持既有触发门槛不变

不因「改为每轮」而放宽或收紧 `minCandidates` / `maxCandidates` / `enabled`。既有的门槛（候选 ≥ 3 且字面命中 < k）已足够保守——memory 索引条目多且字面能命中时不会触发 LLM 调用。

理由：本次 change 的目标是**接通链路**，不是调参。调参应基于接通后的实测数据另开 change。

## Risks / Trade-offs

- [每轮读 `MEMORY.md` 带来文件 IO] → 文件通常 < 25KB（索引有硬截断），且 `MemoryIndex.parse` 是逐行正则；实测开销在亚毫秒量级，相对每轮 LLM 调用可忽略。
- [sideQuery 每轮触发导致响应变慢] → 既有门槛已保守（字面命中足够时不触发）；D3 的轮内缓存避免工具迭代重复调用。若实测仍偏慢，可经 `memory.dynamicRetrieval=false` 或 `sideQuery.enabled=false` 关闭，无需回滚代码。
- [system prompt 每轮变化，破坏 prefix 缓存复用] → DeepSeek 的上下文缓存以 prompt 前缀匹配，memory 段位于 prompt 末尾（在 storage / extra 之后），前缀（身份 + 行为 + 存储）仍可命中缓存。设计上**把 memory 段放在最后**正是为减少缓存失效。
- [行为变更导致既有测试断言失败] → 既有测试多直接构造 `AgentLoop`（`memoryProvider = null`），不受影响；受影响的是断言 system prompt 内容的测试，逐个人工确认。
- [两处装配路径（CLI / Web）行为不一致] → 两者都经 `AgentLoopFactory.buildLoop`，只需改一处；但仍需各自跑一次集成测试确认。

## Migration Plan

1. `SystemPromptBuilder` 增加 `buildBase(...)`；保留 `build(...)` 供既有调用与测试使用。
2. `AgentConfig.Memory` 增加 `dynamicRetrieval` 字段 + `ConfigLoader` 解析（缺省 `true`）。
3. 新增 `MemorySectionProvider`（封装 `MemoryRetriever` + dirs + k），置于 `core` 或 `memory` 包。
4. `AgentLoop` 新增可选字段 + 轮内缓存 + `toRequest()` 动态拼装；新增构造器重载。
5. `AgentLoopFactory` 按 `dynamicRetrieval` 决定是否构造 provider 并注入。
6. 清理 `ChatCommand:150` 死代码。
7. 补端到端装配测试（断言产物含 `(relevant)` 指纹）。
8. `mvn -o -pl agent-core,agent-web verify` 全绿。

**回滚策略**：配置层面置 `memory.dynamicRetrieval=false` 即回到 v0.1 行为；代码层面本 change 是单分支内的原子改动，未合并前回退分支即可。

## Open Questions

- `MemorySectionProvider` 放 `core` 包还是 `memory` 包？（倾向 `memory`：它属于记忆域，`core` 只依赖其接口）
- 每轮取 query 时，是否需要把「历史最近 N 轮」也纳入召回输入？（倾向否：先用单条当前提问，多轮上下文召回留待实测后再议）
