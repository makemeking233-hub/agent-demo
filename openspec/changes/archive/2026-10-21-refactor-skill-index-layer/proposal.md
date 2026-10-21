## Why

当前 `SkillCatalog.discover()` 在启动时把每个 `SKILL.md` 的**正文全文**读进内存并常驻（`Skill.content`），10000 个 skill 会占用数百 MB 常驻堆，且每个 skill 变成一个 `SkillTool` 使 tools 段随规模线性膨胀（约 90 token/skill，10000 个 ≈ 900k token，远超 83808 的 compact 阈值）。

本 change 是纯重构：把「元数据常驻 + 正文按需加载 + 建索引」这三件事从 `SkillCatalog` 里拆出来，**对外行为 100% 不变**（现有 `SkillCatalogTest` / `SkillToolTest` 全绿即证明），为后续 `add-skill-demand-loading`（按需暴露）铺好基建。

## What Changes

- `Skill` record 拆为 `Skill`（元数据 + 懒加载正文）与 `SkillMeta`（纯元数据，不含正文）：`SkillMeta` 常驻索引，`Skill.body()` 首次调用时按 `dir` 读盘。
- `SkillCatalog.discover()` 改为只读 frontmatter（读到第二个 `---` 为止），不再把正文读进内存；新增 `discoverMeta()` 返回 `List<SkillMeta>`。
- 新增 `SkillPool`（单例，跨 session 共享）：持有全部 `SkillMeta`，并按需构建**倒排索引**（keyword → skillNames）与 **embedding 索引**（复用 `OnnxEmbeddingProvider` + `VectorIndexStore`，512 维 HNSW）。
- 新增懒加载正文 API：`SkillPool.loadBody(name)` + `mtime` 校验缓存（`<agentHome>/.vectors` 同构的 mtime.json 模式），文件变更后自动重读。
- `SkillTool.execute()` 改为调 `SkillPool.loadBody(name)` 而非返回构造时已持有的 `skill.content()`；**Tool 的 name / description / inputSchema 保持不变**。
- embedding 不可用时（模型缺失 / ONNX 加载失败）严格 `isReady()=false`，倒排索引仍可用（退化为纯关键词检索），不抛异常。

## Capabilities

### New Capabilities

（无——本 change 为纯重构，不新增 capability）

### Modified Capabilities

（无——对外可观测行为不变：发现规则、frontmatter 解析、SkillTool 语义、SkillsPlugin 注入全部维持现状，因此不写 delta spec）

## Impact

- 受影响类：`agent-core/.../skill/Skill`（拆 record）、`skill/SkillCatalog`（discover 只读 frontmatter + 新增 discoverMeta）、`skill/SkillTool`（execute 改懒加载）、`core/AgentLoopFactory`（注入 SkillPool）
- 新增类：`skill/SkillMeta`、`skill/SkillPool`（含倒排索引 + embedding 索引 + mtime 缓存）
- 复用既有基建：`memory/embedding/OnnxEmbeddingProvider`、`memory/embedding/VectorIndexStore`、`memory/embedding/LuceneVectorIndex`（不重复造轮子）
- 依赖：新增 ONNX Runtime 传递依赖（agent-core 已有，见 `memory/embedding`）
- 测试：现有 `SkillCatalogTest` / `SkillToolTest` 必须全绿（行为不变证明）；新增 `SkillPoolTest` / `SkillMetaTest` / mtime 缓存失效测试
- 不涉及前端、不涉及 config schema、不涉及 spec delta