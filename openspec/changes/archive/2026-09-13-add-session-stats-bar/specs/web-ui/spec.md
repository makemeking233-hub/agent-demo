## MODIFIED Requirements

### Requirement: 流式聊天（SSE）

系统 SHALL 通过 Server-Sent Events 推送 agent 回合输出。

#### Scenario: 流按序推送事件

- WHEN 客户端打开 `GET /api/chat/stream/{stream_id}`，请求头 `Accept: text/event-stream`
- AND 对应的 agent 回合正在运行
- THEN 服务端以 `Content-Type: text/event-stream` 和 `Cache-Control: no-cache` 推送事件
- AND 每个事件形如 `data: <json>\n\n`，其中 `<json>` 必须是以下之一：`message_start`、`message_delta`、`tool_call_start`、`tool_call_end`、`permission_request`、`turn_stats`、`message_stop`、`error`
- AND 事件按因果序到达（`tool_call_end` 不会出现在它的 `tool_call_start` 之前）

#### Scenario: 回合结束流关闭

- WHEN `message_stop` 事件已推送
- THEN 服务端在 1s 内关闭 SSE 连接
- AND 客户端可安全地用同一 `session_id` 再发新回合

#### Scenario: 主动中断流关闭

- WHEN 客户端在流打开期间发送 `POST /api/chat/abort/{stream_id}`
- THEN 服务端推送最后一个 `error` 事件 `{"code": "aborted"}` 后关闭 SSE 连接
- AND `AgentLoop` 中的回合被中断（不再推送后续事件）

#### Scenario: stream_id 不存在

- WHEN 客户端用未知 id 打开 `GET /api/chat/stream/{stream_id}`
- THEN 服务端返回 `404 Not Found`，响应体 `{"error": "stream_not_found"}`

#### Scenario: 断线重连

- WHEN SSE 连接在回合中途断开（网络抖动）
- AND 客户端用同一 `stream_id` 重连
- THEN 服务端从客户端上次断开的位置恢复事件（通过 `Last-Event-ID` 请求头定位）
- AND 每个回合最多推送一次 `message_stop`（幂等）
