# 测试报告：add-message-feedback（👍/👎 + sidecar + per-item CAS）

> 执行日期：2026-09-22
> 用例来源：`test-cases.md`（明细）/ `test-design.md` §5（矩阵）
> 分支：`feat/add-message-feedback`（worktree `.worktrees/add-message-feedback`，基于 `9ad08c1`）

---

## 1. 执行结果总览

| # | 命令 | 结果 | 关键数字 |
|:-:|------|------|---------|
| 1 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dspring-boot.repackage.skip=true -Dsurefire.excludes=**/e2e/**` | ✅ **BUILD SUCCESS** | agent-web `Tests run: 399, Failures: 0, Errors: 0`；jacoco `All coverage checks have been met` |
| 2 | `npx vitest run`（`agent-web/frontend`） | ⚠️ 用例全绿 / 进程退 1 | `Test Files 42 passed`、`Tests 356 passed | 1 skipped`、`Errors 9`（既有，见 §4） |
| 3 | `npx tsc --noEmit` | ✅ 未超基线 | **3** 个错误 |
| 4 | `openspec validate add-message-feedback --type change --strict` | ✅ | `Change 'add-message-feedback' is valid` |

定向复跑（用于快速定位）：

```text
mvn -o -q -pl agent-core test -Dtest=MessageFeedbackStoreTest -Dsurefire.failIfNoSpecifiedTests=false   → 退出码 0（13 用例）
mvn -o -q -pl agent-web test -Dtest=FeedbackControllerTest -DskipNpm=true ...                          → 退出码 0（13 用例）
npx vitest run src/api/feedback.test.ts src/components/MessageActionRow.test.tsx                        → 32 passed
npx vitest run src/components/ChatPanel.test.tsx                                                        → 16 passed
```

---

## 2. 本批次新增用例

| 文件 | 新增 | 说明 |
|------|:----:|------|
| `agent-core/.../session/MessageFeedbackStoreTest.java` | +13 | CAS 全路径 / 并发两式 / 非法 id / 跨 session 隔离 |
| `agent-web/.../api/FeedbackControllerTest.java` | +13 | 3 端点 happy + 400/404/409 + 快照排序 |
| `agent-web/frontend/src/api/feedback.test.ts` | +14 | `nextRating` 四态 + 3 个 fetch 的错误码映射 + URL 编码 |
| `agent-web/frontend/src/components/MessageActionRow.test.tsx` | +7 | P3 赞踩渲染与回调（P1/P2 用例保持全绿） |
| `agent-web/frontend/src/components/ChatPanel.test.tsx` | +7 | 首屏拉取 / 乐观更新 / 回滚 / 409 调和 |

合计新增 **54** 条；前端套件 328 → 356 passed（41 → 42 文件），后端 agent-web 386 → 399。

---

## 3. 逐用例结果

`test-cases.md` 中 T-01 ~ T-52 全部 **通过**；`test-design.md` §5 的 T-45（tsc）通过（3 ≤ 基线）、
T-46（隔离审计）通过（见 §6）。无失败、无跳过（vitest 的 1 条 skip 为既有）。

---

## 4. 环境适配与既有失败归因

### 4.1 vitest `Errors 9` + 进程退出码 1（**既有**）

`ReferenceError: EventSource is not defined`（`src/lib/settings-sse.ts:28` → `useSettingsStore`），
jsdom 无 `EventSource` 导致 unhandled rejection，使 vitest 进程退 1。

归因：同一现象在上一个 change（`add-message-actions`）已用 `git stash` 回干净 HEAD `4c4df4d` 复现出
**逐位一致的 9 条**并记录放行；本 change 的 `9 errors` 与之数量一致，且本 change 未触碰
`settings-sse.ts` / `useSettingsStore.ts`。→ 既有问题，记录放行。

### 4.2 tsc 基线

实跑 3 条：`MessageActionRow.tsx(54,7)`（P1 引入的 `setTimeout` 返回类型）、`Sidebar.tsx(454,11)`、
`vite.config.ts(128,3)`——与改动前**逐条相同**，总数 3 ≤ 基线 7。AGENTS.md §2.7.7 记的 7 是旧值。

### 4.3 Maven 侧的两个环境坑（**非测试失败**）

| 坑 | 现象 | 处置 |
|----|------|------|
| 未加 `-DskipNpm=true` | frontend-maven-plugin 触发 `npm ci`，原生模块 `.node` 被占用 → `EPERM -4048`，npm 先删后装把 `vendor` 包删掉，vitest 起不来 | 用 `npm install --no-audit --no-fund` 就地修复；门禁命令固定带 `-DskipNpm=true` |
| 主工作区有 `java -jar agent-web/target/agent-web.jar` 在跑 | `spring-boot:repackage` 重命名 jar 失败 → BUILD FAILURE（**测试阶段已通过**） | 加 `-Dspring-boot.repackage.skip=true`；本次 worktree 内构建不受影响 |

---

## 5. 覆盖率

`mvn verify` 输出 `All coverage checks have been met`；本 change 新增
`MessageFeedbackStore`（13 用例）与 `FeedbackController`（13 用例），未出现覆盖率回退。

---

## 6. 测试数据隔离审计（全局规则 §10）

| 项 | 结论 |
|----|------|
| 落盘用例写在哪 | 后端全部 `@TempDir`（`MessageFeedbackStoreTest` 13 条、`FeedbackControllerTest` 13 条）；前端全部 `vi.stubGlobal("fetch", ...)`，不发真实请求 |
| 真实 `~/.agent-demo/feedback/` | **不存在**（`Test-Path` = False）→ 零写入 |
| 真实 `~/.agent-demo/sessions/` | 52 个文件，跑前跑后未变（本 change 的测试不写真实 sessions） |
| 构建期产生的 feedback 目录 | 仅 `agent-web/target/test-data/.agent-demo/feedback` 与 `agent-web/target/test-data-model-http/.agent-demo/feedback` 两个**空目录**（`WebRuntimeConfig` 的 bean 构造时 `ensureDir()`），位于 gitignored 的 `target/` 内，`mvn clean` 即清 |
| 需要删除的测试产物 | **无** |
| 保留但可疑的清单 | **无** |

---

## 7. 缺陷清单

| # | 严重度 | 现象 | 处置 |
|:-:|:------:|------|------|
| D-1 | 🟢 | `FeedbackController` 首次实现用 `Map.of("current", null)` 返回 409 body，`Map.of` 不接受 null → NPE（测试 T-24 捕获） | **已修**：抽出 `currentAsMap()` + `Collections.singletonMap`，null 安全 |
| D-2 | 🟡 | `ChatPanel` 的会话切换 effect 用 `lastSessionIdRef` 做「首次挂载不入内」短路：挂载时若 `currentSessionId` prop 已非空且 localStorage 无快照，历史与反馈都不加载 | **未修**（超出本 change 范围）：真实流程（reload 有 localStorage / 侧边栏点击）不受影响；已记入 `tasks.md` §实施期发现 |
| D-3 | 🟢 | vitest 进程因既有 `EventSource is not defined` 退出码 1 | 既有；已归因（§4.1） |

无 🔴 缺陷。
