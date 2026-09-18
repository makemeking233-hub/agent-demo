## MODIFIED Requirements

### Requirement: /api/chat/models 端点

后端 SHALL 提供 `GET /api/chat/models` 端点。响应 MUST 为嵌套结构 `{"providers": [{"id": "...", "name": "...", "models": [{"id": "...", "name": "...", "supportsReasoning": true, "reasoningEfforts": [{"id":"low","name":"Low"}, ...]}]}]}`。每个 provider 至少含 `id / name / models[]`,每个 model 至少含 `id / name / supportsReasoning / reasoningEfforts[]`,每个 reasoningEffort 至少含 `id / name`（`description` 可选）。顶层 **不**再有平铺 `models` 字段。

#### Scenario: 列出 supported-providers

- **WHEN** 客户端发 `GET /api/chat/models`
- **THEN** 返回当前 `agent.chat.providers` 配置的所有 provider + 每个 provider 的所有 model + 每个 model 的 supportsReasoning + reasoningEfforts
- **AND** 数据从启动时构建的 `ModelCatalog` 单例读取

#### Scenario: trusted-host 鉴权

- **WHEN** 客户端源 IP 不在 trusted-hosts 白名单
- **THEN** 返回 `403 host_not_trusted`

### Requirement: 后端 ModelsResponse.Model.reasoningEfforts

`GET /api/chat/models` SHALL 在嵌套结构的每个 model 上返回 `reasoningEfforts` 数组，元素为 `{id, name, description?}` 对象（v0.1 为 `string[]`，v0.2 升级为对象数组以携带显示名）。数组根据模型能力动态返回:不支持 reasoning 的 model 返回 `[]`,支持者返回其配置档位。

#### Scenario: supported-models 配置默认

- **WHEN** `application-web.yml` 配置 `agent.chat.providers` 含 `deepseek-v4-flash`（supports-reasoning=false）与 `deepseek-reasoner`（supports-reasoning=true，3 档）
- **THEN** 响应中 `deepseek-v4-flash` 的 `reasoningEfforts` 为 `[]`
- **AND** `deepseek-reasoner` 的 `reasoningEfforts` 为 `[{id:"low",name:"Low"},{id:"medium",name:"Medium"},{id:"high",name:"High"}]`

#### Scenario: description 为 null 时不输出字段

- **WHEN** 某档位未配置 `description`
- **THEN** 该档位 JSON 中不含 `description` 键（`@JsonInclude.NON_NULL`）

### Requirement: 后端 SendRequest.reasoning_effort 透传

`POST /api/chat/send` SHALL 接受 `provider` 与 `reasoning_effort` 字段（`null`/缺省 = 不传），在 `ChatController` 调用 `ChatStreamService.create(...)` 时把 `provider` 传给 `AgentLoop.setProviderId`、`reasoning_effort` 传给 `AgentLoop.setReasoningEffort`。

#### Scenario: 客户端不传 reasoning_effort

- **WHEN** 客户端发 `{"content":"hi","model":"deepseek-reasoner"}`(无 `reasoning_effort` 字段)
- **THEN** `AgentLoop.reasoningEffort` 保持 `null`(后续由 Provider 内部 fallback)

#### Scenario: 客户端传 reasoning_effort=high

- **WHEN** 客户端发 `{"content":"hi","model":"deepseek-reasoner","reasoning_effort":"high"}`
- **THEN** `AgentLoop.reasoningEffort = "high"`
- **AND** `OpenAiCompatibleMapper` 把 `reasoning_effort: "high"` 写入请求 body(原硬编码 `medium` 改读 req)

#### Scenario: 客户端传 provider

- **WHEN** 客户端发 `{"content":"hi","provider":"deepseek","model":"deepseek-reasoner"}`
- **THEN** `AgentLoop.providerId = "deepseek"`
- **AND** `ChatRequest.extra["provider"] = "deepseek"`

#### Scenario: 客户端不传 provider 时从 model 推断

- **WHEN** 客户端发 `{"content":"hi","model":"o1"}`(无 provider)
- **THEN** 后端用 `ProviderInference.inferProvider("o1")` 推断为 `"openai"`
- **AND** `AgentLoop.providerId = "openai"`

#### Scenario: 推断失败时从 default-provider 兜底

- **WHEN** 客户端发 `{"content":"hi","model":"abab6.5s-chat"}`（该 model 在 supported-models 内但前缀无法推断）
- **THEN** 后端读 `agent.chat.default-provider`；未配置或为空时回退硬编码 `deepseek`

### Requirement: 后端 ProviderRequest.reasoningEffort 透传

`ProviderRequest` SHALL 含 `reasoningEffort: String` 字段(`null` = 不传)。`AgentLoop` SHALL 在每次构造 `ProviderRequest` 时把 volatile `reasoningEffort` 与 `providerId` 写入。`OpenAiCompatibleProvider` / `DeepSeekProvider` / `AnthropicProvider` SHALL 各自按规则把该字段映射到上游请求 body。

#### Scenario: OpenAI o1 透传 reasoning_effort

- **WHEN** `ProviderRequest.reasoningEffort = "high"` 且 model 为 `o1`
- **THEN** `OpenAiCompatibleMapper` 写 `reasoning_effort: "high"` 到 OpenAI 请求 body(原硬编码 `medium` 替换)

#### Scenario: Anthropic 折算 budget_tokens

- **WHEN** `ProviderRequest.reasoningEffort = "medium"` 且 model 为 `claude-opus-4-thinking`
- **THEN** `AnthropicProvider` 写 `thinking: {type: "enabled", budget_tokens: 4096}`(medium → 4096)

#### Scenario: DeepSeek 忽略 reasoningEffort

- **WHEN** `ProviderRequest.reasoningEffort = "high"` 且 model 为 `deepseek-reasoner`
- **THEN** `DeepSeekProvider` 不写任何 `reasoning_effort` 字段(DeepSeek 不接受该参数,reasoner 自动控制)
- **AND** 上游响应 reasoning_content 仍正常返回

#### Scenario: provider 字段写入 extra

- **WHEN** `AgentLoop.providerId = "openai"` 或 `reasoningEffort != null`
- **THEN** `ChatRequest.extra` 至少含 `provider` / `reasoning_effort` 之一
- **AND** 两者均为 null 时 `extra` 为 null（不产生空 map）

### Requirement: 前端 ModelSelect 组件

`TopBar` SHALL 渲染 `ModelSelect` 两层菜单组件，接受 `value: ModelSelection`（provider + model + reasoningEffort?）。trigger SHALL 显示「provider 名 · model 名」+ effort 徽标。点击 SHALL 打开两层菜单：左栏 provider 列表，右栏当前 provider 的 model 列表；支持 reasoning 的 model 行 SHALL 内联 effort 档位。选择 SHALL 更新完整 `ModelSelection`（下次 `send` 时生效，流中不切）。

#### Scenario: trigger 显示完整选择

- **WHEN** `value = {provider:"deepseek", model:"deepseek-reasoner", reasoningEffort:"high"}`
- **THEN** trigger 显示 provider 名 `DeepSeek` + model 名 `DeepSeek Reasoner` + effort 徽标 `High`

#### Scenario: 两层菜单打开

- **WHEN** 用户点击 trigger
- **THEN** 出现 `role=menu` 面板
- **AND** 左栏列出所有 provider；右栏仅列出当前高亮 provider 的 model

#### Scenario: 切换 provider 刷新右栏

- **WHEN** 用户在左栏点击另一个 provider
- **THEN** 右栏刷新为该 provider 的 model 列表
- **AND** 原 provider 的 model 不再出现

#### Scenario: 选中 model 触发 onChange

- **WHEN** 用户在右栏点击某个 model
- **THEN** `onChange({provider, model, reasoningEffort})` 被调用
- **AND** 切换到的 model 若支持 reasoning 且原 effort 仍在其档位内则保留原值，否则取第一档；不支持则 `reasoningEffort` 为 `undefined`

#### Scenario: effort 子档位联动

- **WHEN** 当前 model 支持 reasoning，用户点击其行下方的 effort chip
- **THEN** `onChange` 带上相同 provider/model + 新 effort

#### Scenario: 关闭路径

- **WHEN** 用户在面板外按下鼠标 / 按下 `Esc`
- **THEN** 面板关闭

#### Scenario: providers 为空时禁用

- **WHEN** `GET /api/chat/models` 返回空 `providers`
- **THEN** trigger 为 `disabled`

### Requirement: 前端 ReasoningEffortSelect 组件

`Composer` 状态栏 SHALL 在 `permission_mode` 旁边渲染 `ReasoningEffortSelect`，prop 为 `options: ReasoningEffort[]`（v0.1 为 `model: ModelEntry`，v0.2 升级）。`options` 为空数组时组件 SHALL 返回 `null`（从 DOM 移除）。

#### Scenario: options 渲染

- **WHEN** `options = [{id:"low",name:"Low"},{id:"medium",name:"Medium"},{id:"high",name:"High"}]`，`value = "medium"`
- **THEN** trigger 显示 `思考 Medium`
- **AND** 打开后 3 个 `role=option` 文案分别为 `思考 Low` / `思考 Medium` / `思考 High`

#### Scenario: 空 options 隐藏

- **WHEN** `options = []`
- **THEN** 组件返回 `null`，不占用布局空间

#### Scenario: 选中 effort 后回调

- **WHEN** 用户在下拉中选 `high`
- **THEN** `onChange("high")` 被调用

### Requirement: 前端 localStorage 模型持久化

App SHALL 在初始化时从 `localStorage["agent-demo:model-selection"]` 读取 `{provider, model, reasoningEffort?}`（v0.1 为 `{model, reasoningEffort}`；读到旧格式时 `provider` 为空串，SHALL 用 `inferProvider(model)` 推断）。ModelSelect / ReasoningEffortSelect 变更 SHALL 同步写回。

#### Scenario: 新格式 localStorage

- **WHEN** localStorage 存 `{provider:"deepseek", model:"deepseek-reasoner", reasoningEffort:"high"}`
- **THEN** 读回后三字段一致

#### Scenario: 旧格式 localStorage 兼容

- **WHEN** localStorage 存 v0.1 格式 `{model:"deepseek-chat", reasoningEffort:"medium"}`（无 provider）
- **THEN** 读回 `provider` 为空串，`model` / `reasoningEffort` 保留
- **AND** 调用方按 model 前缀推断 provider（`deepseek-` → `deepseek`）

#### Scenario: localStorage 数据无效(model 不在目录)

- **WHEN** localStorage 的 `model` 字段值不在 `/api/chat/models` 返回的目录里
- **THEN** 忽略该值，fallback 到 `deepseek-chat`，再退到第一个 provider 的第一个 model
- **AND** 不抛错(优雅降级)

#### Scenario: localStorage reasoningEffort 不在模型 supported 列表

- **WHEN** 当前 model `supportsReasoning=true`，localStorage 的 `reasoningEffort` 不在该 model 档位中
- **THEN** 忽略该值，fallback 到数组中第一个元素

#### Scenario: 损坏 JSON 不抛错

- **WHEN** localStorage 值为非法 JSON
- **THEN** `readModelSelection()` 返回 `null`

## ADDED Requirements

### Requirement: 后端 ModelCatalog 抽象启动时构建

`agent-web` 启动时 SHALL 由 `ProviderCatalogService` 从 `agent.chat.providers` yml 配置构造不可变 `ModelCatalog` 实例。`ModelCatalog` SHALL 支持 `providers() / provider(id) / model(providerId, modelId) / supportedEfforts(providerId, modelId)` 四个查询方法。

#### Scenario: 启动加载 yaml

- **WHEN** `application-web.yml` 含 `agent.chat.providers` 嵌套列表
- **THEN** Spring 启动时 `ProviderCatalogService` 解析并构造 `ModelCatalog`(不可变)

#### Scenario: 启动配置校验失败 fail-fast

- **WHEN** `agent.chat.providers` 配置中 `supports-reasoning=false` 但 `reasoning-efforts` 非空
- **THEN** Spring 启动失败,日志输出明确的字段路径错误
- **AND** 不进入 web 监听状态

### Requirement: 后端 AgentLoop.setProviderId 与 setSelection

`AgentLoop` SHALL 新增 volatile `providerId` 字段 + `setProviderId(String)` + `model()` getter + `setSelection(providerId, model, reasoningEffort)` 复合 setter。三者均只影响切换之后的新 `ChatRequest`。

#### Scenario: setSelection 同时切三字段

- **WHEN** 调用 `agentLoop.setSelection("openai", "o1", "high")`
- **THEN** `AgentLoop.providerId() == "openai"`、`model() == "o1"`、`reasoningEffort() == "high"`

#### Scenario: 旧调用方不传 provider

- **WHEN** 5 参 `ChatStreamService.create` 被调用（providerId 为 null）
- **THEN** `AgentLoop.providerId()` 保持 `null`（不改默认）

#### Scenario: blank providerId 视同 null

- **WHEN** `setProviderId("   ")` 经 6 参 `create` 传入
- **THEN** `AgentLoop.providerId()` 仍为 `null`

### Requirement: Provider 校验 providerId 一致性

多 provider 共存时，各 Provider SHALL 校验 `ChatRequest.extra["provider"]` 与自身 provider id 一致，不匹配时抛 `IllegalArgumentException`。`extra` 为 null 或不含 `provider` 字段时 SHALL 跳过校验（向后兼容 v0.1 调用方）。

#### Scenario: provider 匹配时放行

- **WHEN** `extra = {provider: "deepseek"}` 且调用 `DeepSeekProvider.streamChat`
- **THEN** 不抛异常

#### Scenario: provider 不匹配时抛错

- **WHEN** `extra = {provider: "anthropic"}` 且调用 `DeepSeekProvider.streamChat`
- **THEN** 抛 `IllegalArgumentException`，消息含期望与实际 provider id

#### Scenario: extra 为 null 时跳过校验

- **WHEN** `extra == null`
- **THEN** 不抛异常（v0.1 调用方兼容）

#### Scenario: extra 不含 provider 字段时跳过校验

- **WHEN** `extra = {reasoning_effort: "high"}`
- **THEN** 不抛异常

### Requirement: ProviderInference 模型名前缀推断

agent-core SHALL 提供 `ProviderInference.inferProvider(model)` 工具方法，按模型名前缀返回 provider id：`o1` / `o3` / `o4` / `gpt-` → `openai`；`claude-` → `anthropic`；`deepseek-` → `deepseek`；其他 / null → `null`。匹配 SHALL 大小写不敏感。

#### Scenario: 三大 provider 家族

- **WHEN** 传入 `o1-preview` / `claude-opus-4-20250514` / `deepseek-chat`
- **THEN** 分别返回 `openai` / `anthropic` / `deepseek`

#### Scenario: 大小写不敏感

- **WHEN** 传入 `DeepSeek-Chat` / `GPT-4o`
- **THEN** 分别返回 `deepseek` / `openai`

#### Scenario: 无法推断返回 null

- **WHEN** 传入 `abab6.5s-chat` / `gemini-pro` / `null` / `""`
- **THEN** 返回 `null`
