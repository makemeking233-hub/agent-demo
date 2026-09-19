## MODIFIED Requirements

### Requirement: /api/chat/send 发送聊天消息

系统 SHALL 通过 HTTP 接受用户消息并启动一次 agent 回合。

#### Scenario: 合法消息启动回合

- WHEN 客户端发送 `POST /api/chat/send`，请求体 JSON 为 `{"content": "你好", "session_id": "<uuid>"}`（`session_id` 可选；缺省时新建 session）
- AND 请求源 IP 在 trusted-hosts 白名单内（或 server 绑 127.0.0.1）
- THEN 服务端在 200ms 内返回 `200 OK`，响应体 `{"stream_id": "<uuid>", "session_id": "<uuid>", "model": "<解析后的模型 id>"}`
- AND 服务端开始在 `GET /api/chat/stream/{stream_id}` 上推送 SSE 事件

#### Scenario: 服务端用配置/环境变量覆盖上游 base URL

- WHEN yml 配 `agent.chat.providers[].baseUrl` 或环境变量 `DEEPSEEK_BASE_URL` 指向自部署/代理的 base URL
- THEN `LlmProvider` 实例的上游 HTTP 请求实际打到该 URL（不是默认 `https://api.deepseek.com`）

> 此前：覆盖值被读、合并、丢弃；现修复为真正生效。MiniMaxProvider 同语义。
