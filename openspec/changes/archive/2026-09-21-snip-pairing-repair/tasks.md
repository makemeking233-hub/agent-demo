# snip-pairing-repair — 任务清单

- [x] T1.1 测试先红：`SessionResumeLoaderTest` 新增「裁剪点落在 `assistant(tool_calls=[c1,c2])` 与其结果之间 → 裁剪后不存在无前置 `tool_calls` 的 `tool_result`」，同时补「未超限不裁剪」「对齐后仍 ≤ 上限」「头部 `[RESUMED]`」三个边界用例。
  - 实测红：`Tests run: 13, Failures: 1` → `snipDoesNotSplitToolCallGroup` 报 `expected: <false> but was: <true>`（裁剪制造了孤儿）；其余 3 个边界用例改动前即通过（它们描述的是既有正确行为）。
- [x] T1.2 实现 ①：`SessionResumeLoader.snip` 裁剪点按配对组对齐（`drop` 跳过前导 `ToolResult`）。
- [x] T1.3 T1.1 转绿（`Tests run: 13, Failures: 0`）；commit + push。commit `2d4153e`。
- [x] T2.1 测试先红：`ToolCallPairingTest` 新增 7 例——单孤儿注入、连续多孤儿合并为一个合成 assistant、有前置的不动、已干净历史逐元素不变、幂等、不改入参、与 `repair` 组合后双向满足。
  - 实测红：`test-compile` 报「找不到符号 repairOrphanResults」（方法尚不存在）。
- [x] T2.2 实现 ②a：`ToolCallPairing.repairOrphanResults` 纯函数 + `ORPHAN_CALL_NAME` 常量 + `hasMatchingCall` 私有辅助；`SessionResumeLoader.injectOrphanSkeletons` 删除并改调该函数。
- [x] T2.3 T2.1 转绿（`ToolCallPairingTest` 15/15），既有 `injectsOrphanSkeletonForOrphanToolResult` 回归通过（`SessionResumeLoaderTest` 13/13）。
- [x] T3.1 测试先红：`AgentLoopToolPairingTest` 新增 `requestRepairsBothPairingDirectionsOnDirtyHistory`——内存历史同时含悬挂 `tool_calls`(c9) 与孤儿 `tool_result`(c0)，捕获真实发出的 `ChatRequest.messages()`。
  - 实测红：`Tests run: 2, Failures: 1` → 反向孤儿断言 `expected: <false> but was: <true>`。
- [x] T3.2 实现 ②b：`AgentLoop.toRequest` 改为 `repairOrphanResults(repair(history.all()))`。
- [x] T3.3 T3.1 转绿（`Tests run: 2, Failures: 0`）；commit + push。commit `a94ad13`。
- [x] T4.1 门禁 1（分支）：`mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` → **BUILD SUCCESS**，agent-core `545/0`、agent-web `379/0`，jacoco check 通过（零违规）；前端 `npx vitest run` → `40 files / 302 tests` 全过；`npx tsc --noEmit` → 6 个错误（≤ 基线 7）。
- [x] T4.2 与 `main` 同步（`git merge main`，快进至 `8b60312`，无冲突）后重跑门禁 1 → **BUILD SUCCESS**，agent-core `545/0`、agent-web `379/0`，日志 `[ERROR]` 行数 0。同步后 `main` 的前端已被并行 agent 重构（shadcn/Tailwind），故重拷一次 `static/` 再跑。
- [x] T5.1 `openspec archive snip-pairing-repair --yes` → 归档为 `2026-09-21-snip-pairing-repair`；delta 已并入 `openspec/specs/testability/spec.md`（`+ 1 added, ~ 1 modified`），已核验新需求 `裁剪不破坏配对不变式` 出现在主 spec 中。
- [x] T5.2 合并回 `main` → `main` 上复验门禁 1 → push → 清理 worktree / 分支。
- [x] T5.3 补测试文档四件套 `docs/test-agent-demo/2026-09-22-snip-pairing-repair/` + `test-guide.md` §1 登记行与 §2.20 详情小节（§2.6）。
- [x] T5.4 §10 清理：本批新增用例全部在内存构造、无 IO；探针文件在 `%TEMP%`（上一轮产物）；`gate*.log` 属 worktree 构建期诊断日志，随 worktree 删除；用户真实会话仅只读读取。详见 `test-report.md` §10。

## failure attribution

| 现象 | 归属 | 依据 |
|------|------|------|
| 前端首轮 `_axe-debug.test.tsx` 套件失败 | **非本 change**（并行 agent 的未入库临时文件） | `git ls-files` 无此文件；单跑该路径报 `No test files found`（已在两次运行之间被删除）；本分支 `git diff main...HEAD -- agent-web/frontend` 为空 |
| 前端 `9 unhandled errors`（EventSource）导致 vitest 退出码 1 | **既有问题** | 本分支零前端改动；`Tests 302 passed` 全过，仅未处理错误计数 |
