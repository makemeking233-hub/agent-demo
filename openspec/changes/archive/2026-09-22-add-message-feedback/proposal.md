# Proposal: add-message-feedback

## Why

`add-message-actions` 的 P1（copy）/ P2（per-message clock）已上线，但截图里的 **👍 / 👎 两个反馈按钮**没做。
用户明确要求「只做 👍/👎 两态」（不做备注文本），且反馈要**对模型不可见**——DSH 的做法是存独立
sidecar，不写进会话存档。这条需求与 `add-message-actions` 的 P1/P2 在存储与并发语义上差别很大
（新增 store + REST + CAS），单独成 change 更清楚。本 change 是 `add-message-actions` 里 P3 的正式拆分。

## What Changes

- 新增 `MessageFeedbackStore`（agent-core）：`~/.agent-demo/feedback/<sessionId>.json` 独立 sidecar，
  0600 文件 / 0700 目录，写操作加文件锁，**per-item version CAS**
- 新增 `FeedbackController`（agent-web）：`GET / PUT / DELETE` 三个端点，CAS 冲突返回 409 + `{current}`
- `MessageActionRow` 加 👍/👎 按钮，两态互斥（首次 / 取消 / 切换），乐观更新 + 409 用 `current` 调和 + 失败回滚
- 复用 `add-message-actions` 已落地的 `SessionMessageDto.uuid`：按钮的 `messageId` 就是它

## Impact

| 项 | 内容 |
|----|------|
| 新增文件 | `agent-core/.../session/MessageFeedbackStore.java`、`agent-web/.../api/FeedbackController.java`、`agent-web/frontend/src/api/feedback.ts` |
| 修改文件 | `MessageActionRow.tsx`（加按钮 + toggle）、`MessageBubble.tsx`（透传 rating 回调）、`ChatPanel.tsx`（拉取/更新 rating） |
| 新增目录 | `~/.agent-demo/feedback/`（0700） |
| API 契约 | 新增 `/api/feedback/**`；不改既有端点 |
| 模型可见性 | **无变化**：sidecar 不参与 `AgentLoop.toRequest()`，模型永远看不到反馈 |
| 数据隔离 | 测试必须用临时 `agentDataDir`（`@TempDir`），不得写真实 `~/.agent-demo/feedback/` |

## Out of Scope

- regenerate / 重新生成（走独立 change `add-message-regenerate`）
- 反馈备注文本（用户明确只要两态）
- session 删除时级联删 sidecar（follow-up）
- 反馈聚合报表 / 导出
