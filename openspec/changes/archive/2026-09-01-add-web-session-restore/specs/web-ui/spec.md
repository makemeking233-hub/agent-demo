# web-ui Specification

## ADDED Requirements

### Requirement: 会话重进恢复

系统 SHALL 在 web 会话被刷新或服务端重启后，根据客户端提供的 `session_id` 恢复该会话的对话历史（送模型的上下文与界面渲染），并把该会话持续落盘到 `~/.agent-demo/sessions/<id>.jsonl`。

#### Scenario: 刷新后恢复历史

- WHEN 客户端携带已知的 `session_id` 发送 `GET /api/sessions/{sessionId}/messages`
- AND 该会话的 `sessions/<id>.jsonl` 存在（已落盘）
- THEN 服务端返回 `200 OK`，响应体为 `{"session_id":"<id>","messages":[{"role":"user","content":"..."},{"role":"assistant","content":"...","toolCalls":[...]}...]}`
- AND 前端据其重建对话区消息（用户/助手文本、工具调用卡片）

#### Scenario: 服务端重启后仍可恢复

- WHEN 服务端重启，浏览器仍存有该 `session_id`，客户端发送新回合 `POST /api/chat/send {content, session_id}`
- AND `sessions/<id>.jsonl` 存在
- THEN `WebAgentRuntime.historyFor(sessionId)` 从磁盘回填历史，模型能看到重启前的对话内容
- AND 新回合与历史无缝衔接

#### Scenario: 未知会话回 404

- WHEN 客户端发送 `GET /api/sessions/{sessionId}/messages`，且该 `session_id` 无对应存档文件
- THEN 服务端返回 `404 Not Found`，响应体 `{"error":"session_not_found"}`

#### Scenario: 无 session_id 走全新会话

- WHEN 客户端发送 `POST /api/chat/send`，请求体无 `session_id`
- THEN 服务端创建全新会话，不回填任何历史
- AND 响应体返回新建的 `session_id`，供下次继续

#### Scenario: 落盘失败降级不阻断

- WHEN 该会话的 `SessionStore` 写盘失败（磁盘只读 / 权限异常）
- THEN 对话继续（历史仅驻留内存），不因落盘失败中断回合
