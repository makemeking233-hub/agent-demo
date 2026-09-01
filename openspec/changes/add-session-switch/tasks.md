# Tasks: 真实会话列表与切换

## 1. 后端：会话列表

- [x] 1.1 `SessionStore.listSessions(sessionsDir)`：扫描 sessions/*.jsonl（mtime 降序），id=文件名
- [x] 1.2 `SessionSummaryDto(id,title,preview,workspace)` + `SessionController` 加 `GET /api/sessions`

## 2. 前端：会话列表与切换

- [x] 2.1 `ChatApi.listSessions()`：`GET /api/sessions` → `SessionSummary[]`
- [x] 2.2 `App` 用真实列表替换 `PLACEHOLDER_SESSIONS`，传 `currentSessionId` 给 `ChatPanel`
- [x] 2.3 `ChatPanel` 响应 `currentSessionId`：清空 items + set sessionIdRef + history(id) 加载渲染

## 3. 测试与验证

- [x] 3.1 后端 `SessionControllerTest` 扩充（listReturnsSessions / listEmptyWhenNoSessions）
- [x] 3.2 前端 `vitest` 19 测试全绿
- [x] 3.3 `vite build` 更新产物；SessionControllerTest 6 个全绿
- [x] 3.4 commit + push + archive
