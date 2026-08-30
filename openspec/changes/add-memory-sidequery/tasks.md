# Tasks: Memory SideQuery 召回

## 1. 配置：AgentConfig 加 memory.sideQuery

- [x] 1.1 新增 `AgentConfig.Memory` record（含 `SideQuery(boolean enabled, int maxCandidates, int minCandidates)`），加入 `AgentConfig` 主 record 与 `defaults()`
- [x] 1.2 `ConfigLoader` 解析 `memory.sideQuery.*`（`mergeYaml`/`applyEnv` 补构造点），缺省用默认值

## 2. SideQuery 选择器

- [x] 2.1 新增 `SideQuerySelector`：注入 `LlmProvider`，`select(query, candidates, k)` 用 provider 调一次选择，返回补充条目 filename；失败/超时/解析失败 → 空列表（静默降级）
- [x] 2.2 `SideQuerySelectorTest`：命中、失败降级、候选截断

## 3. 召回接入注入链路

- [x] 3.1 新增 `MemoryRetriever`：注入 `LlmProvider`（可空）+ `MemoryRecall`，逐 scope 解析索引 → 字面召回（scope 限定）→ 命中不足则 sideQuery 补充 → 并集去重 → 返回 `Map<MemoryScope, List<MemoryEntry>>`
- [x] 3.2 触发门槛：候选 ≥ `minCandidates` 且字面命中 < `k` 才调 sideQuery；`enabled=false` 不调
- [x] 3.3 `MemoryPromptBuilder` 改为消费 `MemoryRetriever` 召回结果渲染 scope 小节（不再读全量索引文本）

## 4. 装配：AgentLoopFactory 注入 provider

- [x] 4.1 `AgentLoopFactory.buildSystemPrompt` 增加 `LlmProvider` 参数（保留无 provider 重载），构造 `MemoryRetriever` 传入 memory 注入链路

## 5. 测试与验证

- [x] 5.1 扩充 `MemoryPromptBuilderTest`/`MemoryRecallTest` 适配召回结果注入
- [x] 5.2 `mvn -pl agent-core verify` 全绿（jacoco 门禁达标）
- [x] 5.3 commit + push（中文 Conventional Commits）
