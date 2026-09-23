# web-ui — delta spec

## REMOVED Requirements

### Requirement: /model reasoning slash 命令

**Reason**: 该 Requirement 要求 `/model reasoning` 切到 `deepseek-reasoner`、`/model chat` 切回
`deepseek-chat`——两个 id 都不在 DeepSeek 官方模型列表中（前者是项目虚构、后者已被上游停用），
保留即保留死链。本 change 删除 `/model` 别名表，行为改由 `cli/spec.md §/model 别名向后兼容`
定义（未知模型名一律拒绝，提示列出合法 id）。

**Migration**: 用户改用完整 id（`/model deepseek-flash` 或 `/model deepseek-v4-pro`，可带
`provider/` 前缀）。别名不再提供替代品。

## MODIFIED Requirements

### Requirement: 前端 ReasoningEffortSelect 组件

`Composer` 状态栏 SHALL 在 `permission_mode` 旁边渲染 `ReasoningEffortSelect`，prop 为 `options: ReasoningEffort[]`（v0.1 为 `model: ModelEntry`，v0.2 升级）。`options` 为空数组时组件 SHALL 返回 `null`（从 DOM 移除）。组件本身 SHALL 与档位取值无关（完全由 `options` 驱动），档位集合的真源是 `agent.chat.providers`。

#### Scenario: options 渲染

- **WHEN** `options = [{id:"low",name:"Low"},{id:"high",name:"High"},{id:"max",name:"Max"}]`，`value = "high"`
- **THEN** trigger 显示 `思考 High`
- **AND** 打开后 3 个 `role=option` 文案分别为 `思考 Low` / `思考 High` / `思考 Max`

#### Scenario: 空 options 隐藏

- **WHEN** `options = []`
- **THEN** 组件返回 `null`，不占用布局空间

#### Scenario: 选中 effort 后回调

- **WHEN** 用户在下拉中选 `max`
- **THEN** `onChange("max")` 被调用

### Requirement: 前端 localStorage 模型持久化

App SHALL 在初始化时从 `localStorage["agent-demo:model-selection"]` 读取 `{provider, model, reasoningEffort?}`（v0.1 为 `{model, reasoningEffort}`；读到旧格式时 `provider` 为空串，SHALL 用 `inferProvider(model)` 推断）。ModelSelect / ReasoningEffortSelect 变更 SHALL 同步写回。

历史选择失效时的兜底 SHALL 取自服务端目录：`model` 不在目录中时回退到 `defaultModel`，
`reasoningEffort` 不在该 model 档位中时回退到该 model 的 `defaultReasoningEffort`（缺省才退回
档位数组首项）。SHALL NOT 硬编码任何模型 id 作为兜底值。

#### Scenario: 新格式 localStorage

- **WHEN** localStorage 存 `{provider:"deepseek", model:"deepseek-v4-pro", reasoningEffort:"max"}`
- **THEN** 读回后三字段一致

#### Scenario: 旧格式 localStorage 兼容

- **WHEN** localStorage 存 v0.1 格式 `{model:"deepseek-flash", reasoningEffort:"high"}`（无 provider）
- **THEN** 读回 `provider` 为空串，`model` / `reasoningEffort` 保留
- **AND** 调用方按 model 前缀推断 provider（`deepseek-` → `deepseek`）

#### Scenario: localStorage 数据无效(model 不在目录)

- **WHEN** localStorage 的 `model` 字段值不在 `/api/chat/models` 返回的目录里
- **THEN** 忽略该值，fallback 到服务端下发的 `defaultModel`
- **AND** 不抛错(优雅降级)

#### Scenario: 旧 id 从 localStorage 迁移

- **WHEN** localStorage 存 `model: "deepseek-reasoner"`（本 change 前的旧 id，已不在目录中）
- **THEN** 忽略该值，fallback 到 `defaultModel`（`deepseek-flash`）
- **AND** 规整后的 selection 被写回 localStorage

#### Scenario: localStorage reasoningEffort 不在模型 supported 列表

- **WHEN** 当前 model `supportsReasoning=true`，localStorage 的 `reasoningEffort` 不在该 model 档位中
- **THEN** 忽略该值，fallback 到该 model 的 `defaultReasoningEffort`
- **AND** `defaultReasoningEffort` 缺省时才退回档位数组第一个元素

#### Scenario: 损坏 JSON 不抛错

- **WHEN** localStorage 值为非法 JSON
- **THEN** `readModelSelection()` 返回 `null`

### Requirement: 后端 ModelsResponse.Model.reasoningEfforts

`GET /api/chat/models` SHALL 在嵌套结构的每个 model 上返回 `reasoningEfforts` 数组，元素为 `{id, name, description?}` 对象（v0.1 为 `string[]`，v0.2 升级为对象数组以携带显示名），并 SHALL 同时返回该 model 的 `defaultReasoningEffort`（未配置时缺省不输出）。数组根据模型能力动态返回:不支持 reasoning 的 model 返回 `[]`,支持者返回其配置档位。

#### Scenario: 当前目录配置

- **WHEN** `application-web.yml` 配置 `agent.chat.providers` 含 `deepseek-flash` 与
  `deepseek-v4-pro`（均 `supports-reasoning=true`、三档、`default-reasoning-effort: high`）
- **THEN** 响应中两个 model 的 `reasoningEfforts` 均为
  `[{id:"low",name:"Low"},{id:"high",name:"High"},{id:"max",name:"Max"}]`
- **AND** 两个 model 的 `defaultReasoningEffort` 均为 `"high"`

#### Scenario: 不支持 reasoning 的 model 返回空数组

- **WHEN** 某 model 配 `supports-reasoning: false` 且 `reasoning-efforts: []`
- **THEN** 响应中该 model 的 `reasoningEfforts` 为 `[]`
- **AND** 其 `defaultReasoningEffort` 缺省不输出

#### Scenario: description 为 null 时不输出字段

- **WHEN** 某档位未配置 `description`
- **THEN** 该档位 JSON 中不含 `description` 键（`@JsonInclude.NON_NULL`）

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

#### Scenario: 旧世代 id 一律 400

- WHEN 客户端发送 `{"content":"hi","model":"deepseek-v4-flash"}` / `"deepseek-reasoner"` /
  `"deepseek-chat"`（三者均不在当前目录中）
- THEN 三次均返回 `400 Bad Request`，`error` 为 `invalid_model`
- AND 响应 `supported` 数组恰为 `["deepseek-flash", "deepseek-v4-pro"]`
- AND 不创建任何 stream、不向上游发请求
