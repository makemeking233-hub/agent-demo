# refactor-skill-index-layer — Tasks

> 纯重构：对外行为不变。所有 task 的验收标准都包含「现有 `SkillCatalogTest` / `SkillToolTest` 仍全绿」。

## 1. 前置：补齐行为不变的保护网

- [ ] 1.1 补 `SkillCatalogTest` 覆盖：同名 skill 的项目级覆盖用户级、嵌套子目录不识别、frontmatter 缺失跳过、frontmatter 未闭合跳过
- [ ] 1.2 补 `SkillToolTest` 覆盖：`description()` 前缀拼接、`inputSchema()` 空参数、`isReadOnly()` / `checkPermissions()` 返回值
- [ ] 1.3 跑 `mvn -o -pl agent-core test -Dtest='SkillCatalogTest,SkillToolTest'` 确认基线全绿（重构前的行为快照）

## 2. 数据结构：Skill 拆 Meta + 懒加载

- [ ] 2.1 新增 `SkillMeta` record（`name` / `description` / `scope` / `dir`），加构造校验（非空检查）
- [ ] 2.2 新增 `SkillMetaTest`：字段访问、null 拒绝
- [ ] 2.3 把 `Skill` 从 record 改为 final class：持有 `SkillMeta`，`name()` / `description()` 委托，新增 `body()` 懒加载方法
- [ ] 2.4 `Skill` 的 `scope` / `dir` 访问器委托到 `SkillMeta`
- [ ] 2.5 实现 `body()` 的 mtime 缓存：`volatile cachedBody` + `volatile cachedMtimeMs`，mtime 变则重读
- [ ] 2.6 读盘失败时 `body()` 返回错误说明文本而非抛异常（不阻断对话）
- [ ] 2.7 新增 `SkillBodyCacheTest`：首次读取、mtime 未变复用缓存、mtime 变更重读、读盘失败降级
- [ ] 2.8 跑 `SkillToolTest` 确认仍全绿（`Skill` 形状变化但公开 API 不变）

## 3. 发现层：只读 frontmatter

- [ ] 3.1 `SkillCatalog` 拆出 `readFrontmatter(Path)`：只读到第二个 `---` 为止，不读正文
- [ ] 3.2 `SkillCatalog.discoverMeta(List<Path> roots)` 返回 `List<SkillMeta>`，保持同名覆盖（`removeIf`）逻辑
- [ ] 3.3 保留 `discover(List<Path>)` 作为兼容包装：内部调 `discoverMeta` 再包 `Skill`（不再持有正文）
- [ ] 3.4 `parseMeta` 解析 frontmatter 为 `SkillMeta`（name / description / scope / dir），缺失时返回 null（跳过）
- [ ] 3.5 新增 `SkillCatalogDiscoverMetaTest`：只读 frontmatter 不读正文、同名覆盖、两级目录
- [ ] 3.6 跑 `SkillCatalogTest` 确认仍全绿

## 4. 索引层：倒排 + SkillPool

- [ ] 4.1 新增 `InvertedIndex`（`Map<String, Set<String>>` keyword → skillNames），分词复用 `MemoryRecall.tokenize` 规则
- [ ] 4.2 新增 `InvertedIndexTest`：建索引、keyword 精确命中、多 keyword 并集、无命中返回空
- [ ] 4.3 新增 `SkillPool`（`AutoCloseable`）：持有 `Map<String, SkillMeta>` + per-scope `InvertedIndex`
- [ ] 4.4 `SkillPool.bootstrap(List<Path> roots)` 扫描并建索引（纯内存，无 embedding）
- [ ] 4.5 `SkillPool.instance()` 静态懒初始化 + `AtomicBoolean` 保证只跑一次；提供 `resetForTest()`
- [ ] 4.6 `SkillPool.load(String name)` 返回 `Optional<Skill>`；`SkillPool.search(String query, int k)` 返回倒排匹配的 `List<SkillMeta>`（供 Change 2）
- [ ] 4.7 `SkillPool.all()` / `allOf(MemoryScope scope)` 返回全部 / 按 scope 的元数据
- [ ] 4.8 新增 `SkillPoolTest`：bootstrap 扫两级目录、instance 幂等、load 命中/未命中、search 倒排匹配、allOf scope 过滤

## 5. 接线层：AgentLoopFactory + SkillTool

- [ ] 5.1 `AgentLoopFactory.buildTools` 改为用 `SkillPool.instance()` 拿 skill 列表（不再直接调 `discover`）
- [ ] 5.2 `SkillTool` 构造改为 `(Skill skill, SkillPool pool)`；提供单参兼容构造（内部自建 mini pool）供既有测试
- [ ] 5.3 `SkillTool.execute()` 改为调 `pool.load(name).map(Skill::body)`，读盘失败返回错误文本
- [ ] 5.4 确认 `ToolsPlugin`（若存在）也走 `SkillPool`，避免双份扫描
- [ ] 5.5 跑 `SkillToolTest` + `ToolRegistryTest` 确认全绿

## 6. 验收与门禁

- [ ] 6.1 跑 `mvn -o -pl agent-core -am test -Djacoco.skip=true`：全绿，无回归
- [ ] 6.2 跑 `mvn -o -pl agent-core -am verify -Dsurefire.excludes=**/e2e/**`：确认覆盖率门禁无新增缺口
- [ ] 6.3 确认 `openspec/specs/skills/spec.md` 现有 4 条 Requirement 的行为未受影响（逐条对照）
- [ ] 6.4 `git merge main`（门禁 4：与 main 同步）后重跑 6.1
- [ ] 6.5 `openspec archive refactor-skill-index-layer` 归档，delta 并入 `openspec/specs/skills/spec.md`