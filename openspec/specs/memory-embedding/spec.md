# memory-embedding Specification

## Purpose
TBD - created by archiving change add-embedding-rag. Update Purpose after archive.
## Requirements
### Requirement: embedding 模型接入

系统 SHALL 在 `memory.embedding.enabled=true` 时加载本地 ONNX 模型（默认 `BAAI/bge-small-zh-v1.5`，512 维），对外暴露 `EmbeddingProvider.embed(String text) -> float[]` 接口；模型文件 SHALL 存放于 `<agentHome>/models/bge-small-zh-v1.5/model.onnx`，git 忽略。

#### Scenario: 首次启动模型存在即加载

- **WHEN** `memory.embedding.enabled=true` 且模型文件存在
- **THEN** `EmbeddingProvider` 加载 ONNX session，可调用 `embed()` 返回 512 维向量

#### Scenario: 首次启动模型缺失不致命

- **WHEN** `memory.embedding.enabled=true` 但模型文件不存在
- **THEN** 记录 WARN 并打印下载指引（指向 `tools/download-embedding-model.sh`）；`EmbeddingProvider` 标记为 `unavailable`；召回链路降级为字面 + sideQuery，不阻断主流程

#### Scenario: 模型加载失败不致命

- **WHEN** ONNX 加载过程抛异常（文件损坏 / 版本不兼容）
- **THEN** 记录 WARN + 异常堆栈；同缺失模型处理，`EmbeddingProvider.unavailable=true`，召回降级

#### Scenario: 配置关闭则不加载

- **WHEN** `memory.embedding.enabled=false`
- **THEN** 装配层不构造 `EmbeddingProvider`；新增依赖（onnxruntime / lucene-core / lucene-analysis-common）若已被 Maven 引入则属依赖膨胀——设计应在 `pom.xml` 把这些依赖标 `<scope>compile</scope>` 但运行时按需 lazy init

### Requirement: 向量索引持久化与检索

系统 SHALL 为 USER / PROJECT 两个 scope 各维护一份独立的 Lucene HNSW 向量索引，索引目录 `<scopeDir>/.vectors/`，git 忽略；提供 `VectorIndex.search(query, k) -> List<ScoredItem>` 接口，按 cosine 相似度降序返回 top-k。

#### Scenario: 索引按 scope 隔离

- **WHEN** 向 USER 索引 add 一条 (`foo.md`, vec)
- **THEN** PROJECT 索引不包含该条；USER 索引 `search(vec, k)` 能返回该条

#### Scenario: 检索返回 top-k 按相似度降序

- **WHEN** 索引含 5 条向量，向量 A 与 query 余弦 0.95、向量 B 与 query 余弦 0.80、其余 < 0.5
- **THEN** `search(query, 2)` 返回 `[A, B]`（顺序按分数降序）

#### Scenario: 索引持久化跨重启可用

- **WHEN** 进程向索引 add 一条后调用 `flush()`，再重启进程加载同一索引目录
- **THEN** 重新加载的索引能检索到该条

### Requirement: mtime 比对懒加载

系统 SHALL 在每次加载 scope 索引时，对该 scope 目录下的每个 .md 文件做 mtime 比对：mtime 未变且索引中已存在该 filename → 跳过；mtime 变化或索引中不存在 → 重新算 embedding 并 upsert。mtime 缓存文件 SHALL 存放于 `<scopeDir>/.vectors/mtime.json`，git 忽略。

#### Scenario: mtime 未变跳过

- **WHEN** 启动时 .md 文件 mtime 与缓存一致
- **THEN** 该文件不调用 `EmbeddingProvider.embed()`

#### Scenario: mtime 变化触发重算

- **WHEN** .md 文件 mtime 较缓存新（或索引中不存在）
- **THEN** 调用 `embed()` 重算并 upsert 到索引，更新 mtime 缓存

#### Scenario: mtime 缓存损坏全部重算

- **WHEN** `mtime.json` 解析失败或文件不存在
- **THEN** 记录 WARN；对该 scope 下所有 .md 重新算 embedding 并 upsert（自愈）

#### Scenario: mtime 缓存 flush 持久化

- **WHEN** `flush()` 被调用（启动期完成所有 upsert 后，或显式调用）
- **THEN** mtime 缓存写入 `mtime.json`，下次启动可跳过未变文件

### Requirement: 三层召回架构（粗排 + 精排解耦）

系统 SHALL 在 `MemoryRetriever.retrieve()` 内部按下列顺序执行三层召回，每层独立可降级：

1. **字面层**：`MemoryRecall` 召回 ≥ 0.3 分的条目（既有行为）
2. **embedding 粗排层**：`EmbeddingProvider.isReady()` 时执行；`VectorIndex.search(query, k_embed)` 返回 top-k_embed，与字面命中并集去重
3. **sideQuery 精排层**：字面 ∪ embedding 命中 < k 且候选 ≥ `minCandidates` 时发起 LLM 调用（既有触发逻辑）

任一层失败 SHALL 静默降级，不阻断下一层与最终输出。

#### Scenario: 字面已召满跳过 embedding 与 sideQuery

- **WHEN** 字面召回命中数 ≥ k
- **THEN** 跳过 embedding 阶段、跳过 sideQuery 调用；返回字面结果

#### Scenario: 字面不足 embedding 粗排补足

- **WHEN** 字面召回命中 < k 且 `EmbeddingProvider.isReady()=true`
- **THEN** 执行 embedding 检索；与字面命中并集去重；返回该并集

#### Scenario: embedding 补充后已召满跳过 sideQuery

- **WHEN** 字面 ∪ embedding 命中 ≥ k
- **THEN** 跳过 sideQuery 调用；返回并集

#### Scenario: 仍不足时 sideQuery 精排

- **WHEN** 字面 ∪ embedding 命中 < k 且候选 ≥ `minCandidates` 且 sideQuery.enabled=true
- **THEN** 发起 sideQuery；与既有并集去重；返回最终结果

#### Scenario: embedding 不可用时退化两层

- **WHEN** `EmbeddingProvider.isReady()=false`（模型缺失 / 加载失败 / 配置关闭）
- **THEN** 跳过 embedding 阶段；按字面 + sideQuery 既有逻辑执行；行为与 v0.4 一致

#### Scenario: embedding 异常静默降级

- **WHEN** `embed()` 或 `VectorIndex.search()` 抛异常
- **THEN** 记录 WARN + 异常堆栈；跳过 embedding 阶段；走字面 + sideQuery 路径

### Requirement: 模型与索引文件 git 忽略

系统 SHALL 将以下文件 / 目录加入 `.gitignore`：

- `<agentHome>/models/` 下的所有内容（embedding 模型文件）
- `<scopeDir>/.vectors/` 下的所有内容（HNSW 索引 + mtime 缓存）

#### Scenario: 模型文件不被 git 跟踪

- **WHEN** 模型文件存在 `<agentHome>/models/bge-small-zh-v1.5/model.onnx`
- **THEN** `git status` 不显示该文件为 untracked；`git ls-files` 不包含该路径

#### Scenario: 索引文件不被 git 跟踪

- **WHEN** 索引文件存在 `<scopeDir>/.vectors/index/` 与 `<scopeDir>/.vectors/mtime.json`
- **THEN** `git status` 不显示这些文件为 untracked

