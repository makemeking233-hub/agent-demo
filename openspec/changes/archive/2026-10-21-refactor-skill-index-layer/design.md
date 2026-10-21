## Context

现状（`agent-core/.../skill/`）：

```java
public record Skill(String name, String description, String content, Path dir) {}

public static List<Skill> discover(List<Path> roots) { ... }   // 读全文
```

`discover()` 对每个 `<root>/<name>/SKILL.md` 调 `parse()`，把 frontmatter **和正文全文**都读进 `Skill.content`，全部常驻堆。随后 `AgentLoopFactory.buildTools` 调 `ToolRegistry.registerSkillTools(tools, discover(roots))`，为每个 skill `new SkillTool(skill)`；`AgentLoop.toRequest()` 每轮把 `tools.list()` 全量转 `ToolSpec`（name + description + inputSchema）进 `ChatRequest.tools`。

规模代价（按 90 token/skill 估）：

| skill 数 | tools token | 占 83808 compact 阈值 | 常驻堆（正文按 2KB/skill） |
|---------|------------|---------------------|--------------------------|
| 20（现状量级） | ~1.8k | 2% | 40 KB |
| 1000 | ~90k | **107%** | 2 MB |
| 10000 | ~900k | **1073%** | **20 MB** |

Memory 侧已经有成熟的三层召回基建（`memory/embedding/OnnxEmbeddingProvider` + `VectorIndexStore` + `MemoryRetriever`），但 Skill 侧完全没复用。

约束：

- 本 change **纯重构**，对外可观测行为必须 100% 不变（现有 `SkillCatalogTest` / `SkillToolTest` 全绿即证明）。
- JDK 17 + Spring Boot 3.2；不引入 Lombok / 数据库。
- Change 2（`add-skill-demand-loading`）依赖本 change 产出的 `SkillPool`，所以本 change 必须先落地。

## Goals / Non-Goals

**Goals:**

- 正文不再常驻堆：`SkillMeta` 只含元数据，正文按需从 `dir` 读盘。
- 建索引基建：倒排索引（keyword → skillNames）+ embedding 索引（复用 Memory 侧 ONNX 基建），供 Change 2 做「倒排预筛 → KNN 精排」。
- 正文缓存带 mtime 校验，用户改 `SKILL.md` 后自动失效重读。
- embedding 不可用时严格降级（`isReady()=false` → 倒排仍可用），绝不抛异常阻断主流程。
- 现有测试全绿（行为不变的硬证据）。

**Non-Goals:**

- 不改 `SkillTool` 的 `name()` / `description()` / `inputSchema()` / `execute()` 语义。
- 不做按需暴露（每轮只放 top-k 进 tools）——那是 Change 2 的职责。
- 不加 `always: true` frontmatter 字段、不加频次统计文件——都是 Change 2。
- 不改 config schema。
- 不动前端。

## Decisions

### D1: `Skill` 拆为 `SkillMeta` + 懒加载正文

```java
// 常驻索引结构（不含正文）
public record SkillMeta(String name, String description, MemoryScope scope, Path dir) {}

// 运行时包装（元数据 + 懒加载）
public final class Skill {
    private final SkillMeta meta;
    private volatile String cachedBody;     // 首次 load 后缓存
    private volatile long cachedMtimeMs;

    public String name() { return meta.name(); }
    public String description() { return meta.description(); }
    public String body() { /* mtime 校验 + 读盘 */ }
}
```

**备选**：
- A. 保留 `Skill` record 但 `content` 改 `Supplier<String>` — record 失去值语义，`equals/hashCode` 变身份比较，会破坏 `discover()` 的 `removeIf(same name)` 去重。**否决**。
- B. `SkillMeta` + 单独的 `BodyLoader` 服务 — 多一层间接，`SkillTool` 要同时持有两者。**否决**。
- C. ✅ `SkillMeta`（record，纯数据）+ `Skill`（final class，元数据 + 懒加载）——保留 `Skill` 的公开 API 形状（`name()/description()`），`content` 字段改 `body()` 方法。

### D2: `discover()` 拆为 `discoverMeta()` + 保留 `discover()` 兼容

```java
// 新：只读 frontmatter
public static List<SkillMeta> discoverMeta(List<Path> roots);

// 保留：内部调 discoverMeta 再包 Skill（供既有调用方与测试）
public static List<Skill> discover(List<Path> roots);
```

`discover()` 保留的理由：`SkillsPlugin.tools()` 与既有测试都调它，删掉会破坏编译面；且它现在也只是薄包装（不再持有正文）。

### D3: `SkillPool` 单例，跨 session 共享

```java
public final class SkillPool implements AutoCloseable {
    private static volatile SkillPool instance;

    private final Map<String, SkillMeta> byName;         // name → meta
    private final Map<MemoryScope, InvertedIndex> inverted; // scope → 倒排
    private final Map<MemoryScope, VectorIndex> vectors;   // scope → 向量索引
    private final EmbeddingProvider embedder;              // 可空 = 不可用

    public static SkillPool bootstrap(List<Path> roots, EmbeddingProvider p);
    public static SkillPool instance();                     // AgentLoopFactory 注入
    public List<SkillMeta> search(String query, int k);     // 供 Change 2
    public Optional<Skill> load(String name);               // 供 SkillTool.execute
}
```

**为什么单例**：skill 是**全局资源**（与 Memory 的 USER scope 类似），不该每 session 重建索引——10000 条 embedding 重建一次要几分钟。

**为什么 per-scope 分开**：与 `MemoryRetriever` 一致，PROJECT 与 USER 不混合检索。

**备选**：per-session 实例 — 语义上隔离但索引重建代价不可接受。**否决**。

### D4: 倒排索引只索引 name + description 的分词

```java
// keyword → Set<skillName>
Map<String, Set<String>> invertedIndex;
```

分词复用 `MemoryRecall.tokenize` 的规则（长度 ≥ 2、过滤标点、中英文标点），保证两套检索的分词行为一致。

**不索引 tags**：本 change 不引入 tags 字段（那是 Change 2 的可选增强）。

### D5: embedding 索引复用 Memory 侧基建，但独立实例

```java
// 复用类，不复用实例：
//   OnnxEmbeddingProvider  ← 同一份模型文件、懒加载
//   LuceneVectorIndex      ← 每 scope 一个独立索引实例
//   mtime.json             ← 放在 <skillRoot>/.vectors/mtime.json（与 memory 侧同构但不同目录）
```

**为什么复用类而非实例**：Memory 的索引存在 `~/.agent-demo/memory/.vectors/`，Skill 的在 `~/.agent-demo/skills/.vectors/` 与 `<cwd>/.agent-demo/skills/.vectors/`，物理隔离但代码同构。

**备选**：抽出通用 `VectorIndexStore<T>` 泛型 — 会动 Memory 侧代码，违反「本 change 不改 Memory 行为」的边界。**否决**（留给后续重构）。

### D6: `SkillTool.execute()` 改调 `SkillPool.loadBody()`

```java
@Override
public Mono<ToolResult<String>> execute(String input, Tool.ToolContext ctx) {
    return Mono.fromSupplier(() -> pool.load(this.skill.name())
            .map(Skill::body)
            .orElse("[skill " + this.skill.name() + " 正文加载失败]"));
}
```

**失败语义**：读盘失败返回错误文本而非抛异常——与「记忆/工具失败不阻断主对话」一致。

### D7: mtime 校验缓存

```java
public String body() {
    long now = Files.getLastModifiedTime(dir.resolve("SKILL.md")).toMillis();
    if (cachedBody != null && cachedMtimeMs == now) return cachedBody;
    String fresh = Files.readString(dir.resolve("SKILL.md"), FRONTMATTER_BODY_OFFSET...);
    cachedBody = fresh; cachedMtimeMs = now;
    return fresh;
}
```

`volatile` 字段 + 无锁（允许偶发重复读盘，换取零同步开销；读盘是幂等的）。

**为什么不用 `synchronized`**：多线程同时读同一 skill 正文是最坏情况也只是多几次 `readString`，不值得串行化。

### D8: embedding 不可用的严格语义

沿用 Memory 侧 T8b.0 教训：

> 推理不可用时必须 `isReady()=false`。若返回 `true` 而 `embed()` 给零向量，KNN 会因为「零向量与所有条目 cosine 都是 0」而任意返回 k 条——反而注入无关结果。

本 change 里 embedding 只用于**索引构建**（不是在线检索），所以语义更宽松：`isReady()=false` 时直接**不建向量索引**，`search()` 退化为纯倒排匹配。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| **「行为不变」被破坏**：现有测试只覆盖小规模（几个 skill），可能漏掉边界 | T1 任务先补 `SkillCatalogTest` 的覆盖用例（同名覆盖、无 frontmatter 跳过、frontmatter 未闭合、两级目录优先级），再动实现 |
| **embedding 预计算耗时**：10000 条 bge-small-zh 推理 | 放在 `SkillPool.bootstrap` 里后台线程跑，不阻塞启动；未完成前 `search()` 走倒排 |
| **SkillTool 构造签名变更**：`new SkillTool(skill)` 变 `new SkillTool(skill, pool)` | 提供单参兼容构造（内部自建 mini pool），既有测试不改也能编译 |
| **Lucene 依赖引入**：`memory/embedding` 已在 agent-core 用了 Lucene，本 change 不新增依赖 | 确认 `agent-core/pom.xml` 已有 lucene 依赖；若无则显式添加（与 Memory 侧同版本） |
| **磁盘 IO 移到运行时**：原来启动时一次读完，现在每次调用可能读盘 | mtime 缓存把重复读降到 0；首次读有 2KB 量级的开销，可忽略 |

## Migration Plan

纯重构，无数据迁移、无配置迁移、无回滚脚本需求。回滚 = `git revert` 单一提交。

部署顺序：
1. 合入 main（本 change 单独一个 PR）
2. Change 2 在其之上开发

**不需要停机**：不涉及持久化格式变更（`SKILL.md` 本身格式不变），只改运行时读取方式。

## Open Questions

1. **`SkillPool.bootstrap` 的调用时机**：`AgentLoopFactory.buildTools` 里调（每进程一次，还是每 AgentLoop 一次）？倾向：静态 `instance()` 懒初始化 + `AtomicBoolean` 保证只跑一次，但测试需要 `resetForTest()`。
2. **embedding 索引是否同步构建**：倾向不建（省 ~几分钟启动时间 + 20MB 磁盘），因为 Change 2 才需要向量；倒排索引 Change 1 就建（成本低、立即有用）。待 Change 2 细化时定。
3. **`.vectors/` 目录是否需要 gitignore**：`~/.agent-demo/skills/` 不在仓库里，无需 gitignore；但 `<cwd>/.agent-demo/skills/.vectors/` 在项目里——若项目把 skills 纳入版本控制则需加 ignore。