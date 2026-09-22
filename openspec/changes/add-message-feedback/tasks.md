# Tasks: add-message-feedback

> 来源：`add-message-actions` 的 P3 拆分（P1/P2 已归档）。
> 前置：`SessionMessageDto.uuid` 与前端 `Item.uuid` 已在 P2 落地，反馈按钮直接用它们做 `messageId`。

## F1: sidecar 存储（agent-core，~1h）

- [x] 1.1 新增 `agent-core/src/main/java/com/example/agent/session/MessageFeedbackStore.java`
      （构造接 `Path feedbackDir`；`getAll/get/put/delete`）
- [x] 1.2 sidecar schema v1：`{version:1, session_id, items:{<uuid>:{rating, version, updated_at}}}`；
      文件 0600、目录 0700（POSIX；Windows 跳过）
- [x] 1.3 写操作加文件锁（`<sessionId>.lock`，`FileChannel.tryLock` + 超时退避），读操作不加锁
- [x] 1.4 CAS：`ifVersion == null` = 必须不存在；`ifVersion == N` = 必须等于当前 version；
      冲突抛 `MessageFeedbackVersionConflict`（携带 `current`，可能为 null）
- [x] 1.5 `MessageFeedbackStoreTest`（`@TempDir`，**不得写真实 `~/.agent-demo`**）：13 用例全绿
      （创建 / 重复 CAS / 匹配 ifVersion / 不匹配 ifVersion / 删除 / 不存在删 /
      排序 / 非法 id / 非法 rating / 空 session / 并发同 message / 并发不同 message /
      跨 session 隔离）

## F2: REST 端点（agent-web，~45min）

- [x] 2.1 新增 `agent-web/src/main/java/com/example/agent/web/api/FeedbackController.java`
      （`@Profile("web")`）：`GET /api/feedback/{sessionId}`、`PUT /api/feedback/{sessionId}/{messageId}`、
      `DELETE /api/feedback/{sessionId}/{messageId}`
- [x] 2.2 DTO：`FeedbackPutRequest {rating, ifVersion}` / `FeedbackDeleteRequest {ifVersion}` /
      `FeedbackItemDto {rating, version, updated_at}` / `FeedbackResponse {session_id, items}`
- [x] 2.3 错误码：非法 rating → 400 `{error:"rating_invalid"}`；CAS 冲突 → 409 `{current}`；
      sessionId / messageId 非法（白名单校验防路径穿越）→ 400；未知 session → 404
- [x] 2.4 `FeedbackControllerTest`（直接调控制器方法，`@TempDir` 隔离）：13 用例全绿
      （GET 空/GET 含数据/GET 非法 id/PUT happy/PUT 非法 rating/PUT 404/PUT 路径穿越/
      PUT CAS 冲突/PUT 版本错配/DELETE 匹配/DELETE 缺失/DELETE 版本错配/快照排序）

## F3: 前端赞踩（~1h）

- [x] 3.1 新增 `agent-web/frontend/src/api/feedback.ts`：`getFeedback` / `putFeedback` / `deleteFeedback`
      + `nextRating` 纯函数；`409` 抛 `FeedbackConflictError`（携带 `current`）
- [x] 3.2 `MessageActionRow` 加 👍/👎 按钮（copy 之后、children 之前）：两态互斥、点已选中项 = 取消；
      `data-testid="msg-up"` / `msg-down"`；`rating === undefined` 时整组不渲染
- [x] 3.3 `MessageBubble` / `ChatPanel` 接线：`ratingFor(uuid)` 决定 `undefined`（不渲染）/
      `null`（未选中）；挂载（localStorage 恢复）与切会话（侧边栏）两条路径都 `getFeedback` 拉全量
- [x] 3.4 乐观更新：点击立即改本地态 → 失败回滚；409 用 `current` 调和（`current:null` 表示对方已删）
- [x] 3.5 前端测试：`feedback.test.ts` 14 用例（`nextRating` 四态 + API 错误码映射 + URL 编码）+
      `MessageActionRow.test.tsx` P3 组 7 用例（无 uuid 不渲染 / 未选中 / up / down / 点击回调 / 顺序）+
      `ChatPanel.test.tsx` P3 组 7 用例（渲染 / 首屏高亮 / 首次 PUT / 取消 DELETE / 切换 / 500 回滚 / 409 调和）

## F4: 验证与归档

- [x] 4.1 `npx vitest run`：**356 passed + 1 skipped / 42 文件**（328 → 356）；9 条
      `EventSource is not defined` 为既有（上一 change 已在干净 HEAD 复现同样 9 条）→ 记录放行
- [x] 4.2 `npx tsc --noEmit` 错误数 **3** ≤ 基线
- [x] 4.3 `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` 全绿
      （agent-web **399** tests、jacoco 全达标；BUILD SUCCESS）
- [x] 4.4 测试数据隔离审计：`~/.agent-demo/feedback/` **不存在**（零污染）；仅
      `agent-web/target/test-data*/.agent-demo/feedback` 两个**空目录**（surefire 的 `agent.demo.home`
      隔离目录，位于 gitignored 的 `target/` 内、`mvn clean` 即清）→ 无需删除任何用户数据
- [x] 4.5 中文 Conventional Commits → push `feat/add-message-feedback`
- [x] 4.6 `openspec validate add-message-feedback --type change --strict` 通过 → archive
- [x] 4.7 按 §2.7.5 门禁合并回 `main` + 在 `main` 上复验 + push + 清理 worktree
- [x] 4.8 测试四件套 + `test-guide.md` 登记（§2.6.5）

## 实施期发现（未改，留后续）

- `ChatPanel` 的会话切换 effect 用 `lastSessionIdRef` 做「首次挂载不入内」短路：**挂载时若
  `currentSessionId` prop 已非空且 localStorage 无快照，历史与反馈都不会加载**。当前 `App.tsx`
  初值是 `"1"`（占位），真实流程（reload 有 localStorage / 侧边栏点击）不受影响，故未在本 change 动它。
  若要修，应让「挂载即传 sessionId」也走一次加载，属独立 bugfix change。

## Follow-up

- [ ] session 删除（归档）时级联删 `feedback/<sid>.json`
- [ ] 反馈聚合视图（哪些回复被踩最多）——需先想清楚是否对模型可见
