## ADDED Requirements

### Requirement: 日志查看 API

系统 SHALL 提供 HTTP API 供 Web UI 查看会话日志：`GET /api/logs/sessions` 列出日志会话目录；`GET /api/logs/sessions/{id}/events` 分页读取 `session.jsonl` 事件；`GET /api/logs/sessions/{id}/files/{name}` 读取 `chat.log` / `thinking.log` / `tools.log` 文本。所有端点 SHALL 沿用 trusted-hosts 鉴权。

#### Scenario: 列出日志会话

- **WHEN** 客户端发送 `GET /api/logs/sessions`，且 `logs/sessions/` 下存在会话目录
- **THEN** 返回 `200 OK`，响应体为数组，每项含 `id`、`createdAt`（目录名解析）与文件存在性标记

#### Scenario: 分页读取事件

- **WHEN** 客户端发送 `GET /api/logs/sessions/{id}/events?offset=0&limit=50`
- **THEN** 返回 `200 OK`，响应体含 `events`（按 seq 顺序）与 `total`；超过一页时 `offset+limit < total`

#### Scenario: 读取可读日志文件

- **WHEN** 客户端发送 `GET /api/logs/sessions/{id}/files/chat.log`
- **THEN** 返回 `200 OK`，`Content-Type: text/plain; charset=utf-8`，正文为文件内容

#### Scenario: 非法会话 id 被拒

- **WHEN** 客户端发送 `GET /api/logs/sessions/..%2F..%2Fetc` 或含路径分隔符的 `{id}` / `{name}`
- **THEN** 返回 `400 Bad Request`，不读取任何文件

#### Scenario: 文件不存在

- **WHEN** 客户端请求 `{name}` 不在白名单（chat.log / thinking.log / tools.log / session.jsonl）内，或对应文件不存在
- **THEN** 返回 `404 Not Found`

### Requirement: 日志查看页面

系统 SHALL 在 Web UI 提供日志查看页面（路由 `/logs`）：展示会话列表，点击进入后按时间顺序渲染事件流（类型、时间、内容摘要），并支持在「事件 / 聊天 / 工具」三种视图间切换。

#### Scenario: 会话列表渲染

- **WHEN** 用户访问 `/logs`
- **THEN** 页面显示日志会话列表（id + 创建时间），点击某项进入该会话的事件视图

#### Scenario: 事件流与视图切换

- **WHEN** 用户进入某会话的事件视图并切换到「工具」视图
- **THEN** 页面只渲染 `tool/call` 与 `tool/result` 事件，含工具名、参数摘要、结果摘要与耗时
- **AND** 切换回「事件」视图时恢复全量事件流

#### Scenario: 空会话提示

- **WHEN** 某会话目录存在但 `session.jsonl` 为空或不存在
- **THEN** 页面显示空态提示，不报错
