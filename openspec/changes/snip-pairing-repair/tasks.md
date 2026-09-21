# snip-pairing-repair — 任务清单

- [ ] T1.1 测试先红：`SessionResumeLoaderTest` 新增「裁剪点落在 `assistant(tool_calls=[c1,c2])` 与其结果之间 → 裁剪后不存在无前置 `tool_calls` 的 `tool_result`」，同时补「未超限不裁剪」「对齐后仍 ≤ 上限」「头部 `[RESUMED]`」三个边界用例。
- [ ] T1.2 实现 ①：`SessionResumeLoader.snip` 裁剪点按配对组对齐（`drop` 跳过前导 `ToolResult`）。
- [ ] T1.3 T1.1 转绿；`git add` 显式路径 + commit + push（`fix(resume): snip 裁剪点按配对组对齐，不再切断 tool_calls 与其结果`）。
- [ ] T2.1 测试先红：新增 `ToolCallPairingOrphanTest`——单孤儿注入、连续多孤儿合并为一个合成 assistant、有前置的不动、幂等、已干净历史逐元素不变。
- [ ] T2.2 实现 ②a：`ToolCallPairing.repairOrphanResults` 纯函数；`SessionResumeLoader.injectOrphanSkeletons` 删除并改调该函数。
- [ ] T2.3 T2.1 转绿 + 既有 `injectsOrphanSkeletonForOrphanToolResult` 回归通过；commit + push。
- [ ] T3.1 测试先红：`AgentLoop` 请求路径测试——内存历史同时含悬挂 `tool_calls` 与孤儿 `tool_result`，断言 `toRequest` 产出的消息列表双向配对都满足；连续两次请求长度一致；内存历史条数不变。
- [ ] T3.2 实现 ②b：`AgentLoop.toRequest` 改为 `repairOrphanResults(repair(history.all()))`。
- [ ] T3.3 T3.1 转绿；commit + push。
- [ ] T4.1 门禁 1：`mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` 全绿（零 jacoco 违规）；前端 `npx vitest run` 全绿；`npx tsc --noEmit` 错误数 ≤ 基线。
- [ ] T4.2 与 `main` 同步（`git merge main`）后重跑门禁 1。
- [ ] T5.1 `openspec archive snip-pairing-repair --yes`；确认 delta 已并入 `openspec/specs/testability/spec.md`。
- [ ] T5.2 合并回 `main` → `main` 上复验门禁 1 → push → 清理 worktree / 分支。
- [ ] T5.3 补测试文档四件套 `docs/test-agent-demo/<date>-snip-pairing-repair/` + `test-guide.md` 登记（§2.6）。
- [ ] T5.4 §10 清理本次探针/临时产物，并说明删除依据与保留项。
