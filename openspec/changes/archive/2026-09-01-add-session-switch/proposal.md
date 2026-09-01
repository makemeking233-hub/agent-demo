## Why

web 前端侧边栏会话列表当前是**硬编码占位**（`PLACEHOLDER_SESSIONS`），点击某个会话只改高亮状态，`ChatPanel` 完全不响应（`currentSessionId` 未传入），导致"点了会话切换不了"。用户希望点侧边栏会话能真正切换（加载该会话历史并渲染）。

## What Changes

- **后端**：`SessionStore` 加静态 `listSessions(sessionsDir)` 扫描 `sessions/*.jsonl`；`SessionController` 加 `GET /api/sessions` 返回会话列表（id / title / preview / workspace）。
- **前端**：
  - `App` 用真实会话列表（调 `ChatApi.listSessions()`）替换 `PLACEHOLDER_SESSIONS`。
  - 把 `currentSessionId` 传入 `ChatPanel`。
  - `ChatPanel` 在 `currentSessionId` 改变时，用 `ChatApi.history(sessionId)` 加载该会话历史并渲染到对话区；无历史时清空。
- `ChatApi` 加 `listSessions()` 方法（`GET /api/sessions`）。

## Capabilities

### New Capabilities
- （无）

### Modified Capabilities
- `web-ui`：修改"会话列表"相关行为——侧边栏展示真实会话、点击可切换并加载对应历史。

## Impact

- 后端：`agent-core/.../session/SessionStore.java`（加 listSessions）、`agent-web/.../api/SessionController.java`（加 `/api/sessions`）、新增 DTO（`SessionListResponse`/`SessionSummaryDto`）。
- 前端：`agent-web/frontend/src/App.tsx`、`components/ChatPanel.tsx`、`api/chat.ts`。
- 测试：后端 `SessionControllerTest`/`SessionStoreTest` 扩充；前端 `ChatPanel`/`App` 相关。
- 无破坏性 API（新增端点；前端行为增强）。

## Out of Scope

- 不新增"新建会话/删除会话"操作（保留占位新建）；只做"切换 + 加载历史"。
- workspace 分组 v0.1 用默认值（后续可从会话 cwd 提取）。
