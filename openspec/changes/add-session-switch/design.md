## Context

web 前端侧边栏会话列表是硬编码占位（`PLACEHOLDER_SESSIONS`），点击只改 `currentSessionId` 状态但未传入 `ChatPanel`，`ChatPanel` 不响应 → 切换失效。后端已有 `GET /api/sessions/{id}/messages`（`SessionController.messages`）与 `WebAgentRuntime.messagesFor(sessionId)`，但**无会话列表端点**。`SessionStore` 也无列 session 方法。

## Goals / Non-Goals

**Goals:**
- 后端 `GET /api/sessions` 返回真实会话列表（id/title/preview/workspace）。
- 前端侧边栏展示真实列表；点击切换：`ChatPanel` 加载该会话历史渲染 + `session_id` 切换。

**Non-Goals:**
- 不做"新建/删除会话"操作（保留占位新建）。
- workspace v0.1 用默认值（从会话 cwd 提取后续做）。
- 不改变会话落盘/恢复逻辑（复用 `history`/`messagesFor`）。

## Decisions

**D1: `SessionStore.listSessions(sessionsDir)` 静态扫描。**
- 遍历 `sessions/*.jsonl`（按 mtime 降序），对每个文件读取**首条 user 消息**作为 title/preview；id 用文件名（去 `.jsonl`）。异常文件跳过。
- 备选：解析会话 header/cwd 取 workspace。否决——当前 jsonl 无统一 header，先默认 workspace。

**D2: `SessionController` 加 `GET /api/sessions`。**
- 返回 `List<SessionSummaryDto(id,title,preview,workspace)>`，来自 `SessionStore.listSessions`。
- workspace 默认 `"agent-demo"`（v0.1 简化）。

**D3: 前端 `ChatApi.listSessions()`。**
- `GET /api/sessions` → `SidebarSession[]`。

**D4: `App` 用真实列表 + 传 currentSessionId 给 `ChatPanel`。**
- App 启动拉 `listSessions()` 存入 state；`onSelect=setCurrentSessionId`。
- 把 `currentSessionId` 作为 prop 传入 `ChatPanel`。

**D5: `ChatPanel` 响应 currentSessionId 变化。**
- useEffect(currentSessionId)：清空 items、`sessionIdRef=currentSessionId`；非空时 `ChatApi.history(id)` 加载历史，把 `HistoryMessage[]` 转成 items 渲染（user→MessageBubble，assistant→MessageBubble 含 toolCalls 转工具卡片，tool→ToolCallCard）。
- 发送消息时用 `sessionIdRef`（即当前会话），延续上下文。

## Risks / Trade-offs

- [历史消息转 items 渲染] → user/assistant 文本用 MessageBubble；assistant 的 toolCalls 用 ToolCallCard（内联）；tool_result 聚合成 assistant 工具卡片。
- [切换时正在流式] → 切换先 abort 当前流（clearing items + 新 sessionId）。v0.1 简化：清空后加载，已有流由业务中止。
- [listSessions 读文件开销] → 只读首行/首条消息，轻量。

## Migration Plan

1. 后端 `SessionStore.listSessions` + `SessionController` `/api/sessions` + `SessionSummaryDto`。
2. 前端 `ChatApi.listSessions`。
3. `App` 用真实列表 + 传 prop。
4. `ChatPanel` 切换加载历史渲染。
5. 测试；`mvn verify`/`vitest` 全绿。

## Open Questions

- 历史消息中 assistant 若无 text 只有 toolCalls 时的渲染：用空 MessageBubble + 内联工具卡片（对齐现有内联逻辑）。
