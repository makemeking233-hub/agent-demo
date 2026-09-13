# Tasks: web_search 阻塞调用修复（fix-web-search-blocking-call）

## 1. WebSearchTool 加 subscribeOn 调度隔离

- [ ] 1.1 在 `WebSearchTool.java` 顶部新增 import `reactor.core.scheduler.Schedulers`
- [ ] 1.2 `WebSearchTool.execute()` 末尾的 `Mono.fromCallable(() -> { ... })` 链上追加 `.subscribeOn(Schedulers.boundedElastic())`

## 2. 错误消息透传（去掉误导性 fallback 文案）

- [ ] 2.1 `WebSearchTool.execute()` catch 块：移除 `+ "（请检查搜索 provider 的 API key 配置与网络连接）"` 拼接，保留 `e.getMessage()`

## 3. 新增测试

- [ ] 3.1 `WebSearchToolTest`：新增用例 "在 reactor 调度器上执行不触发 block() 抛错" —— mock provider 让 `search()` 阻塞 50ms；用 `StepVerifier.create(execute(...)).assertNext(...).verifyComplete()` 验证正常完成
- [ ] 3.2 `WebSearchToolTest`：新增用例 "provider 抛错时错误文本包含原始 e.getMessage()" —— mock provider 抛 `IllegalStateException("DeepSeek 搜索缺少 API key：请设置环境变量 DEEPSEEK_API_KEY")`；验证返回结果 `isError=true` 且文本含原消息
- [ ] 3.3 跑 `WebSearchToolTest` 全部用例全绿（新增 + 现有）

## 4. 全量测试 + 验证

- [ ] 4.1 跑 `mvn -o -pl agent-core test -Dtest='WebSearchToolTest,DeepSeekWebSearchProviderTest,TavilyWebSearchProviderTest,WebSearchProviderFactoryTest,WebSearchProviderTest'` 全绿
- [ ] 4.2 跑 `mvn -o -pl agent-core test` 全量全绿（防止回归）
- [ ] 4.3 `npx tsc --noEmit`（如果有前端）错误数不超过基线（与 §2.7.5.1 门禁 1 一致）

## 5. 提交与归档

- [ ] 5.1 `openspec validate fix-web-search-blocking-call --type change --strict` 通过
- [ ] 5.2 `openspec archive fix-web-search-blocking-call --yes`（delta spec 合并到 `openspec/specs/search/spec.md`）
- [ ] 5.3 git add 显式路径（agent-core/src/main/.../WebSearchTool.java + agent-core/src/test/.../WebSearchToolTest.java + openspec/）→ 中文 Conventional Commits commit（如 `fix(web-search): 调度隔离避免 event loop 阻塞 + 错误消息透传`）
- [ ] 5.4 `git push -u origin fix/web-search-blocking-call`

## 6. 与 main 同步 + 重跑门禁（§2.7.5.1 门禁 4）

- [ ] 6.1 在 worktree 分支上 `git fetch origin main && git merge origin/main`（或 rebase）
- [ ] 6.2 解决任何冲突（**仅限本 change 涉及的 2 个文件**；其他文件冲突停下问用户）
- [ ] 6.3 重跑 §4.1 / §4.2 / §4.3 全绿

## 7. 合并回 main（§2.7.5.2）

- [ ] 7.1 切到主工作区 `cd E:/claude-projects/agent-demo`
- [ ] 7.2 `git status -sb` 确认 main 工作区干净；若有他人未提交改动先问用户
- [ ] 7.3 记录当前 HEAD：`git rev-parse HEAD`
- [ ] 7.4 `git merge fix/web-search-blocking-call`（能快进就快进）
- [ ] 7.5 在 main 上跑 §4.1 / §4.2 / §4.3 全绿（**复验**）
- [ ] 7.6 复验通过才 `git push origin main`

## 8. 清理（§2.7.5.3 复验通过后立刻做）

- [ ] 8.1 `git worktree remove .worktrees/fix-web-search-blocking-call`
- [ ] 8.2 `git branch -d fix/web-search-blocking-call`
- [ ] 8.3 `git push origin --delete fix/web-search-blocking-call`（若已 push）
