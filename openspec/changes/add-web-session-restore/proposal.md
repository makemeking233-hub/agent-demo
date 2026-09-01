# 提案：Web 会话重进恢复

## Why

web（`agent-web`）当前把每条会话的对话历史仅保存在服务端内存
`ConcurrentHashMap<String, MessageHistory>`（`WebAgentRuntime`）。浏览器刷新或服务端重启后，
之前的对话记忆丢失，无法恢复。CLI 会话已落盘到 `~/.agent-demo/sessions/<id>.jsonl` 且可 `/resume`，
但 web 没有落盘、没有历史查询接口、前端也未持久化会话 id。

参考 DeepSeek Harness 方案：事件日志持久化到磁盘 + 客户端持久化"当前会话 id" +（重）连接后从日志回填历史。

## What Changes

- 服务端：web 会话落盘到 `~/.agent-demo/sessions/<id>.jsonl`（复用 CLI 的 SessionStore/SessionRecorder 格式）；
  `historyFor(sessionId)` 首次触达会话时从磁盘回填历史。
- 服务端：新增 `GET /api/sessions/{sessionId}/messages`，返回可渲染的会话消息，供前端回填。
- 前端：将 `session_id` + 消息快照持久化到 localStorage；刷新/重进后恢复 `session_id` 并从服务端回填历史。

## Impact

- `agent-core`：`SessionStore` 增加按 id 读取；`SessionResumeLoader` 增加按 id 加载并抽公共转换；
  新增 `CompositeSessionLogSink`。
- `agent-web`：`WebAgentRuntime` 增加 per-session 落盘 recorder 与 history 回填；新增 history 端点；
  前端 `ChatPanel` 持久化/回填。
- 兼容性：CLI 行为不变；web 仅在已有 `session_id` 时回填，无 `session_id` 仍走全新会话。

## Out of Scope

- Sidebar 会话列表的真实数据源（当前为占位数据）与按 workspace 分组。
- `/api/sessions/current` 的自动寻址（仍返回 null；前端直接持久化 session_id）。
- 消息历史的分页 / 全文搜索。

## 风险与降级

- `SessionStore` 写盘失败时降级为不落盘（不阻断对话）。
- 回填历史超 token 上限沿用现有 `snip` 裁剪。
