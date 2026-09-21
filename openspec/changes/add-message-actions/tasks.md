# Tasks: add-message-actions

> scope：P1 copy + P2 clock + P3 赞踩（**不含** regenerate，那个走独立 change `add-message-regenerate`）
> 本 session 目标：**P1 + P2**（~2.5h）；P3 留给下次 session

## P1: copy 按钮（前端，~1h）

- [ ] 1.1 新增 `MessageActionRow.tsx`：`[copy] [children]` row 布局（flex + gap + 半透明 hover）
- [ ] 1.2 copy 按钮：`navigator.clipboard.writeText` + 1s ✓ 反馈（`copyPending` ref 防重入 + `copyEpoch` ref 防 unmount setState）
- [ ] 1.3 降级路径：`navigator.clipboard` 抛错 → 隐藏 textarea + `document.execCommand('copy')`
- [ ] 1.4 `MessageBubble.tsx` 集成：assistant 消息底部渲染 `MessageActionRow`（user 消息只渲染 copy）
- [ ] 1.5 `MessageActionRow.module.css`：图标按钮 + hover 态 + ✓/📋 图标切换
- [ ] 1.6 `MessageActionRow.test.tsx`：复制成功 / ✓ 1s 后恢复 / 防重入 / 降级路径（4+ 用例）

## P2: per-message clock（全栈，~1.5h）

### 后端

- [ ] 2.1 `SseEvent.java` 加 `MessageMeta` record（`{type:"message_meta", uuid, duration_ms, ttft_ms, tok_per_sec, timestamp}`）
- [ ] 2.2 `SseSessionLogSink.onAssistant()`：采集 per-turn 时序（turn 开始时刻 → 首 token 时刻 → finalize 时刻 → usage）
- [ ] 2.3 在 `message_stop` **之前**推送 `message_meta`
- [ ] 2.4 `SseSessionLogSinkTest`：`message_meta` 在 `message_stop` 前推送 / 字段正确 / 无 usage 时 ttftMs/tokPerSec 为 null（3+ 用例）

### 前端

- [ ] 2.5 `sse-client` / `useChatStream`：订阅 `message_meta`，按 `uuid` 存到 `Map<uuid, MessageMeta>`
- [ ] 2.6 `MessageActionRow` 加 clock 渲染：`{HH:MM} · Ran for {N}s · TTFT {N.N}s · {N} tok/s`（null 段跳过）
- [ ] 2.7 `MessageBubble` 把 clock 数据传给 action row
- [ ] 2.8 历史加载（`GET /api/sessions/{id}/messages`）也返回 per-message meta → 刷新后 clock 仍在
- [ ] 2.9 `MessageActionRow.test.tsx`：clock 全字段 / 缺 ttftMs / 缺 tokPerSec / 缺全部（4+ 用例）

## P3: 赞踩 + feedback sidecar（全栈，~2.5h）— 下次 session

### 后端

- [ ] 3.1 新增 `agent-core/session/MessageFeedbackStore.java`：sidecar 读写 + 文件锁 + CAS
- [ ] 3.2 sidecar schema v1（`{version:1, session_id, items:{uuid:{rating, version, updated_at}}}`）+ 0600 权限
- [ ] 3.3 新增 `agent-web/api/FeedbackController.java`：`GET/PUT/DELETE /api/feedback/{sessionId}[/{messageId}]`
- [ ] 3.4 CAS 冲突 → 409 + `{current}`；非法 rating → 400
- [ ] 3.5 `MessageFeedbackStoreTest`：创建 / CAS 冲突 / 删除 / 并发锁（6+ 用例）
- [ ] 3.6 `FeedbackControllerTest`：3 端点 + 错误码（5+ 用例）

### 前端

- [ ] 3.7 `api/feedback.ts`：3 个 fetch 封装 + 409 冲突处理
- [ ] 3.8 `MessageActionRow` 加 👍/👎 按钮 + toggle 语义（首次/取消/切换）
- [ ] 3.9 乐观更新 + 冲突时用 `current` 调和 + 失败回滚
- [ ] 3.10 `MessageActionRow.test.tsx`：toggle 三态 / 409 调和 / 失败回滚（5+ 用例）

## 验证与归档

- [ ] 4.1 跑 `npx vitest run` 全绿（预期 302 + ~15 = ~317）
- [ ] 4.2 跑 `npx tsc --noEmit` 错误数 ≤ 7（基线）
- [ ] 4.3 跑 `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` 全绿
- [ ] 4.4 中文 Conventional Commits 分 commit（feat/test）+ 立即 push
- [ ] 4.5 `openspec validate add-message-actions --type change --strict` 通过
- [ ] 4.6 `openspec archive add-message-actions --yes` 合并 delta spec
- [ ] 4.7 合并回 main + 在 main 上复验 + push main + cleanup

## Follow-up（独立 change）

- [ ] **`add-message-regenerate`**：surface replacement 语义（log 保留 + replacement-origin events）+ regenerate UI + REST（~8h）
- [ ] **`add-session-branch`**：分叉会话（DSH branch 语义）（~4h）
- [ ] sidecar 清理：session 删除时级联删 `feedback/<sid>.json`
- [ ] feedback note（备注文本）