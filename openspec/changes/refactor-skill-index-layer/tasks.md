# refactor-skill-index-layer — Tasks

> 纯重构：对外行为不变。所有 task 的验收标准都包含「现有 `SkillCatalogTest` / `SkillToolTest` 仍全绿」。

## 1. 前置：补齐行为不变的保护网

- [x] 1.1 补 `SkillCatalogTest` 覆盖：同名 skill 的项目级覆盖用户级、嵌套子目录不识别、frontmatter 缺失跳过、frontmatter 未闭合跳过
- [x] 1.2 补 `SkillToolTest` 覆盖：`description()` 前缀拼接、`inputSchema()` 空参数、`isReadOnly()` / `checkPermissions()` 返回值
- [x] 1.3 跑 `mvn -o -pl agent-core test -Dtest='SkillCatalogTest,SkillToolTest'` 确认基线全绿（重构前的行为快照）

## 2. 数据结构：Skill 拆 Meta + 懒加载

- [x] 2.1 新增 `SkillMeta` record（`name` / `description` / `scope` / `dir`），加构造校验（非空检查）
- [x] 2.2 新增 `SkillMetaTest`：字段访问、null 拒绝
- [x] 2.3 把 `Skill` 从 record 改为 final class：持有 `SkillMeta`，`name()` / `description()` 委托，新增 `body()` 懒加载方法
- [x] 2.4 `Skill` 的 `scope` / `dir` 访问器委托到 `SkillMeta`
- [x] 2.5 实现 `body()` 的 mtime 缓存：`volatile cachedBody` + `volatile cachedMtimeMs`，mtime 变则重读
- [x] 2.6 读盘失败时 `body()` 返回错误说明文本而非抛异常（不阻断对话）
- [x] 2.7 新增 `SkillBodyCacheTest`：首次读取、mtime 未变复用缓存、mtime 变更重读、读盘失败降级
- [x] 2.8 跑 `SkillToolTest` 确认仍全绿（`Skill` 形状变化但公开 API 不变）

## 3. 发现层：只读 frontmatter

- [x] 3.1 `SkillCatalog` 拆出 `readFrontmatter(Path)`：只读到第二个 `---` 为止，不读正文
- [x] 3.2 `SkillCatalog.discoverMeta(List<Path> roots)` 返回 `List<SkillMeta>`，保持同名覆盖（`removeIf`）逻辑
- [x] 3.3 保留 `discover(List<Path>)` 作为兼容包装：内部调 `discoverMeta` 再包 `Skill`（不再持有正文）
- [x] 3.4 `parseMeta` 解析 frontmatter 为 `SkillMeta`（name / description / scope / dir），缺失时返回 null（跳过）
- [x] 3.5 新增 `SkillCatalogDiscoverMetaTest`：只读 frontmatter 不读正文、同名覆盖、两级目录
- [x] 3.6 跑 `SkillCatalogTest` 确认仍全绿

## 4. 索引层：倒排 + SkillPool

- [x] 4.1 新增 `InvertedIndex`（`Map<String, Set<String>>` keyword → skillNames），分词复用 `MemoryRecall.tokenize` 规则
- [x] 4.2 新增 `InvertedIndexTest`：建索引、keyword 精确命中、多 keyword 并集、无命中返回空
- [x] 4.3 新增 `SkillPool`（`AutoCloseable`）：持有 `Map<String, SkillMeta>` + per-scope `InvertedIndex`
- [x] 4.4 `SkillPool.bootstrap(List<Path> roots)` 扫描并建索引（纯内存，无 embedding）
- [x] 4.5 `SkillPool.instance()` 静态懒初始化 + `AtomicBoolean` 保证只跑一次；提供 `resetForTest()`
- [x] 4.6 `SkillPool.load(String name)` 返回 `Optional<Skill>`；`SkillPool.search(String query, int k)` 返回倒排匹配的 `List<SkillMeta>`（供 Change 2）
- [x] 4.7 `SkillPool.all()` / `allOf(MemoryScope scope)` 返回全部 / 按 scope 的元数据
- [x] 4.8 新增 `SkillPoolTest`：bootstrap 扫两级目录、instance 幂等、load 命中/未命中、search 倒排匹配、allOf scope 过滤

## 5. 接线层：AgentLoopFactory + SkillTool

- [x] 5.1 `AgentLoopFactory.buildTools` 改为用 `SkillPool.instance()` 拿 skill 列表（不再直接调 `discover`）
- [x] 5.2 `SkillTool` 构造改为 `(Skill skill, SkillPool pool)`；提供单参兼容构造（内部自建 mini pool）供既有测试
  > **实施偏差**：实际保持**单参** `SkillTool(Skill)`。懒加载在 `Skill.body()` 本身，`SkillPool`
  > 对 SkillTool 无必要依赖（设计稿双参是为未来检索预留，属 YAGNI）。`SkillPool` 仅作检索入口供 Change 2。
- [x] 5.3 `SkillTool.execute()` 改为调 `pool.load(name).map(Skill::body)`，读盘失败返回错误文本
  > **实施偏差**：改为直接调 `skill.body()`（读盘失败已返回错误文本，见 T2.6），无需经 pool 转发。
- [x] 5.4 确认 `ToolsPlugin`（若存在）也走 `SkillPool`，避免双份扫描
  > **实施结论**：`SkillsPlugin` 存在但**仅测试使用**；生产走 `AgentLoopFactory.buildTools` 直接注册。
  > 两者不共存 → 无双份扫描。`SkillsPlugin` 保留现状（经 `discover` 兼容包装已享懒加载收益）。
- [x] 5.5 跑 `SkillToolTest` + `ToolRegistryTest` 确认全绿

## 6. 验收与门禁

- [x] 6.1 跑 `mvn -o -pl agent-core -am test -Djacoco.skip=true`：全绿，无回归
  > 实际：861 测试，Failures 0，Errors 1（`WorkspaceStoreTest.sameCanonicalPathIsReusedAcrossSymlinkAndDotDot`
  > —— Windows 非管理员无法建 symlink）。已在干净 HEAD 复现 → pre-existing。
- [x] 6.2 跑 `mvn -o -pl agent-core -am verify -Dsurefire.excludes=**/e2e/**`：确认覆盖率门禁无新增缺口
  > 实际：jacoco 报 `com.example.agent.session: branches 0.69 < 0.70`。已在干净 HEAD
  > （@ e7bbe43）复现同样失败 → pre-existing（另一 agent 在 fix-jacoco-rule 处理）。
  > 本 change 未新增未覆盖分支。
- [x] 6.3 确认 `openspec/specs/skills/spec.md` 现有 4 条 Requirement 的行为未受影响（逐条对照）
- [x] 6.4 `git merge main`（门禁 4：与 main 同步）后重跑 6.1
- [x] 6.5 `openspec archive refactor-skill-index-layer` 归档，delta 并入 `openspec/specs/skills/spec.md`
