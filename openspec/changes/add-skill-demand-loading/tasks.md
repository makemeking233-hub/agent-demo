# add-skill-demand-loading — Tasks

> 前置：`refactor-skill-index-layer` 已合入 main（提供 `SkillPool` / `SkillMeta`）。
> 本 change 改写 skill 暴露方式（写 delta spec），默认档从「全暴露」切到「search」。

## 1. 配置层：AgentConfig.Skill

- [x] 1.1 `AgentConfig` 新增 `record Skill(String mode, int hybridDirectLimit, int hybridProjectQuota, int recallK, double decayLambda, int descriptionTruncateChars)` + `Mode` 枚举（ALL/SEARCH/HYBRID）
- [x] 1.2 `ConfigLoader` 解析 `agent.skill.*` 六个键（缺失走默认：search/10/6/20/0.05/40）
- [x] 1.3 非法值回退：`mode` 非枚举 → `search` + WARN；数值 ≤0 → 默认值 + WARN；不抛异常
- [x] 1.4 `AgentConfig.defaults()` 加 `new Skill(...)` 默认实例
- [x] 1.5 新增 `AgentConfigSkillTest`：六键解析、缺省、非法 mode 回退、非法数值回退

## 2. 频次持久化：SkillUsageStore

- [x] 2.1 新增 `SkillUsage` record（`count` / `lastUsedMs`）与 `SkillUsageStore`（`~/.agent-demo/skill-usage.json`）
- [x] 2.2 内存累积 + `record(name)` 接口；turn 结束 / 每 10 次调用 flush 一次
- [x] 2.3 `flush()` 原子写（tmp + `ATOMIC_MOVE`，与 `SettingsService.writeAtomic` 同构）
- [x] 2.4 `load()` 缺失文件视为空 map；JSON 损坏记 WARN 并从空开始（自愈）
- [x] 2.5 `score(name, lambda, now)` = `count × exp(-lambda × daysSince(lastUsed))`
- [x] 2.6 新增 `SkillUsageStoreTest`：记录累积、原子写、文件缺失、JSON 损坏自愈、得分衰减计算

## 3. 检索层：倒排预筛 + embedding KNN

- [x] 3.1 `SkillPool.search(query, k)` 接入：`InvertedIndex.lookupAll(tokenize(query))` 得候选集（10000 → ~200）
- [x] 3.2 候选集非空且 `EmbeddingProvider.isReady()` → `LuceneVectorIndex.knn(queryVec, candidates, k)`
- [x] 3.3 降级路径：`isReady()=false` 或候选集为空 → 纯倒排结果按 name 排序截断 k；**候选集为空时不回退全量 KNN**（避免噪声）
- [x] 3.4 `SkillPool` 接入 embedding 索引构建：`LuceneVectorIndex` per-scope 实例 + `.vectors/mtime.json`（复用 `VectorIndexStore` 模式）
- [x] 3.5 embedding 索引构建放后台线程，不阻塞 `bootstrap`
- [x] 3.6 新增 `SkillPoolSearchTest`：倒排命中、KNN 精排、`isReady()=false` 降级、候选集空不回退全量

## 4. 热门选择：HotSkillSelector

- [x] 4.1 新增 `HotSkillSelector.pick(limit, projectQuota)`：PROJECT scope 先占 `projectQuota` 名额
- [x] 4.2 剩余名额按 `SkillUsageStore.score` 降序填充用户级 skill
- [x] 4.3 合并「会话内最近调用过」（滑动窗口，默认 2 个名额）与前两步结果去重
- [x] 4.4 整体去重后截断 `limit`（默认 10）
- [x] 4.5 PROJECT scope 不足配额时用频次补位（不浪费名额）
- [x] 4.6 新增 `HotSkillSelectorTest`：PROJECT 优先、不足补位、频次排序、去重截断、全空返回空

## 5. skill_search 工具

- [x] 5.1 新增 `SkillSearchTool implements Tool<String, String>`：`name()="skill_search"`
- [x] 5.2 `description()` 说明「按关键词检索可用技能，返回匹配技能名与描述；找到后用同名工具加载完整指令；可多次调用以精化检索」
- [x] 5.3 `inputSchema()` = `{query: string}`（required）
- [x] 5.4 `execute(query)` 调 `pool.search(query, recallK)`，返回「name: description」每行一条；空结果返回空列表文本
- [x] 5.5 `isReadOnly()`=true、`category()`=READ、`checkPermissions()`=allow（与 SkillTool 对齐）
- [x] 5.6 检索异常 catch → 返回空列表文本，不抛
- [x] 5.7 新增 `SkillSearchToolTest`：工具名、schema 必填、检索命中、无命中空、异常降级

## 6. 接线层：SkillsPlugin + AgentLoop

- [x] 6.1 `SkillsPlugin.tools()` 按 mode 三分支：`ALL` 全量 SkillTool / `SEARCH` 仅 SkillSearchTool / `HYBRID` 热门 SkillTool + SkillSearchTool
- [x] 6.2 `SkillsPlugin.fragment()` 改为静态说明（三 scope 目录位置 + 工具暴露 + search 用法）；无 skill 时返回空串
- [x] 6.3 `AgentLoop` 注入 `AgentConfig.Skill` + `SkillPool` + `SkillUsageStore`
- [x] 6.4 `AgentLoop.toRequest()`：`SKILL_HYBRID` 档下把「会话内最近调用」动态追加进 tools（启动期 tools 之外）
- [x] 6.5 `AgentLoop.resolveSystemPrompt()` 追加「可能相关的技能」小节：候选来自 `pool.search(query, recallK)`，格式 `- name: desc(截断 N 字)`
- [x] 6.6 轮内缓存候选（`cachedQuery` + `cachedCandidates`，与 `memorySectionFor` 同构）
- [x] 6.7 提问为空 / 无候选时不注入小节
- [x] 6.8 `SkillTool.execute()` 成功时调 `usageStore.record(name)`；`SkillSearchTool.execute()` 命中时也记
- [x] 6.9 新增 `SkillModeWiringTest`：三档各自 tools 形状（数量 + 是否含 search）、候选名单渲染、提问为空不注入、轮内缓存生效

## 7. 频次写入时机

- [x] 7.1 `AgentLoop` turn 结束时调 `usageStore.flush()`（覆盖延迟写）
- [x] 7.2 `SkillUsageStore` 每 10 次 `record` 自动 flush（计数触发）
- [x] 7.3 `SkillUsageStore implements AutoCloseable`，`@PreDestroy` flush 兜底

## 8. 验收与门禁

- [x] 8.1 跑 `mvn -o -pl agent-core -am test -Djacoco.skip=true`：全绿无回归
- [x] 8.2 新增端到端断言：1000 个 skill 时 `ChatRequest.tools.size()` 不超过「基础 + 10 + 1」
- [x] 8.3 跑 `mvn -o -pl agent-core -am verify -Dsurefire.excludes=**/e2e/**`：覆盖率门禁无新增缺口
- [x] 8.4 补 `docs/design/design.md` 的 skill 章节（按需暴露 + 三档模式 + 检索链路）
- [x] 8.5 `git merge main`（门禁 4）后重跑 8.1
- [x] 8.6 `openspec archive add-skill-demand-loading`，delta 并入 `openspec/specs/skills/spec.md`
