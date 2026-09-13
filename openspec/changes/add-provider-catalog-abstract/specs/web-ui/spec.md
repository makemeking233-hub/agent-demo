## ADDED Requirements

### Requirement: 后端 ModelsResponse 提供分层目录

`ModelsResponse` SHALL 重写为嵌套结构:顶层为 `providers: ProviderGroup[]`,每个 `ProviderGroup` 含 `id / name / models[]`,每个 `ModelEntry` 含 `id / name / supportsReasoning / reasoningEfforts: ReasoningEffort[]`,每个 `ReasoningEffort` 含 `id / name / description?`。`ModelsController.list()` SHALL 返回 `catalog.providers()`,前端两层菜单按此结构渲染。

#### Scenario: 三 provider 配置默认目录

- **WHEN** `application-web.yml` 配置 `agent.chat.providers: [deepseek, openai, anthropic]` 三个 provider,各自带 models
- **THEN** `GET /api/chat/models` 返回 `{"providers":[{id:"deepseek", name:"DeepSeek", models:[...]}, {id:"openai", name:"OpenAI", models:[...]}, {id:"anthropic", name:"Anthropic", models:[...]}]}`
- **AND** 顶层 **不**再有平铺 `models` 字段

#### Scenario: ModelEntry reasoningEfforts 嵌套对象数组

- **WHEN** provider `openai` 的 model `o1` 在 yaml 配置 `reasoning-efforts: [{id:low, name:Low}, {id:medium, name:Medium}, {id:high, name:High}]`
- **THEN** 响应中 `providers[openai].models[o1].reasoningEfforts` 是 `[{id:"low",name:"Low"},{id:"medium",name:"Medium"},{id:"high",name:"High"}]`

### Requirement: 后端 ModelCatalog 抽象启动时构建

`agent-web` 启动时 SHALL 由 `ProviderCatalogService` 从 `agent.chat.providers` yml 配置构造不可变 `ModelCatalog` 实例。`ModelCatalog` SHALL 支持 `providers() / provider(id) / model(providerId, modelId) / supportedEfforts(providerId, modelId)` 四个查询方法。

#### Scenario: 启动加载 yaml

- **WHEN** `application-web.yml` 含 `agent.chat.providers` 嵌套列表
- **THEN** Spring 启动时 `ProviderCatalogService` 解析并构造 `ModelCatalog`(不可变)
- **AND** 任意时点 `modelsController.list()` 调用 `catalog.providers()` 返回相同实例

#### Scenario: 启动配置校验失败 fail-fast

- **WHEN** `agent.chat.providers` 配置中 `supports-reasoning=false` 但 `reasoning-efforts` 非空
- **THEN** Spring 启动失败,日志输出明确的字段路径错误(如 `provider[openai].models[o1-mini].supports-reasoning=false 但 reasoning-efforts 非空`)
- **AND** 不进入 web 监听状态

### Requirement: 后端 ProviderRequest.provider + AgentLoop.setProvider

`ProviderRequest` SHALL 新增 `provider: String` 字段。`AgentLoop` SHALL 新增 volatile `provider` 字段 + `setProvider(String)` 方法 + `setSelection(ModelSelection)` 复合方法。`ProviderRequest` SHALL 由 `provider + model + reasoningEffort` 三 volatile 字段组装而成。

#### Scenario: setSelection 同时切 provider + model + effort

- **WHEN** 调用 `agentLoop.setSelection(new ModelSelection("openai","o1","high"))`
- **THEN** `AgentLoop.provider = "openai"`、`AgentLoop.model = "o1"`、`AgentLoop.reasoningEffort = "high"`
- **AND** 下次构造 `ProviderRequest` 时三字段都正确填充

#### Scenario: 旧调用方不传 provider 时从 model 推断

- **WHEN** 客户端发 `{"model":"o1"}`(无 provider 字段)
- **THEN** 后端从 `model` 名称推断 provider:`o1` / `gpt-` → `openai`、`claude-` → `anthropic`、`deepseek-` → `deepseek`
- **AND** `AgentLoop.provider` 设为推断结果

### Requirement: 三 Provider 接受 provider 参数

`DeepSeekProvider` / `OpenAiCompatibleProvider` / `AnthropicProvider` SHALL 接受 `ProviderRequest.provider` 用于内部 baseURL 路由决策(虽然 v0.1 多数情况下 provider == model 前缀,但接口完整性需保证)。

#### Scenario: DeepSeek provider 路由

- **WHEN** `ProviderRequest.provider = "deepseek"` + `model = "deepseek-reasoner"`
- **THEN** `DeepSeekProvider` 用 `https://api.deepseek.com` baseURL,`Authorization: Bearer DEEPSEEK_API_KEY`

#### Scenario: OpenAI provider 路由

- **WHEN** `ProviderRequest.provider = "openai"` + `model = "o1"`
- **THEN** `OpenAiCompatibleProvider` 用 `https://api.openai.com` baseURL,`Authorization: Bearer OPENAI_API_KEY`

#### Scenario: Anthropic provider 路由

- **WHEN** `ProviderRequest.provider = "anthropic"` + `model = "claude-opus-4-7-thinking"`
- **THEN** `AnthropicProvider` 用 `https://api.anthropic.com` baseURL + `x-api-key: ANTHROPIC_API_KEY` + `anthropic-version: 2023-06-01`

### Requirement: 前端 ModelSelect 两层菜单

前端 `ModelSelect` SHALL 升级为两层菜单:trigger 按钮点击 → 第一层面板(左)列 provider 列表 → 选中 provider 后右侧面板(右)列该 provider 的 models,每个 model 可展开 `ReasoningEffortSelect`(当 `supportsReasoning=true && reasoningEfforts.length > 0`)。选中 model SHALL 同时触发 `provider + model + reasoningEffort` 三字段更新。

#### Scenario: 触发器显示当前选择

- **WHEN** 当前 `ModelSelection = {provider:"openai", model:"o1", reasoningEffort:"high"}`
- **THEN** trigger 按钮显示 "o1"(仅 model 名,不带 provider)
- **AND** 按钮右侧有 "OpenAI · high" 灰色提示(完整上下文)

#### Scenario: 两层菜单打开

- **WHEN** 用户点击 trigger 按钮
- **THEN** 弹出面板宽 480px,左 1/3 显示 providers 列表(当前 provider 高亮),右 2/3 显示该 provider 的 models(当前 model 高亮)

#### Scenario: 切换 provider 刷新右面板

- **WHEN** 用户在左面板点选 "OpenAI"
- **THEN** 右面板内容切换为 OpenAI 的 models(`gpt-4o` / `o1` / `o3-mini`)
- **AND** 自动聚焦到第一个 model

#### Scenario: 选中 model 同时切 reasoningEffort 默认值

- **WHEN** 用户在右面板点选 `o3-mini`(`supportsReasoning=true`, `reasoningEfforts=[low, medium, high]`)
- **THEN** ModelSelect onChange 触发 `{provider:"openai", model:"o3-mini", reasoningEffort:"medium"}`(默认中位档)
- **AND** 关闭弹层

#### Scenario: 嵌套 ReasoningEffortSelect 联动

- **WHEN** 在两层菜单中展开 model `o1` 的 effort 子下拉
- **THEN** 显示 `Low / Medium / High` 三档(从 `o1.reasoningEfforts` 渲染)
- **AND** 选某档时 onChange 触发 `{provider:"openai", model:"o1", reasoningEffort:"<新值>"}`,不关闭弹层

### Requirement: 前端 ReasoningEffortSelect prop 形态升级

`ReasoningEffortSelect` SHALL 接受 `options: ReasoningEffort[]` 参数(替代 change A 的 `model: Model`),从 `options` 渲染。仍支持 `options.length === 0` 时返回 `null`。

#### Scenario: options 渲染

- **WHEN** `options = [{id:"low",name:"Low"},{id:"medium",name:"Medium"},{id:"high",name:"High"}]` + `value = "medium"`
- **THEN** 下拉显示 "Medium"(value 对应 label)
- **AND** 列表项按 options 顺序渲染

#### Scenario: 空 options 隐藏

- **WHEN** `options = []`(model 不支持 reasoning)
- **THEN** 组件返回 `null`,从 DOM 移除

### Requirement: 前端 localStorage ModelSelection schema

`localStorage["agent-demo:model-selection"]` SHALL 存储完整 `ModelSelection = {provider, model, reasoningEffort?}` 三字段。读 change A 旧格式 `{model, reasoningEffort}`(无 provider)时 SHALL 从 `default-provider` yaml 配置兜底。

#### Scenario: 新格式 localStorage

- **WHEN** localStorage 值为 `{"provider":"openai","model":"o1","reasoningEffort":"high"}`
- **THEN** 初始化 `ModelSelection = {provider:"openai", model:"o1", reasoningEffort:"high"}`
- **AND** TopBar ModelSelect trigger 显示 "o1 · OpenAI · high"

#### Scenario: 旧格式 localStorage 兼容

- **WHEN** localStorage 值为 `{"model":"deepseek-chat","reasoningEffort":"medium"}`(change A 旧格式,无 provider)
- **THEN** 推断 provider:`deepseek-chat` → `deepseek`
- **AND** 初始化 `ModelSelection = {provider:"deepseek", model:"deepseek-chat", reasoningEffort:"medium"}`

#### Scenario: 校验失败 fallback

- **WHEN** localStorage provider 值为 `nonexistent`(不在 catalog)
- **THEN** fallback 到 `default-provider` yaml 配置 + `default-model` 配置 + `medium`

## MODIFIED Requirements

### Requirement: /api/chat/models 端点(嵌套结构升级)

后端 SHALL 提供 `GET /api/chat/models` 端点。响应 MUST 包含嵌套结构,返回 `{"providers": [{"id": "...", "name": "...", "models": [{"id": "...", "name": "...", "supportsReasoning": true, "reasoningEfforts": [{"id":"low","name":"Low","description":"..."}, ...]}]}]}`。每个 provider 至少含 `id / name / models[]`,每个 model 至少含 `id / name / supportsReasoning / reasoningEfforts[]`,每个 reasoningEffort 至少含 `id / name`。此 requirement 升级自 change `add-models-dropdown-v0` 的平铺 `models[]` 结构。

#### Scenario: 列出 supported-providers

- **WHEN** 客户端发 `GET /api/chat/models`
- **THEN** 返回当前 `agent.chat.providers` 配置的所有 provider + 每个 provider 的所有 model + 每个 model 的 supportsReasoning + reasoningEfforts
- **AND** 数据从启动时构建的 `ModelCatalog` 单例读取

#### Scenario: trusted-host 鉴权

- **WHEN** 客户端源 IP 不在 trusted-hosts 白名单
- **THEN** 返回 `403 host_not_trusted`

### Requirement: 后端 SendRequest 接受 provider

`POST /api/chat/send` SHALL 接受 `provider: String` 字段(`null`/缺省时从 `model` 名称推断或从 `default-provider` 兜底),与 `model` + `reasoning_effort` 三个字段一起透传到 `AgentLoop` 三 volatile 字段。

#### Scenario: send 同时传 provider/model/reasoning_effort

- **WHEN** 客户端发 `{"content":"hi","provider":"openai","model":"o1","reasoning_effort":"high"}`
- **THEN** `AgentLoop.provider = "openai"` + `AgentLoop.model = "o1"` + `AgentLoop.reasoningEffort = "high"`
- **AND** `ProviderRequest = {provider:"openai", model:"o1", reasoningEffort:"high", ...}`

#### Scenario: send 只传 model

- **WHEN** 客户端发 `{"content":"hi","model":"o1"}`(无 provider)
- **THEN** `AgentLoop.provider` 从 model 推断为 `"openai"`
- **AND** 推断逻辑:`o1` / `gpt-` → `openai`、`claude-` → `anthropic`、`deepseek-` → `deepseek`