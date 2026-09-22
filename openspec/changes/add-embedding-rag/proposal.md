## Why

`fix-memory-recall-wiring`（2026-09-21 已归档）修通了「每轮按 query 召回」的链路，但召回后端仍是 v0.3 的 **LLM 选择式 sideQuery**——只能从 ≤8 条候选里挑，且同义改写召回依赖 LLM 语义理解。这有两个问题：

1. **候选上限硬限**：`SideQuerySelector` 为控制 prompt 长度把候选截断到 8 条；当 memory 索引条目 > 8 且字面无命中时，相关条目根本进不了候选，sideQuery 救不回来。
2. **每轮 LLM 调用**：sideQuery 触发时多一次 DeepSeek 调用（~1-3 秒延迟 + 几百 token 成本），且同轮多次 `toRequest()` 虽有缓存，跨轮仍可能频发。

升级为 **embedding 向量检索** 后，候选数无硬限（O(N) 内任意数量），召回从数学距离计算（毫秒级、无网络），sideQuery 退化为可选的精排层。

## What Changes

- **新增 embedding 模型接入**：本地 ONNX Runtime + BAAI/bge-small-zh-v1.5（512 维），单条推理 ~5ms CPU；模型文件 ~95MB，首次启动检测缺失并提示用户下载。
- **新增向量索引层**：`VectorIndex` 接口 + `LuceneVectorIndex` 实现（HNSW KNN，jar 增加 ~10MB）。
- **三层召回架构**：`MemoryRetriever` 在字面 + sideQuery 之间嵌入一层 embedding 召回（粗排），sideQuery 退化为可选精排；USER + PROJECT 两个 scope 各持一份独立索引。
- **轻量懒加载**：`VectorIndexStore` 用文件 mtime 比对跳过未变的 .md；启动后稳态开销极小（一次 mtime 检查）。
- **配置可关闭**：`memory.embedding.enabled=false` 退回当前架构（字面 + sideQuery）；`memory.dynamicRetrieval=false` 进一步退回启动期全量索引。
- **故障降级**：embedding provider 初始化失败（模型缺失 / 加载异常）→ 记录 WARN + 退回字面 + sideQuery 架构，绝不阻断主对话。

## Capabilities

### New Capabilities

- `memory-embedding`：embedding 模型接入 + 向量索引 + 检索能力的总集（含配置、模型加载、索引更新、检索 API）。

### Modified Capabilities

- `memory`：将 `Requirement: Memory 作用域索引与召回` 的召回流程从「字面 + sideQuery」扩展为「字面 + embedding + sideQuery」三层；保持所有既有 SHALL 行为，仅在「字面命中不足」时增加 embedding 粗排阶段。

## Impact

- **新增依赖**：`onnxruntime` (~30MB jar) + `lucene-core` + `lucene-analysis-common` (~10MB jar) + 启动时本地 `bge-small-zh-v1.5` 模型文件 (~95MB，git 忽略)。
- **受影响类**：`memory/MemoryRetriever`（加 embedding 阶段）、`memory/MemoryPromptBuilder`（无改动，依赖 retriever 输出）、`core/AgentLoopFactory`（装配 embedding provider + vector index store）、`config/AgentConfig`（加 `Memory.embedding` 段）。
- **新增类**（`memory/embedding` 包）：`EmbeddingProvider` 接口、`OnnxEmbeddingProvider`、`VectorIndex` 接口、`LuceneVectorIndex`、`VectorIndexStore`、`EmbeddingModelPaths`。
- **数据目录**：新增 `<agentHome>/models/bge-small-zh-v1.5/`（模型）和 `<memory>/.vectors/`（向量索引）。两者 git 忽略。
- **首次启动**：需下载模型文件（一次性，~95MB）。缺失时打印明确提示而非启动失败。
- **行为变更**：召回路径多一层 embedding 计算；命中率与质量提升；同义改写召回不再依赖 sideQuery。
- **测试**：新增 5-6 个测试类覆盖 embedding / vector index / 检索流程；端到端 E2E 验证。

## Out of Scope

- 不改 memory 写入链路（写入工具不感知 embedding）。
- 不改 `MemoryPromptBuilder`（保持纯渲染）。
- 不做跨轮 embedding 缓存 / LRU。
- 不引入服务端向量数据库（Qdrant / Milvus / pgvector）。
- 不做 embedding 模型自动下载（仅打印指引；提供 `scripts/download-embedding-model.sh` 辅助脚本）。
- LOCAL scope 不建索引（无持久化意义）。
- 不替换 sideQuery（保留为可选精排；`embedding.enabled=false` 即退化为当前架构）。
