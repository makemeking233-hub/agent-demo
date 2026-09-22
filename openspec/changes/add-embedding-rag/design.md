## Context

`fix-memory-recall-wiring`（2026-09-21 归档）后，Memory 召回已能每轮按 query 动态触发，但召回后端仍是 v0.3 的 **LLM 选择式 sideQuery**——候选数被截断到 8 条（prompt 长度限制），且触发时多一次 LLM 调用。本 change 在「每轮召回」地基上，把后端从「LLM 选择」升级为 **embedding 向量检索**，让候选数无硬限、检索毫秒级、无网络依赖。

约束：
- 零新外部服务（不上 Qdrant / Milvus / pgvector）。
- JDK 17 + Spring Boot 3.2 + Maven 3.9 + 不引入 Lombok（项目硬约束）。
- 多 agent 并行环境：worktree 隔离，禁止 `git add -A`，提交清单只能含本 change 文件。
- 数据隔离（全局规则 §10）：embedding 测试不触碰用户真实 `~/.agent-demo/`，使用 `agent.demo.home` 系统属性 + 临时 `user.dir` 双重隔离。
- 既有 `MemoryRetriever` 编排逻辑（字面 + sideQuery + 并集去重）保留，新增 embedding 粗排作为可降级插入层。

## Goals / Non-Goals

**Goals:**

- 提供**纯本地**的 embedding 计算（ONNX + bge-small-zh），离线可用，零 API 成本。
- 提供**进程内**的向量索引（Lucene HNSW），检索 < 10ms。
- `MemoryRetriever` 三层架构（字面 → embedding 粗排 → sideQuery 精排），每层独立可降级。
- 启动 mtime 比对懒加载：只重算 mtime 变化的 .md 文件。
- 配置可关闭（`memory.embedding.enabled=false` 退回当前字面 + sideQuery 架构）。
- 首次启动缺失模型时**不致命**：打印明确指引，不阻断 agent 运行。

**Non-Goals:**

- 不引入服务端向量数据库。
- 不改 memory 写入链路（写工具不感知 embedding）。
- 不做 embedding 自动下载（仅提供 `scripts/download-embedding-model.sh` 辅助脚本）。
- 不做跨轮 embedding 缓存。
- LOCAL scope 不建索引（无持久化意义）。
- 不替换 sideQuery（保留为可选精排；embedding 启用时它仍是粗排后的窄候选精排）。
- 不引入 ONNX GPU 加速（仅 CPU；GPU 加速后续 change 再议）。

## Decisions

### D1: 三层召回架构（粗排 + 精排解耦）

`MemoryRetriever.retrieve()` 内部依次执行：

```
阶段 1（字面）：MemoryRecall 召回 ≥ 0.3 分的条目 → 字面命中集
阶段 2（embedding）：向量索引 KNN 取 top-K_embed，与字面命中并集去重
阶段 3（sideQuery，可选）：字面 ∪ embedding < k 且候选 ≥ minCandidates 时发起 LLM 精排
```

三阶段都返回候选条目 + filename，`mergeByFilename` 维持字面优先 → embedding 补 → sideQuery 补的去重顺序。

**理由**：保留既有 sideQuery 精确语义判定能力；embedding 解决"候选截断到 8"的硬限；字面召回保留以避免 sideQuery 在无网络时仍依赖 LLM。任一阶段失败静默降级，不阻断。

**备选**：纯 embedding 替换 sideQuery。否决——失去 sideQuery 的精确语义判定，且与既有 spec 行为不兼容。

### D2: Embedding 接口与实现

```java
@FunctionalInterface
public interface EmbeddingProvider {
    float[] embed(String text);          // 归一化后的 512 维向量
    int dimensions();                    // 返回维度（512 for bge-small-zh）
}
```

`OnnxEmbeddingProvider implements EmbeddingProvider`：封装 `ai.onnxruntime.OrtEnvironment` + `OrtSession`，加载 bge-small-zh-v1.5 模型。线程安全（OrtSession 线程安全）；懒加载模型（首次 `embed()` 时初始化）。

**理由**：`OrtSession` 一次加载多次推理，无网络、CPU ~5ms / 条。线程安全允许多轮并发检索。

**备选**：每次推理重新加载。否决——95MB 模型加载慢。

### D3: VectorIndex 接口

```java
public interface VectorIndex {
    void add(String id, float[] vector);          // 重复 id 覆盖
    List<ScoredItem> search(float[] query, int k); // 返回 (id, score) 降序
    int size();
    void flush();                                  // 刷盘
    void close();                                  // 释放资源
}
```

实现 `LuceneVectorIndex`：基于 `org.apache.lucene.core` + `lucene-analysis-common`，使用 `Lucene94HnswVectorFormat`（Lucene 9.4+ 提供 HNSW KNN）。HNSW 参数 `M=16, efConstruction=200`。

**理由**：Lucene HNSW 索引持久化、跨重启可用；`Lucene94HnswVectorFormat` 是官方 KNN 实现，无需额外算法库。

**备选**：暴力 cosine（JSONL）。否决——决策 2 用户坚持 HNSW。

### D4: VectorIndexStore（mtime 比对懒加载）

```java
public class VectorIndexStore {
    Path indexDir;                              // <scopeDir>/.vectors/
    Map<MemoryScope, VectorIndex> indexes;     // 缓存的索引实例
    Map<Path, Long> mtimeCache;                 // 文件 mtime 缓存
}
```

启动流程：
1. 加载 mtime 缓存（`<scopeDir>/.vectors/mtime.json`，git 忽略）
2. 对每个 .md 文件比对当前 mtime：
   - mtime 未变且索引中存在该 filename → 跳过
   - mtime 变化或不在索引 → 重新算 embedding 并 upsert
3. 退出或显式 `flush()` 时保存 mtime 缓存

不拦截写入路径——写 .md 时**不**更新索引；下次启动时 mtime 变化触发重算。故障自愈（mtime 缓存损坏 → 全部重算）。

**理由**：实现极简（约 50 行），覆盖了 90% 价值（启动后稳态几乎无开销）。

**备选**：完整失效检测 + 写入路径拦截。否决——决策 4 用户选择轻量版。

### D5: 模型加载与首次启动处理

模型文件位置：`<agentHome>/models/bge-small-zh-v1.5/model.onnx`（git 忽略）。

首次启动时 `OnnxEmbeddingProvider.init()`：
1. 检查文件存在 → 不存在则记录 WARN 并打印指引：
   ```
   [WARN] Embedding model not found at <path>
   To enable embedding-based memory retrieval, run:
     bash scripts/download-embedding-model.sh
   Or set memory.embedding.enabled=false to disable.
   ```
2. 文件存在 → 加载 ONNX session

`embed()` 在模型缺失时抛 `EmbeddingUnavailableException`，被 `MemoryRetriever` 捕获 → 跳过 embedding 阶段，仅字面 + sideQuery（既有的两层架构）。

**理由**：缺失不致命，符合项目一贯的"失败降级"原则（见既有 spec "sideQuery 失败静默降级"）。

### D6: 三层架构的触发门槛

```
embedding 触发条件：embedding.enabled == true 且 EmbeddingProvider.isReady()
sideQuery 触发条件：字面 ∪ embedding < k AND 候选 ≥ minCandidates AND sideQuery.enabled
```

这意味着：
- 关闭 embedding → 走当前字面 + sideQuery 两层
- 字面已召满 → 跳过 embedding 与 sideQuery（短路）
- embedding 阶段后已召满 → 跳过 sideQuery（节省 LLM 调用）

**理由**：每层独立短路，最大化节省计算与成本。

### D7: 配置可关闭

`AgentConfig.Memory` 扩展为：

```java
public record Memory(
    SideQuery sideQuery,
    boolean dynamicRetrieval,
    Embedding embedding) {}

public record Embedding(
    boolean enabled,
    String modelPath,              // 默认 <agentHome>/models/bge-small-zh-v1.5/model.onnx
    HnswConfig hnsw) {}

public record HnswConfig(int m, int efConstruction) {}  // 默认 (16, 200)
```

`ConfigLoader.mergeMemory` 解析 `memory.embedding.*`。`embedding.enabled = false` 时 `AgentLoopFactory` 不构造 `OnnxEmbeddingProvider`，整个三层架构退化为字面 + sideQuery（v0.3 行为）。

## Risks / Trade-offs

- [Lucene HNSW 依赖体积 +10MB] → 一次性代价，换未来扩展空间；agent-core 从 ~5MB 增到 ~15MB
- [ONNX Runtime 依赖体积 +30MB] → 一次性代价，换离线可用 + 零 API 成本
- [首次启动慢 5-10s（加载模型 + 重建索引）] → 仅首次，后续 mtime 缓存命中后 < 1s
- [bge-small-zh 模型需手动下载] → 提供 `scripts/download-embedding-model.sh` 辅助；缺失不致命
- [Lucene 9.x HNSW 是较新 API，版本兼容风险] → 锁定 lucene-core 9.10.0（2024-04 stable）；如失败可降级为 9.4.0
- [每轮 `embed(query)` 是同步调用，会阻塞 toRequest ~5ms] → 接受；后续可异步化（后续 change）
- [mtime 比对不防秒级精度内的快速连续修改] → 可接受；mtime 缓存损坏 → 全部重算（自愈）
- [三层架构下日志更难排查"哪一层召回了什么"] → 设计阶段在 `MemoryRetriever` 加 debug 日志（`log.debug("embedding 召回: {} 条", ...)`）
- [测试中真用 ONNX 推理需要模型文件 → CI 怎么办] → 单测用 `MockEmbeddingProvider`（返回固定向量）；集成测试用真实模型（仅本地跑）

## Migration Plan

1. **新增依赖**：`agent-core/pom.xml` 加 `onnxruntime` + `lucene-core` + `lucene-analysis-common`
2. **新增包** `com.example.agent.memory.embedding`：6 个类
3. **新增配置**：`AgentConfig.Memory.embedding` + `ConfigLoader.mergeMemory` 解析
4. **改造**：`MemoryRetriever` 注入 `EmbeddingProvider` + `VectorIndexStore`，加 embedding 粗排阶段
5. **装配**：`AgentLoopFactory` 构造 embedding provider + index store，注入 retriever
6. **首次启动处理**：模型缺失时 WARN + 指引，不致命
7. **辅助脚本**：`scripts/download-embedding-model.sh`（HF mirror 链接 + curl 指令）
8. **测试**：embedding / vector index / retriever / 端到端装配 四组 + 7-9 个测试类
9. **文档**：更新 `memory-recall-deep-dive.md` §6 + §7.2；新增 `embedding-design.md` 描述 ONNX / Lucene 集成细节

**回滚策略**：配置 `memory.embedding.enabled=false` 即退回字面 + sideQuery 两层架构；或删除 `memory.embedding` 整段后回滚代码 commit。已合并的 `fix-memory-recall-wiring`（每轮召回链路）独立可用。

## Open Questions

- Lucene HNSW 实际打包大小可能比预估大；待 `mvn dependency:tree` 跑后确认（影响 pom 注释中的"~10MB"）
- 模型下载是否走 HF mirror（国内访问 huggingface.co 不稳定）——待 `download-embedding-model.sh` 实施时定
