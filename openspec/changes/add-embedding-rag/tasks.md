# Tasks: 接入 embedding 向量检索（Memory 三层召回）

TDD 节奏：每组内先写/改测试（红）→ 实现（绿）→ 提交并 push 本分支。

依赖基线：`fix-memory-recall-wiring`（已归档于 2026-09-21）已让「每轮按 query 召回」链路可达，本 change 在其基础上把召回后端升级为「字面 + embedding 粗排 + sideQuery 精排」三层架构。

## 1. 配置：`memory.embedding` 段

- [x] 1.1 测试先红：`AgentConfig` 增加 `Embedding(enabled, modelPath, HnswConfig(m, efConstruction))` 与 `Memory(..., embedding)` 扩展；断言 `defaults().memory().embedding().enabled()==true`、缺省 modelPath 指向 `<agentHome>/models/bge-small-zh-v1.5/model.onnx`、HnswConfig 默认 `(16, 200)`
- [x] 1.2 实现：`AgentConfig.Memory` record 增加 `Embedding embedding` 字段；`AgentConfig.Memory.Embedding` / `AgentConfig.Memory.HnswConfig` record；`defaults()` 给 embedding=true, m=16, efConstruction=200；`ConfigLoader.mergeMemory` 解析 `memory.embedding.*`（enabled / modelPath / hnsw.m / hnsw.efConstruction），缺失保持 base 值
- [x] 1.3 `mvn -o -pl agent-core test -Dtest='ConfigLoaderTest'` 转绿后 commit + push 本分支

## 2. EmbeddingProvider 接口 + ONNX 实现

- [x] 2.1 测试先红：`EmbeddingProviderTest` —— `MockEmbeddingProvider` 返回固定 512 维向量；`OnnxEmbeddingProvider` 在模型文件缺失时 `isReady()=false`（不抛异常）；`isReady()=true` 时 `embed()` 返回非空 float[512]；`dimensions()==512`
- [ ] 2.2 实现：`memory/embedding/EmbeddingProvider` 接口（`embed(text)`, `dimensions()`, `isReady()`）；`OnnxEmbeddingProvider` 实现懒加载 ONNX session（首次 embed 时 `OrtEnvironment.getEnvironment()` + `OrtSession.create(modelPath)`），捕获异常记 WARN 并保持 `unavailable`；暴露模型路径解析（`AgentPaths.agentHome() + "/models/bge-small-zh-v1.5/model.onnx"`）；线程安全
- [ ] 2.3 新增 `OnnxEmbeddingProviderTest` 端到端（用测试 fixture 写一个最小可用的 ONNX 模型文件 or 用 Mock 替换 session 加载逻辑）—— 验证模型加载成功路径
- [ ] 2.4 `mvn -o -pl agent-core test -Dtest='EmbeddingProviderTest'` 转绿后 commit + push

## 3. VectorIndex 接口 + Lucene HNSW 实现

- [ ] 3.1 测试先红：`VectorIndexTest` —— `LuceneVectorIndex(indexDir, dims)` add/search 基础路径；add 重复 id 覆盖；search 返回 `(id, score)` 降序；size() 正确；flush + 重建索引后 search 仍能找到
- [ ] 3.2 实现：`memory/embedding/VectorIndex` 接口（`add(id, vec)`, `search(vec, k) -> List<ScoredItem>`, `size()`, `flush()`, `close()`）；`ScoredItem(id, score)` record；`LuceneVectorIndex` 使用 `IndexWriter` + `Lucene94HnswVectorFormat` (HNSW)；持久化到 `indexDir`；close() 释放 writer
- [ ] 3.3 `mvn -o -pl agent-core test -Dtest='VectorIndexTest'` 转绿后 commit + push

## 4. mtime 懒加载：`VectorIndexStore`

- [ ] 4.1 测试先红：`VectorIndexStoreTest` —— 给定 scope 目录含 2 个 .md 文件 + mtime 缓存（其中 1 个 mtime 匹配），断言只对不匹配的那个文件调用 `embed()`；首次启动无缓存时全部重算；缓存文件损坏时全部重算（自愈）；flush 后 mtime.json 落盘
- [ ] 4.2 实现：`memory/embedding/VectorIndexStore` 维护 `(scope -> VectorIndex)` + mtime 缓存；`load(scope, memoryEntries)` 流程：读 mtime.json → 对每个条目比对 mtime → 变化/缺失则调 embedding 重算并 upsert → flush mtime.json
- [ ] 4.3 `mvn -o -pl agent-core test -Dtest='VectorIndexStoreTest'` 转绿后 commit + push

## 5. 三层召回：`MemoryRetriever` 接入 embedding

- [ ] 5.1 测试先红：`MemoryRetrieverTest` 新增用例 —— ① embedding 不可用时行为与 v0.4 一致（仅字面 + sideQuery）；② embedding 可用且字面不足时，embedding 召回的条目出现在结果中（不调 LLM）；③ 字面 + embedding 已召满时跳过 sideQuery；④ 字面 ∪ embedding 仍不足时 sideQuery 触发；⑤ embed / search 抛异常时静默降级
- [ ] 5.2 实现：`MemoryRetriever` 构造器增加 `EmbeddingProvider` + `VectorIndexStore` + `kEmbed`（默认 10）；`retrieve()` 在字面召回后、sideQuery 前增加 embedding 阶段（`embedding.search(query, kEmbed)` → `mergeByFilename` 与字面并集去重）；sideQuery 触发条件由 `hit.size() < k` 改为 `hit.size() < k`（注意：此时 hit 已含 embedding 结果——含义不变，但语义是"三层并集不足"）
- [ ] 5.3 `mvn -o -pl agent-core test -Dtest='MemoryRetrieverTest'` 转绿后 commit + push

## 6. 装配：`AgentLoopFactory` 接入 embedding provider + index store

- [x] 6.1 测试先红：`AgentLoopFactoryMemoryTest` 新增用例 —— `embedding.enabled=true` 时 `buildLoop` 产出的 agent 在首轮请求的 system prompt 中含 `(relevant)`（已有用例扩一条含 embedding 路径即可）；`embedding.enabled=false` 时行为与 v0.4 一致（验证三层降为两层）；`modelPath` 不存在时首轮请求**不**报错（仅 WARN 日志）
- [x] 6.2 实现：`AgentLoopFactory` 按 `embedding.enabled` 决定是否构造 `OnnxEmbeddingProvider` + `VectorIndexStore`；`MemoryRetriever` 构造时传入；缺失模型文件场景下 `OnnxEmbeddingProvider.unavailable=true`，装配层正常返回（不抛）
- [x] 6.3 `mvn -o -pl agent-core,agent-web test` 全绿后 commit + push

## 7. .gitignore + 首次启动提示 + 下载脚本

- [x] 7.1 测试先红：`EmbeddingGitignoreTest`（定位仓库根 grep .gitignore）断言 `**/models/` 与 `**/.vectors/` 模式存在
- [x] 7.2 实现：`.gitignore` 增加 `**/models/` 与 `**/.vectors/`；`OnnxEmbeddingProvider` 模型缺失时的 WARN 日志含模型路径 + `bash scripts/download-embedding-model.sh` 提示；新增 `scripts/download-embedding-model.sh`（HF 官方 + hf-mirror 双源 curl → chmod 0600）。
  注：脚本放 `scripts/` 而非 `tools/`——后者被 `.gitignore` 的 `/tools/` 规则忽略
- [x] 7.3 `mvn -o -pl agent-core test` 转绿后 commit + push

## 8. 依赖接入：`pom.xml`

- [x] 8.1 测试先红：以 `VectorIndexTest`（真实 Lucene HNSW）+ `EmbeddingGitignoreTest` 作为依赖接入的验证（原计划的 `PomDependencyTest` 改为由编译+运行测试间接验证，避免为测 pom 而测 pom）
- [x] 8.2 实现：`agent-core/pom.xml` 增加三个依赖；锁定版本（ONNX Runtime **1.19.2**，Lucene **9.11.1**）
- [x] 8.3 跑 `mvn -pl agent-core dependency:resolve` 确认依赖可解析（走阿里云镜像），替代原计划的体积核对
- [x] 8.4 commit + push

### 8b. ONNX 推理接通（**待独立 change 处理**）

> **计划遗漏**：design/proposal 只写了「加载 ONNX 模型」，未识别出 **tokenizer** 这一必需组件。
> bge-small-zh-v1.5 是 BERT 架构，ONNX 模型只做「token ids → 向量」的推理；
> 「文本 → token ids」需要 BERT WordPiece tokenizer（中文版 BasicTokenizer + WordPiece 贪心最长匹配），
> 依赖 vocab.txt（约 110KB，已探测 hf-mirror / huggingface 两源均可下载 HTTP 200）。
> 估算实现量约 400 行（tokenizer ~200 + 推理封装 ~100 + 测试 ~100），且有独立技术决策点
>（手写 tokenizer vs 引入 `ai.djl.huggingface:tokenizers` 库），适合拆为独立 change。

- [x] 8b.0 **已修正确性问题**：ONNX 推理未接通期间，`OnnxEmbeddingProvider.isReady()` 必须为 `false`。
  否则 `embed()` 返回零向量，而零向量对所有条目的 cosine 都是 0，KNN 会任意返回 k 条——
  等于往召回结果里注入无关条目。宁可少一层，也不要污染结果。
- [x] 8b.1 更新 design.md：新增 D2.1 小节补 tokenizer 组件、vocab.txt、external data 模型来源与「推理未接通必须 unavailable」的安全约束
- [x] 8b.2 实现 BERT WordPiece tokenizer（**手写**，17 个单测全绿）
- [x] 8b.3 `OnnxEmbeddingProvider.ensureLoaded()` 接入真实 `OrtSession.create()`；`embed()` 实现 tokenize → run → CLS 池化 → L2 归一化
- [x] 8b.4 用真实模型做端到端验证（4/4 通过，含「代码规范」vs「编码风格约定」相似度 > 无关文本的**核心价值验证**）

## 9. 端到端验证与收尾

- [x] 9.1 端到端装配测试：**已由三层测试合并覆盖**——装配路径由 `AgentLoopFactoryMemoryTest`（9 用例，含经 `buildLoop` 的端到端降级验证）覆盖；三层召回语义由 `MemoryRetrieverEmbeddingTest`（7 用例）覆盖；真实模型下的同义改写召回由 `OnnxEmbeddingProviderE2ETest`（4 用例）覆盖。未再另建「buildLoop + 真模型」测试（需把 95MB 模型塞进 `target/test-home`，成本高于收益）
- [x] 9.2 `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` 全绿（agent-core 643 通过 + 4 条件跳过；agent-web 386 全绿；BUILD SUCCESS）
- [x] 9.3 更新 `docs/design/memory-recall-deep-dive.md`：§7.2 标注为已完成，并记录与原计划的差异（Lucene 而非 JSONL、三层而非替换、补 tokenizer）
- [x] 9.4 新增 `docs/design/embedding-design.md`：三层架构、组件划分、数据流、降级矩阵、模型准备（含 external data 踩坑）、实测数据、已知限制、配置
- [x] 9.5 `openspec archive` 归档本 change（delta spec 并入 `openspec/specs/memory/spec.md` 与新 spec `openspec/specs/memory-embedding/spec.md`）后 commit + push
