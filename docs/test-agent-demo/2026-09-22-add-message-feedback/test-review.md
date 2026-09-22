# 测试复盘：add-message-feedback（👍/👎 + sidecar + per-item CAS）

> 批次：`docs/test-agent-demo/2026-09-22-add-message-feedback/`
> 复盘时间：2026-09-22（测试执行当日）
> 四件套：`test-design.md` / `test-cases.md` / `test-report.md` / 本文件

---

## 1. 流程回顾

| 阶段 | 动作 | 耗时（估） |
|------|------|:---------:|
| 侦察 | 读 `SessionStore`（文件权限/写入模式）、`AgentPaths`（路径单一入口）、`WorkspaceController`（控制器风格）、`SessionControllerTest`（测试惯例） | ~20min |
| F1 存储 | `AgentPaths.feedbackDir()` + `MessageFeedbackStore`（sidecar / 锁 / CAS）+ 13 用例 | ~50min |
| F2 REST | 4 个 DTO + `FeedbackController` + bean 装配 + 13 用例 | ~40min |
| F3 前端 | `api/feedback.ts` + `MessageActionRow` 按钮 + `MessageBubble` 透传 + `ChatPanel` 接线 + 28 用例 | ~55min |
| 门禁 | mvn verify / vitest / tsc + 隔离审计 | ~25min |
| 文档 | tasks 勾选 + 四件套 + test-guide 登记 | ~30min |

---

## 2. 做得好的

1. **CAS 用「纯函数 + 显式异常」表达**：`casCheck(existing, ifVersion)` 把 `null`→「必须不存在」与
   `N`→「必须相等」两种情况收在一处，409 的 `current` 直接由异常携带，控制器只做映射——语义不易漂移。
2. **并发测试测「输家」而不是只测赢家**：T-11 断言「1 个成功 + 3 个 409 + 终态 version=1」，
   比只断言「最终有一个值」强得多。第一版写成 `assertThat(failure).isNull()` 是错的（把**预期的**
   409 当成失败），跑出来才发现并改正。
3. **前端 409/500 用真实交互验证而非只测函数**：`ChatPanel` 组用路由式 fetch 桩点真按钮，
   断言 `aria-pressed` 的最终状态——`handleRate` 的回滚/调和分支都真的被执行到。
4. **隔离审计给出可复核证据**：不是「应该没污染」，而是 `Test-Path ~/.agent-demo/feedback` = False
   + 列出唯二产生的目录（`target/` 下空目录），并说明判据。

## 3. 问题与根因

| # | 问题 | 根因 | 修复 |
|:-:|------|------|------|
| P-1 | 409 body 拼装抛 NPE | `Map.of` 不接受 null value，而「对方已删除」场景 `current` 就是 null | 抽 `currentAsMap()`，用 `Collections.singletonMap`（允许 null） |
| P-2 | 7 个 `ChatPanel` 用例全挂在「找不到 `msg-up`」 | 测试直接在挂载时传 `currentSessionId="s-1"`，而 ChatPanel 的切换 effect 用 `lastSessionIdRef` 短路了「首次挂载 → 同一值」的情形，于是 `sessionIdRef` 从未被赋值 → `ratingFor()` 恒返回 `undefined` → 按钮不渲染 | 测试改为「先 null 挂载 → rerender 成 s-1」，与真实侧边栏点击一致；**产品侧的行为未改**（真实流程不受影响），作为发现 D-2 记录 |
| P-3 | agent-web 编译找不到 `MessageFeedbackStore`（新类在 agent-core） | 未先 `mvn -pl agent-core install`，agent-web 从本地 m2 拿的是旧 jar | 先 `install` agent-core 再跑 agent-web 用例 |
| P-4 | 并发用例误判 | 把「除赢家外的线程都该 409」写成了「任何异常都是失败」 | 分开捕获：`MessageFeedbackVersionConflict` 计入 `conflicts`，其余进 `unexpected`，并断言 `conflicts == threads-1` |
| P-5 | `mvn test` 未加 `-DskipNpm` 删坏 `node_modules` | frontend-maven-plugin 默认 `npm ci`，原生 `.node` 被占用 → `EPERM -4048`，npm 先删后装 | 门禁命令固定带 `-DskipNpm=true`；已坏目录用 `npm install` 修复 |

## 4. 可改进

1. **前端接线用例的「真实入口」还不够真**：目前靠 `rerender` 模拟侧边栏点击；若能在 E2E 里真点
   侧边栏再点赞，覆盖会更完整（列入下次 Web E2E 批次）。
2. **跨进程并发未覆盖**：T-11 覆盖同 JVM 多线程；两个 JVM（或两个浏览器进程）同时写同一 sidecar
   依赖 OS 级文件锁，逻辑上成立但无自动化证据。若要证，需要跨进程测试脚手架，成本高于收益，暂缓。
3. **`MessageFeedbackStore` 的锁退避是固定预算**：20 次 × 最多 125ms ≈ 2.5s 上限，超时抛
   `RuntimeException`。高竞争场景下会变成 500；当前使用形态（单人单机多标签页）足够，后续若上多人
   协作需改为带超时参数的可配置值。
4. **`ChatPanel` 的「首次挂载短路」（D-2）值得单独修**：它影响的不只是反馈——挂载即带 sessionId
   时历史也不加载。建议开一个小 bugfix change，把「统一从 currentSessionId 派生一次加载」理顺。

## 5. 交付物

| 类型 | 路径 |
|------|------|
| 存储 | `agent-core/.../session/MessageFeedbackStore.java`（新）、`agent-core/.../config/AgentPaths.java`（+`feedbackDir()`） |
| REST | `agent-web/.../api/FeedbackController.java`（新）+ `dto/Feedback{PutRequest,DeleteRequest,ItemDto,Response}.java`（新） |
| 装配 | `agent-web/.../config/WebRuntimeConfig.java`（+`webMessageFeedbackStore` bean） |
| 前端 API | `agent-web/frontend/src/api/feedback.ts`（新） |
| 前端组件 | `MessageActionRow.tsx`（+👍/👎）、`MessageBubble.tsx`（透传）、`ChatPanel.tsx`（拉取/乐观更新/调和） |
| 测试（后端） | `MessageFeedbackStoreTest`（13）、`FeedbackControllerTest`（13） |
| 测试（前端） | `feedback.test.ts`（14）、`MessageActionRow.test.tsx`（+7）、`ChatPanel.test.tsx`（+7） |
| OpenSpec | `add-message-feedback`（proposal / specs / tasks） |
| 四件套 | 本目录 |
