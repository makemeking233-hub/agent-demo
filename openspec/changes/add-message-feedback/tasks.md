# Tasks: add-message-feedback

> 来源：`add-message-actions` 的 P3 拆分（P1/P2 已归档）。
> 前置：`SessionMessageDto.uuid` 与前端 `Item.uuid` 已在 P2 落地，反馈按钮直接用它们做 `messageId`。

## F1: sidecar 存储（agent-core，~1h）

- [ ] 1.1 新增 `agent-core/src/main/java/com/example/agent/session/MessageFeedbackStore.java`
      （构造接 `Path feedbackDir`；`get(sessionId)` / `put(sessionId, messageId, rating, ifVersion)` / `delete(sessionId, messageId, ifVersion)`）
- [ ] 1.2 sidecar schema v1：`{version:1, session_id, items:{<uuid>:{rating, version, updated_at}}}`；
      文件 0600、目录 0700（POSIX；Windows 跳过）
- [ ] 1.3 写操作加文件锁（`<sessionId>.lock`，`FileChannel.tryLock` + 超时退避），读操作不加锁
- [ ] 1.4 CAS：`ifVersion == null` = 必须不存在；`ifVersion == N` = 必须等于当前 version；
      冲突抛 `MessageFeedbackVersionConflict`（携带 `current`），未命中 lock 抛可重试异常
- [ ] 1.5 `MessageFeedbackStoreTest`（`@TempDir`，**不得写真实 `~/.agent-demo`**）：创建 / CAS 冲突 /
      删除 / 记录不存在时删除 / 并发两线程 PUT 同 message（6+ 用例）

## F2: REST 端点（agent-web，~45min）

- [ ] 2.1 新增 `agent-web/src/main/java/com/example/agent/web/api/FeedbackController.java`
      （`@Profile("web")`）：`GET /api/feedback/{sessionId}`、`PUT /api/feedback/{sessionId}/{messageId}`、
      `DELETE /api/feedback/{sessionId}/{messageId}`
- [ ] 2.2 DTO：`FeedbackPutRequest {rating, ifVersion}` / `FeedbackDeleteRequest {ifVersion}` /
      `FeedbackItemDto {rating, version}` / `FeedbackResponse {items}`
- [ ] 2.3 错误码：非法 rating → 400 `{error:"rating_invalid"}`；CAS 冲突 → 409 `{current}`；
      sessionId / messageId 非法（白名单校验防路径穿越）→ 400
- [ ] 2.4 `FeedbackControllerTest`（直接调控制器方法，遵循 `SessionControllerTest` 惯例）：
      3 端点 happy path + 400 + 409 + 空 session 返回 `{items:{}}`（5+ 用例）

## F3: 前端赞踩（~1h）

- [ ] 3.1 新增 `agent-web/frontend/src/api/feedback.ts`：`getFeedback(sessionId)` /
      `putFeedback(sessionId, messageId, rating, ifVersion)` / `deleteFeedback(sessionId, messageId, ifVersion)`；
      `409` 抛出携带 `current` 的 `FeedbackConflictError`
- [ ] 3.2 `MessageActionRow` 加 👍/👎 按钮（`extraActions` 位置）：两态互斥、点已选中项 = 取消；
      `data-testid="msg-up"` / `msg-down"`
- [ ] 3.3 `MessageBubble` / `ChatPanel` 接线：`currentSessionId` + `item.uuid` 存在才渲染按钮；
      进入会话时 `getFeedback` 一次性拉全量 rating
- [ ] 3.4 乐观更新：点击立即改本地态 → 请求失败回滚；409 用 `current` 调和（`current:null` 表示对方删了）
- [ ] 3.5 `MessageActionRow.test.tsx` / `ChatPanel.test.tsx`：toggle 三态 / 409 调和 / 500 回滚 /
      无 uuid 不渲染（5+ 用例）

## F4: 验证与归档

- [ ] 4.1 `npx vitest run` 全绿（基线外的既存 `EventSource is not defined` 需先归因再放行）
- [ ] 4.2 `npx tsc --noEmit` 错误数 ≤ 基线（当前 3，AGENTS.md §2.7.7 记的 7 是旧值）
- [ ] 4.3 `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` 全绿（含 jacoco）
- [ ] 4.4 测试数据隔离审计：确认测试只写 `@TempDir`；跑完核对 `~/.agent-demo/feedback/` 无测试残留
- [ ] 4.5 中文 Conventional Commits → push `feat/add-message-feedback`
- [ ] 4.6 `openspec validate add-message-feedback --type change --strict` 通过 → archive
- [ ] 4.7 按 §2.7.5 门禁合并回 `main` + 在 `main` 上复验 + push + 清理 worktree

## Follow-up

- [ ] session 删除（归档）时级联删 `feedback/<sid>.json`
- [ ] 反馈聚合视图（哪些回复被踩最多）——需先想清楚是否对模型可见
