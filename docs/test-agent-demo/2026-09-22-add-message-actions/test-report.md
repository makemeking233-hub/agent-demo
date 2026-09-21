# 测试报告：add-message-actions（P1 copy + P2 per-message clock）

> 执行日期：2026-09-22
> 用例来源：`test-cases.md`（明细）/ `test-design.md` §5（矩阵）
> 分支：`feat/add-message-actions-p2`（worktree `.worktrees/add-message-actions-p2`，基于 `4c4df4d`）

---

## 1. 执行结果总览

| # | 命令 | 结果 | 关键数字 |
|:-:|------|------|---------|
| 1 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` | ✅ **BUILD SUCCESS** | agent-web `Tests run: 386, Failures: 0, Errors: 0`；jacoco `All coverage checks have been met` |
| 2 | `npx vitest run`（`agent-web/frontend`） | ⚠️ 用例全绿 / 进程退 1 | `Test Files 41 passed`、`Tests 328 passed | 1 skipped`、`Errors 9`（均为既有 unhandled error，见 §4） |
| 3 | `npx tsc --noEmit` | ✅ 未超基线 | **3** 个错误（基线 7） |
| 4 | `openspec validate add-message-actions --type change --strict` | ✅ | `Change 'add-message-actions' is valid` |

定向复跑（用于快速定位）：

```text
mvn -o -q -pl agent-core,agent-web test -Dtest=SessionRecorderTest,SseSessionLogSinkTest \
    -DskipNpm=true -Dsurefire.failIfNoSpecifiedTests=false    → 退出码 0
mvn -o -q -pl agent-web test -Dtest=SessionControllerTest ... → 退出码 0（22 用例）
npx vitest run src/lib/message-clock.test.ts src/components/MessageActionRow.test.tsx \
    src/components/ChatPanel.test.tsx                          → 33 passed
```

---

## 2. 本批次新增/修改用例

| 文件 | 新增 | 说明 |
|------|:----:|------|
| `agent-web/.../stream/SseSessionLogSinkTest.java` | +4 | `message_meta` 顺序、duration、字段、null 派生指标 |
| `agent-core/.../log/SessionRecorderTest.java` | +2 | 落盘读数 / 无 assistant 不写 |
| `agent-web/.../api/SessionControllerTest.java` | +3 | 历史回填读数 / uuid 找不到 / 老会话 |
| `agent-web/frontend/src/lib/message-clock.test.ts` | +18 | 新建 |
| `agent-web/frontend/src/components/MessageActionRow.test.tsx` | +4 | clock 渲染（P1 的 1 条顺序用例同步改为 meta 驱动） |
| `agent-web/frontend/src/components/ChatPanel.test.tsx` | +5 | 时间线装配 + 历史透传 |

合计新增 **36** 条；套件规模 306 → 328 passed（41 文件）。

---

## 3. 逐用例结果

`test-cases.md` 中 T-01 ~ T-17 全部 **通过**；T-18（tsc）通过（3 ≤ 7）。无失败、无跳过（vitest 的 1 条 skip 为既有）。

---

## 4. 环境适配与既有失败归因

### 4.1 vitest `Errors 9` + 进程退出码 1（**既有，与本 change 无关**）

现象：`ReferenceError: EventSource is not defined`（`src/lib/settings-sse.ts:28` → `useSettingsStore`），
出现在 `SettingsModal.test.tsx` / `ChatPanel.test.tsx` 等文件的 unhandled rejection 中，导致 vitest 进程退 1。

归因证据（门禁 5 要求「能在合并前的干净 HEAD 复现」）：

```text
# 干净 HEAD 4c4df4d（stash 掉本 change 全部改动后）
Test Files  40 passed (40)
     Tests  306 passed | 1 skipped (307)
    Errors  9 errors
[exit code: 1]

# 本 change 分支
Test Files  41 passed (41)
     Tests  328 passed | 1 skipped (329)
    Errors  9 errors
[exit code: 1]
```

两侧 `Errors 9` **逐位一致** → 既有问题，记录放行。根因是 jsdom 无 `EventSource`，属既有测试环境缺口
（建议后续单独 change 在 setup 里补 `EventSource` 桩）。

### 4.2 tsc 基线

实跑 3 条：`MessageActionRow.tsx(54,7)`（`setTimeout` 返回 `number` vs `Timeout`，P1 引入）、
`Sidebar.tsx(454,11)`、`vite.config.ts(128,3)`。三者在本 change 干净 HEAD 上同样出现（stash 前后一致），
且总数 3 ≤ 基线 7。AGENTS.md §2.7.7 记的基线 7 为旧值（已注明待更新）。

### 4.3 npm / node_modules 事故（**非测试失败，但影响可复现性**）

第一次 `mvn test` 未加 `-DskipNpm=true`，frontend-maven-plugin 触发 `npm ci`，因
`node_modules/@tailwindcss/oxide-win32-x64-msvc/*.node` 被占用而 `EPERM/-4048` 失败，并**部分删除了
`node_modules`**（`vite` 等包丢失），导致随后 vitest 无法启动。
处置：改用 `npm install --no-audit --no-fund` 就地修复（658 个包补齐），vitest 恢复。
**结论**：在本仓库跑 Maven 必须带 `-DskipNpm=true`（本项目门禁命令已如此）。

---

## 5. 覆盖率

`mvn verify` 输出 `All coverage checks have been met`（LINE ≥ 80% / BRANCH ≥ 70%）；本 change 的
`agent-web` 386 用例内含新增的 9 条 SSE / REST 用例，未出现覆盖率回退。

---

## 6. 缺陷清单

| # | 严重度 | 现象 | 处置 |
|:-:|:------:|------|------|
| D-1 | 🟡 | `SessionEntry.assistant(content, null, parent)` 在 `toolCalls == null` 时因 `Map.of("toolCalls", null)` 抛 NPE，被 `SessionRecorder.safeStore` 静默吞掉（该条 assistant 不落盘） | **未修**（超出本 change 范围）；实施期由新增单测踩到，测试内改用 `List.of()` 规避；已写入 `openspec/changes/add-message-actions/tasks.md` 的 Follow-up 与归档记录，建议后续单独 change 补防御 + 回归用例 |
| D-2 | 🟢 | vitest 进程因既有 `EventSource is not defined` 退出码 1 | 既有；已在干净 HEAD 复现并记录（§4.1） |

无 🔴 缺陷。

---

## 7. 测试数据隔离审计（全局规则 §10）

| 项 | 结论 |
|----|------|
| 落盘用例写在哪 | 全部使用 JUnit `@TempDir`（`SessionRecorderTest` 2 条、`SessionControllerTest` 新增 3 条），JUnit 跑完自动删除临时目录 |
| 是否触碰真实 `~/.agent-demo/` | **未触碰**。`SessionRecorderTest` 自建 `SessionStore`（显式传 `@TempDir` 路径）；`SessionControllerTest` 的 `WebAgentRuntime` 用 `new WebAgentRuntime(..., tmp, cfgNoLogging())` 注入临时数据目录（既有惯例） |
| 是否触碰真实 sessions/feedback 目录 | 未触碰；本批次未引入 feedback sidecar（属 `add-message-feedback`） |
| 需要删除的测试产物 | **无**。前端 vitest 只跑 jsdom + mock fetch（`vi.stubGlobal("fetch", ...)`），不发真实请求、不写盘 |
| 保留但可疑的清单 | **无** |
