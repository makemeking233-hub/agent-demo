# add-message-actions

> **归档说明（2026-09-22）**：本 change 实际交付 **P1（copy）+ P2（clock）**；原 P3（赞踩 + feedback sidecar）
> 因存储/并发语义独立，已拆为 `openspec/changes/add-message-feedback/`。下文 P3 相关段落保留为提案期原文。

## Why

agent-demo Web UI 的 assistant 消息**没有任何操作栏**——用户无法复制回复、无法评价好坏、看不到该条消息的耗时与吞吐。对比 DSH Web（`MessageIconActions.tsx`：copy + 赞踩 + branch + clock），差距明显。

当前证据：
- `MessageBubble.tsx` 只渲染 markdown / mermaid / thinking，**零 action**
- `SseEvent.TurnStats` 只有**会话累计**值（turns/tokensIn/llmMs/avgTtftMs/tokPerSec），**没有 per-turn 数据**，无法做 DSH 式 `Ran for 15s · TTFT 1.2s · 34 tok/s`
- 无 message 级反馈存储

## What Changes

- **P1 copy**：`MessageBubble` 加 action row（copy 按钮 + `navigator.clipboard` + 1s ✓ 反馈 + epoch 防重入）
- **P2 clock**：后端新增 SSE `message_meta` 事件（`{uuid, durationMs, ttftMs, tokPerSec}`）；前端按 `uuid` 绑定到对应 assistant 消息，渲染 `16:23 · Ran for 15s · TTFT 1.2s · 34 tok/s`
- **P3 赞踩**：新增 `MessageFeedbackStore` sidecar（`~/.agent-demo/feedback/<sessionId>.json`）+ REST（`GET/PUT/DELETE /api/feedback/{sessionId}`）+ per-item `version` CAS + 前端 👍/👎 两态切换

## Impact

- 受影响模块：`agent-web/api/dto/SseEvent`（+message_meta）、`agent-web/stream/SseSessionLogSink`（采集 per-turn 时序）、`agent-core/session/MessageFeedbackStore`（新）、`agent-web/api/FeedbackController`（新）、`frontend/components/MessageBubble`（+action row）
- API：新增 3 个 feedback 端点 + 1 个 SSE event 类型（向后兼容：老前端忽略未知 event）
- 数据：新增 `~/.agent-demo/feedback/<sessionId>.json`（**不进 session log，模型不可见**）
- 行为：assistant 消息 finalize 后显示 action row；流式中不显示

## Out of Scope

- **regenerate（重新生成）**：需 surface replacement 语义（log 保留 + replacement-origin events），独立 change `add-message-regenerate`（~8h）
- **branch（分叉会话）**：DSH 有、agent-demo 无，独立 change
- **feedback note（备注文本）**：本次只做 👍/👎 两态
- **feedback 对模型可见**：sidecar 不进 session log，模型永远看不到