## MODIFIED Requirements

### Requirement: 发送聊天消息

系统 SHALL 通过 HTTP 接受用户消息并启动一次 agent 回合。

#### Scenario: 合法消息启动回合

- WHEN 客户端发送 `POST /api/chat/send`，请求体 JSON 为 `{"content": "你好", "session_id": "<uuid>"}`（`session_id` 可选；缺省时新建 session）
- AND 请求源 IP 在 trusted-hosts 白名单内（或 server 绑 127.0.0.1）
- THEN 服务端在 200ms 内返回 `200 OK`，响应体 `{"stream_id": "<uuid>", "session_id": "<uuid>", "model": "<解析后的模型 id>"}`
- AND `model` 的取值遵循 `模型选择真源单一` 的解析规则
- AND 服务端开始在 `GET /api/chat/stream/{stream_id}` 上推送 SSE 事件

#### Scenario: 空内容被拒

- WHEN 客户端发送 `POST /api/chat/send`，且 `content` 为空或全空白
- THEN 服务端返回 `400 Bad Request`，响应体 `{"error": "content_empty"}`
- AND 不创建 stream

#### Scenario: provider 未配置

- WHEN 客户端发送 `POST /api/chat/send`，且 `provider.apiKey` 在配置（环境变量 + yaml）中都缺失
- THEN 服务端返回 `503 Service Unavailable`，响应体 `{"error": "provider_not_configured", "hint": "set DEEPSEEK_API_KEY"}`
- AND 不创建 stream

### Requirement: /api/chat/models 端点

后端 SHALL 提供 `GET /api/chat/models` 端点，返回 provider / model 分层目录 `{"providers": [{"id": "<provider id>", "name": "...", "models": [{"id": "...", "name": "...", "supportsReasoning": <bool>, "reasoningEfforts": [...]}]}], "defaultProvider": "<provider id>", "defaultModel": "<model id>"}`。

`providers` SHALL 取自配置 `agent.chat.providers`；`defaultProvider` / `defaultModel` SHALL 取自配置 `agent.chat.default-provider` / `agent.chat.default-model`。响应同时 SHALL 保留平铺 `models[]` 字段以供过渡期前端消费。

#### Scenario: 列出 providers 与默认值

- WHEN 客户端发 `GET /api/chat/models`
- THEN 返回 `providers[]`，每项含 `id` / `name` / `models[]`，每个 model 含 `id` / `name` / `supportsReasoning` / `reasoningEfforts`
- AND 顶层 `defaultProvider` 与 `defaultModel` 分别等于配置的 `agent.chat.default-provider` 与 `agent.chat.default-model`
- AND `defaultModel` 必定存在于 `providers[]` 的某个 `models[]` 中

#### Scenario: 平铺字段与嵌套结构一致

- WHEN 客户端发 `GET /api/chat/models`
- THEN 平铺 `models[]` 是 `providers[].models[]` 的扁平化结果
- AND 两者包含完全相同的 model id 集合

#### Scenario: trusted-host 鉴权

- WHEN 客户端源 IP 不在 trusted-hosts 白名单
- THEN 返回 `403 host_not_trusted`

## ADDED Requirements

### Requirement: 模型选择真源单一

系统 SHALL 以配置 `agent.chat.providers`（经 `ProviderCatalogService` 构造的 `ModelCatalog`）作为「哪些模型合法」的**唯一**真源。任何校验、默认值、列表端点 SHALL NOT 依赖第二个来源（如独立配置 key 或代码内硬编码的模型 id 常量）。

`ProviderCatalogService` SHALL 在启动时校验配置的 `default-provider` 与 `default-model` 均存在于目录中，任一不匹配则启动失败（fail-fast），SHALL NOT 让一个非法的默认值进入运行时。

#### Scenario: 默认模型不在目录中则启动失败

- WHEN 配置 `agent.chat.default-model` 为一个不在 `agent.chat.providers` 中的 id
- THEN `ProviderCatalogService` 初始化抛 `IllegalStateException`
- AND 应用启动失败，不进入可服务状态

#### Scenario: 默认 provider 不在目录中则启动失败

- WHEN 配置 `agent.chat.default-provider` 为一个不在 `agent.chat.providers` 中的 id
- THEN `ProviderCatalogService` 初始化抛 `IllegalStateException`
- AND 应用启动失败，不进入可服务状态

#### Scenario: 默认值合法则正常启动

- WHEN `agent.chat.default-provider` 与 `agent.chat.default-model` 均能匹配到目录中的 provider / model
- THEN 应用正常启动
- AND `GET /api/chat/models` 返回的 `defaultProvider` / `defaultModel` 与配置一致

### Requirement: 非法模型被拒绝

当客户端在 `POST /api/chat/send` 中显式指定了 `model`，系统 SHALL 以 `ModelCatalog` 校验其合法性：

- `model` 为 `null` 或缺省或全空白 → 视为「未指定」，使用 `agent.chat.default-model`；
- `model` 命中目录 → 原样使用；
- `model` 非空但未命中目录 → 返回 `400 Bad Request`，响应体含 `{"error": "invalid_model", "requested": "<收到的值>", "supported": ["<合法 id>", ...]}`，且 SHALL NOT 创建 stream。

系统 SHALL NOT 把未通过校验的模型 id 透传给上游 Provider，也 SHALL NOT 在一个非法 `model` 上静默回退后继续执行回合。

#### Scenario: 未指定模型走配置默认值

- WHEN 客户端发送 `POST /api/chat/send`，请求体不含 `model` 字段（或 `model` 为空白串）
- THEN 服务端返回 `200 OK`，响应体 `model` 等于 `agent.chat.default-model`
- AND 该值必定存在于 `agent.chat.providers` 目录中

#### Scenario: 合法模型原样透传

- WHEN 客户端发送 `{"content": "hi", "model": "<目录中的某个 id>"}`
- THEN 服务端返回 `200 OK`，响应体 `model` 与请求中的值逐字符相等
- AND 该 model 被透传到 `AgentLoop`

#### Scenario: 非法模型被拒且不创建流

- WHEN 客户端发送 `{"content": "hi", "model": "deepseek-chat"}`（该 id 不在 `agent.chat.providers` 目录中）
- THEN 服务端返回 `400 Bad Request`，响应体 `error` 为 `invalid_model`
- AND 响应体 `requested` 为 `deepseek-chat`，`supported` 列出目录中全部合法 model id
- AND **不创建 stream**（响应中无 `stream_id`）
- AND 上游 Provider SHALL NOT 收到任何请求
