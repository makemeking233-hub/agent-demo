# web-ui Specification

## Purpose
TBD - created by archiving change add-web-ui-v0-1. Update Purpose after archive.
## Requirements
### Requirement: 发送聊天消息

系统 SHALL 通过 HTTP 接受用户消息并启动一次 agent 回合。

#### Scenario: 合法消息启动回合

- WHEN 客户端发送 `POST /api/chat/send`，请求体 JSON 为 `{"content": "你好", "session_id": "<uuid>"}`（`session_id` 可选；缺省时新建 session）
- AND 请求源 IP 在 trusted-hosts 白名单内（或 server 绑 127.0.0.1）
- THEN 服务端在 200ms 内返回 `200 OK`，响应体 `{"stream_id": "<uuid>", "session_id": "<uuid>", "model": "deepseek-chat"}`
- AND 服务端开始在 `GET /api/chat/stream/{stream_id}` 上推送 SSE 事件

#### Scenario: 空内容被拒

- WHEN 客户端发送 `POST /api/chat/send`，且 `content` 为空或全空白
- THEN 服务端返回 `400 Bad Request`，响应体 `{"error": "content_empty"}`
- AND 不创建 stream

#### Scenario: provider 未配置

- WHEN 客户端发送 `POST /api/chat/send`，且 `provider.apiKey` 在配置（环境变量 + yaml）中都缺失
- THEN 服务端返回 `503 Service Unavailable`，响应体 `{"error": "provider_not_configured", "hint": "set DEEPSEEK_API_KEY"}`
- AND 不创建 stream

### Requirement: 流式聊天（SSE）

系统 SHALL 通过 Server-Sent Events 推送 agent 回合输出。

#### Scenario: 流按序推送事件

- WHEN 客户端打开 `GET /api/chat/stream/{stream_id}`，请求头 `Accept: text/event-stream`
- AND 对应的 agent 回合正在运行
- THEN 服务端以 `Content-Type: text/event-stream` 和 `Cache-Control: no-cache` 推送事件
- AND 每个事件形如 `data: <json>\n\n`，其中 `<json>` 必须是以下之一：`message_start`、`message_delta`、`tool_call_start`、`tool_call_end`、`permission_request`、`message_stop`、`error`
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

### Requirement: SSE 事件类型

系统 SHALL 推送以下粗粒度 SSE 事件，形状稳定。

#### Scenario: message_start 载荷

- WHEN agent 回合开始
- THEN 服务端推送 `data: {"type":"message_start","stream_id":"<uuid>","session_id":"<uuid>","model":"deepseek-chat","timestamp":<epoch_ms>}`

#### Scenario: message_delta 载荷（文本）

- WHEN 模型产出文本 token
- THEN 服务端按 token chunk（每块 ≤ 80 字符）推送 `data: {"type":"message_delta","delta_type":"text","content":"<chunk>"}`
- AND 按序拼接所有 `content` 等于完整助手消息

#### Scenario: message_delta 载荷（思维链）

- WHEN provider 返回 reasoning 内容（例如 `deepseek-reasoner`）
- THEN 服务端按 chunk 推送 `data: {"type":"message_delta","delta_type":"thinking","content":"<chunk>"}`
- AND 客户端将思维链渲染为可折叠区块，与最终文本区分

#### Scenario: tool_call_start 载荷

- WHEN agent 触发工具调用
- THEN 服务端在工具执行前推送 `data: {"type":"tool_call_start","tool_call_id":"<uuid>","name":"<tool_name>","args":<json>}`

#### Scenario: tool_call_end 载荷

- WHEN 工具调用结束（成功或失败）
- THEN 服务端在工具返回后推送 `data: {"type":"tool_call_end","tool_call_id":"<uuid>","name":"<tool_name>","ok":<bool>,"result":<text-or-json>,"duration_ms":<int>}`

#### Scenario: permission_request 载荷

- WHEN agent 需要用户授权（工具的 `checkPermissions` 返回 `ASK`）
- THEN 服务端推送 `data: {"type":"permission_request","permission_id":"<uuid>","tool_call_id":"<uuid>","tool_name":"<tool_name>","reason":"<string>","choices":["yes","no","always"]}` 后暂停回合
- AND 客户端把它渲染为聊天消息，选项作为快捷回复按钮

#### Scenario: permission_request 决策

- WHEN 用户在 `permission_request` 之后提交恰好为 `yes` / `no` / `always` 的聊天消息
- THEN 服务端用该决策恢复 agent 回合
- AND 推送 `data: {"type":"permission_response","permission_id":"<uuid>","decision":"<value>"}`
- AND 在决策落地前提交的其他聊天消息会被排队，决策前不发给模型

#### Scenario: message_stop 载荷

- WHEN agent 回合完成（模型返回 `finish_reason=stop`、或 max iterations 命中、或 compact circuit broken、或 abort）
- THEN 服务端推送 `data: {"type":"message_stop","finish_reason":"stop"|"length"|"max_iterations"|"compact_broken"|"aborted"}` 后关闭 SSE 连接

#### Scenario: error 载荷

- WHEN 发生不可恢复错误（provider 多次重试后仍 5xx、内部异常）
- THEN 服务端推送 `data: {"type":"error","code":"<stable_code>","message":"<human>"}` 后关闭 SSE 连接
- AND 客户端把错误内联渲染并禁用输入框，直到用户发送新消息

### Requirement: 中断聊天

系统 SHALL 允许用户中断进行中的回合。

#### Scenario: 中断成功

- WHEN 客户端发送 `POST /api/chat/abort/{stream_id}` 且 stream_id 有效
- AND 流当前处于打开状态
- THEN 服务端返回 `200 OK`，响应体 `{"aborted": true}`
- AND 活动的 `AgentLoop` 迭代被中断（不再触发工具调用）

#### Scenario: 回合已结束的 abort 是幂等的

- WHEN 客户端发送 `POST /api/chat/abort/{stream_id}`，但回合已经结束
- THEN 服务端返回 `200 OK`，响应体 `{"aborted": false, "reason": "already_stopped"}`（不报错）

#### Scenario: 中断未知 stream

- WHEN 客户端发送 `POST /api/chat/abort/{stream_id}`，stream_id 未知
- THEN 服务端返回 `404 Not Found`，响应体 `{"error": "stream_not_found"}`

### Requirement: Slash 命令

系统 SHALL 通过 `POST /api/chat/send` 接受 slash 命令（与普通聊天输入共用入口），由 `SlashCommand` bean 执行，结果以合成的 `message_delta` 事件推送。

#### Scenario: /help 列出命令

- WHEN 用户提交 `/help`
- THEN 服务端执行 `SlashCommand.help()`
- AND 推送 `message_delta`，`delta_type:"text"`，`content` 列出全部命令及一行描述

#### Scenario: /clear 重置消息历史

- WHEN 用户提交 `/clear`
- THEN 服务端执行 `SlashCommand.clear()`——清空当前 session 的内存 `MessageHistory`
- AND 推送 `message_delta`，`content: "已清空当前会话历史"`
- AND 不启动 agent 回合

#### Scenario: /quit 关闭 session

- WHEN 用户提交 `/quit`
- THEN 服务端执行 `SlashCommand.quit()`——关闭当前 session（flush JSONL、关闭 stream）
- AND 推送 `message_stop` 后关闭 SSE
- AND 客户端收到 `{"closed": true}` 以跳转到落地页

#### Scenario: /resume（依赖 add-resume-command archive）

- WHEN 用户提交 `/resume`
- AND `add-resume-command` change 已 archive（即 `SessionStore.loadLatest` 已实现）
- THEN 服务端执行 `SlashCommand.resume()`——恢复最近的 JSONL session
- AND 推送 `message_delta` 总结加载内容

#### Scenario: 未知 slash 命令

- WHEN 用户提交 `/foo`（未注册的命令）
- THEN 服务端返回 `400`，响应体 `{"error": "unknown_command", "command": "/foo"}`
- AND 不启动 agent 回合

### Requirement: Trusted-Host 鉴权

系统 SHALL 对所有 `/api/**` 端点强制基于 IP 的访问控制。

#### Scenario: 可信主机通过

- WHEN 请求源 IP 命中 `agent.web.trusted-hosts` 条目（CIDR 或单 IP）
- AND 服务端端口监听接受该 IP
- THEN 请求正常处理

#### Scenario: 不可信主机被拒

- WHEN 请求源 IP 不在 `agent.web.trusted-hosts` 内
- AND 配置的 bind 是 LAN 可达的（例如 `--host=0.0.0.0` 或 `--host=<LAN-IP>`）
- THEN 服务端返回 `403 Forbidden`，响应体 `{"error": "host_not_trusted"}`
- AND 把该源 IP 以每分钟一次的频率在 WARN 级别记日志

#### Scenario: loopback 始终可信

- WHEN 请求源 IP 是 `127.0.0.1` 或 `::1`
- THEN 请求允许，无论 `trusted-hosts` 怎么配
- AND loopback 永不返 403

#### Scenario: 默认 bind 仅 loopback

- WHEN `agent.web.host` 未配置
- THEN 服务端只 bind `127.0.0.1`
- AND 只有 loopback 请求能成功
- AND 不暴露任何外部网络

#### Scenario: bind 0.0.0.0 被拒

- WHEN 启动时传入 `--host=0.0.0.0` 或 `agent.web.host=0.0.0.0`
- THEN 服务端拒绝启动，抛 `IllegalStateException("binding 0.0.0.0 is not supported; specify a concrete LAN IP")`
- AND 在 WARN 级别记一条拒绝日志

### Requirement: 当前 session

系统 SHALL 暴露当前 session 元数据，供 UI 渲染 session 列表。

#### Scenario: 活动 session 返回

- WHEN 客户端发送 `GET /api/sessions/current`
- AND 当前 session 处于活动状态（回合进行中，或当前 session_id 对应的 session 文件存在）
- THEN 服务端返回 `200 OK`，响应体 `{"session_id":"<uuid>","started_at":<epoch_ms>,"turn_count":<int>,"tokens_in":<int>,"tokens_out":<int>,"model":"<model>"}`

#### Scenario: 没有活动 session

- WHEN 客户端发送 `GET /api/sessions/current`，但尚未启动过任何 session
- THEN 服务端返回 `200 OK`，响应体 `{"session_id": null}`（HTTP 200，不是 404）

### Requirement: 健康检查

系统 SHALL 暴露轻量健康检查端点，用于监控与就绪探针。

#### Scenario: 服务健康

- WHEN 客户端发送 `GET /api/health`
- THEN 服务端返回 `200 OK`，响应体 `{"status":"ok","version":"<x.y.z>","uptime_s":<int>}`

#### Scenario: provider 未配置

- WHEN 客户端发送 `GET /api/health`，且 provider.apiKey 缺失
- THEN 服务端返回 `200 OK`，响应体 `{"status":"degraded","reason":"provider_not_configured"}`（不是 503——健康检查端点不应失败）

### Requirement: 静态资源托管

系统 SHALL 把 React SPA bundle 作为静态资源由同一个 Spring 服务端托管。

#### Scenario: 根路径返回 index

- WHEN 客户端访问 `http://<host>:<port>/`
- THEN 服务端返回 `200 OK`，`Content-Type: text/html`，内容为 SPA `index.html`
- AND `index.html` 通过 `<script src="/assets/*.js">` 等引用打包后的 JS/CSS

#### Scenario: 客户端路由回落到同一 index

- WHEN 客户端访问 `http://<host>:<port>/sessions/<uuid>`（SPA 路由）
- THEN 服务端返回 `200 OK`，内容仍为 `index.html`（不是 404——SPA 在客户端处理路由）

#### Scenario: 静态资源缓存

- WHEN 客户端请求 `/assets/index-<hash>.js`
- THEN 服务端返回 `200 OK`，`Cache-Control: public, max-age=31536000, immutable`（一年）
- AND `index.html` 自身 `Cache-Control: no-cache`，更新能立即生效

### Requirement: 构建流水线集成

系统 SHALL 通过 frontend-maven-plugin 把 React 构建集成进 Maven 构建。

#### Scenario: mvn package 同时构建后端与前端

- WHEN 开发者执行 `mvn clean package`
- THEN agent-web 模块的 frontend-maven-plugin 跑 `npm ci` 然后 `npm run build`
- AND Vite 输出（位于 `agent-web/frontend/dist/`）被复制到 `agent-web/src/main/resources/static/`
- AND `mvn package` 产出单个 `agent-web-<version>.jar`，含 Java 字节码与 React 静态资源

#### Scenario: CI 可选跳过前端构建

- WHEN CI 设置 `-Dskip.npm`（自定义属性）或环境变量 `SKIP_NPM=true`
- THEN frontend-maven-plugin 跳过 npm 步骤（若已存在 `static/` 则复用）
- AND Maven 构建其余部分正常进行

#### Scenario: 前端构建失败中断 Maven

- WHEN `npm run build` 退出码非零（TypeScript 错误、Vite 错误）
- THEN frontend-maven-plugin 让 Maven 构建失败
- AND 失败产物不会被复制到 `static/`

### Requirement: 与 CLI 向后兼容

系统 SHALL 保留现有 CLI 行为；加入 web profile MUST NOT 改动 CLI 默认行为。

#### Scenario: 默认 spring-boot:run 启动 CLI

- WHEN 开发者执行 `mvn spring-boot:run`（未指定 profile）
- THEN 现有 CLI 行为被保留：仅当设置 `-Dspring.profiles.active=web` 时才打印 `dsh web:` URL 行
- AND 默认 `chat` 子命令照常运行
- AND 无 HTTP 服务端绑定端口

#### Scenario: 显式 web profile 同时启动 web

- WHEN 开发者执行 `mvn spring-boot:run -Dspring.profiles.active=web`
- THEN CLI 的 REPL 被抑制（不读 stdin）
- AND HTTP 服务端在配置的端口启动
- AND 启动时打印一次 `dsh web: http://<host>:<port>` 日志

### Requirement: 日志查看 API

系统 SHALL 提供 HTTP API 供 Web UI 查看会话日志：`GET /api/logs/sessions` 列出日志会话目录；`GET /api/logs/sessions/{id}/events` 分页读取 `session.jsonl` 事件；`GET /api/logs/sessions/{id}/files/{name}` 读取 `chat.log` / `thinking.log` / `tools.log` 文本。所有端点 SHALL 沿用 trusted-hosts 鉴权。

#### Scenario: 列出日志会话

- WHEN 客户端发送 `GET /api/logs/sessions`，且 `logs/sessions/` 下存在会话目录
- THEN 返回 `200 OK`，响应体为数组，每项含 `id` 与文件存在性标记

#### Scenario: 分页读取事件

- WHEN 客户端发送 `GET /api/logs/sessions/{id}/events?offset=0&limit=50`
- THEN 返回 `200 OK`，响应体含 `events`（按 seq 顺序）与 `total`；超过一页时 `offset+limit < total`

#### Scenario: 读取可读日志文件

- WHEN 客户端发送 `GET /api/logs/sessions/{id}/files/chat.log`
- THEN 返回 `200 OK`，`Content-Type: text/plain; charset=utf-8`，正文为文件内容

#### Scenario: 非法会话 id 被拒

- WHEN 客户端发送 `GET /api/logs/sessions/..%2F..%2Fetc` 或含路径分隔符的 `{id}` / `{name}`
- THEN 返回 `400 Bad Request`，不读取任何文件

#### Scenario: 文件不存在

- WHEN 客户端请求 `{name}` 不在白名单（chat.log / thinking.log / tools.log / session.jsonl）内，或对应文件不存在
- THEN 返回 `404 Not Found`

### Requirement: 日志查看页面

系统 SHALL 在 Web UI 提供日志查看页面（路由 `/logs`）：展示会话列表，点击进入后按时间顺序渲染事件流（类型、内容摘要），并支持在「事件 / 聊天 / 工具」三种视图间切换。

#### Scenario: 会话列表渲染

- WHEN 用户访问 `/logs`
- THEN 页面显示日志会话列表，点击某项进入该会话的事件视图

#### Scenario: 事件流与视图切换

- WHEN 用户进入某会话的事件视图并切换到「工具」视图
- THEN 页面只渲染 `tool/call` 与 `tool/result` 事件，含工具名、参数摘要与结果摘要
- AND 切换回「事件」视图时恢复全量事件流

#### Scenario: 空会话提示

- WHEN 某会话目录存在但 `session.jsonl` 为空或不存在
- THEN 页面显示空态提示，不报错

### Requirement: 对话区消息渲染

系统 SHALL 在中间对话区渲染用户/助手消息与工具调用/权限卡片，并以 DeepSeek Harness 风格样式呈现。

#### Scenario: 助手 markdown 渲染

- **WHEN** 助手消息包含 markdown 文本
- **THEN** 对话区用 Markdown 渲染显示（含行内代码、粗体、代码块）

#### Scenario: 工具调用卡片三态

- **WHEN** 一轮中出现工具调用
- **THEN** 对话区在 tool_call_start 时渲染"执行中"卡片，tool_call_end 时更新为"完成/失败"卡片，并显示耗时与结果

### Requirement: 会话流式状态

系统 SHALL 在流式回复期间显示进行中状态，并允许用户中断。

#### Scenario: abort 按钮

- **WHEN** 流式回复进行中（stream_id 存在且未 message_stop）
- **THEN** 底部输入区显示 abort 按钮，点击后调用 `/api/chat/abort/{id}`

#### Scenario: 流结束恢复输入

- **WHEN** 收到 message_stop 或流错误
- **THEN** abort 按钮消失，输入框恢复可用

