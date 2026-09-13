## ADDED Requirements

### Requirement: 前端 ModelSelect 组件

`TopBar` SHALL 在右上角渲染 `ModelSelect` 组件,展示当前会话使用的模型名(如 `DeepSeek Chat` / `DeepSeek Reasoner`)。点击下拉框 SHALL 弹出从 `GET /api/chat/models` 拉取的模型列表,选择某项 SHALL 更新当前会话的 `model` 字段(下次 `send` 时生效,流中不切)。

#### Scenario: TopBar 显示当前 model

- **WHEN** ChatPanel 初始化并从 localStorage 读到 `model=deepseek-reasoner`
- **THEN** TopBar 的 ModelSelect trigger 按钮显示 "DeepSeek Reasoner"
- **AND** 该按钮 aria-label 为 "选择模型"

#### Scenario: 点击下拉弹出模型列表

- **WHEN** 用户点击 ModelSelect trigger
- **THEN** 弹出列表显示 `ModelsResponse.models[]` 中所有项
- **AND** 当前选中项前显示 ✓ 标记

#### Scenario: 选中后 trigger 更新

- **WHEN** 用户在下拉列表中点选 `deepseek-chat`
- **THEN** trigger 按钮文字变为 "DeepSeek Chat"
- **AND** localStorage `agent-demo:model-selection` 的 `model` 字段更新为 `deepseek-chat`
- **AND** 下次 `send` 时 `SendRequest.model = "deepseek-chat"`

#### Scenario: 键盘 ↑↓ Enter Esc 导航

- **WHEN** 下拉打开且当前焦点在 trigger
- **THEN** 按 `↓` 移到下一项,`↑` 移到上一项,`Enter` 选中,`Esc` 关闭下拉
- **AND** 焦点环可见(无障碍)

### Requirement: 前端 ReasoningEffortSelect 组件

`Composer` 状态栏 SHALL 在 `permission_mode` 旁边渲染 `ReasoningEffortSelect` 下拉框,选项为 `["low", "medium", "high"]` 三档,仅当当前 model 的 `supportsReasoning=true` 时启用,否则隐藏。

#### Scenario: 当前 model 支持 reasoning 时下拉可见

- **WHEN** ChatPanel 当前 model = `deepseek-reasoner`(`supportsReasoning=true`)
- **THEN** Composer 状态栏显示 ReasoningEffortSelect 下拉,默认选中 `medium`
- **AND** 右侧显示 "下次发送生效" 提示文字

#### Scenario: 当前 model 不支持 reasoning 时下拉隐藏

- **WHEN** ChatPanel 当前 model = `deepseek-chat`(`supportsReasoning=false`)
- **THEN** Composer 状态栏**不**显示 ReasoningEffortSelect(从 DOM 移除)
- **AND** 不占用布局空间

#### Scenario: 选中 effort 后持久化

- **WHEN** 用户在下拉中选 `high`
- **THEN** localStorage `agent-demo:model-selection` 的 `reasoningEffort` 字段更新为 `high`
- **AND** 下次 `send` 时 `SendRequest.reasoning_effort = "high"`

### Requirement: 前端 localStorage 模型持久化

ChatPanel SHALL 在初始化时从 `localStorage["agent-demo:model-selection"]` 读取 `{model, reasoningEffort}`,缺省时使用 server default(`deepseek-chat` + `medium`)。ModelSelect / ReasoningEffortSelect 变更 SHALL 同步写回 localStorage。

#### Scenario: 首次访问(localStorage 空)

- **WHEN** 用户首次打开 Web UI,localStorage 无 `agent-demo:model-selection` 键
- **THEN** 当前 model 默认为 `deepseek-chat`,reasoningEffort 默认 `medium`
- **AND** 首次下拉变更后立即写入 localStorage

#### Scenario: localStorage 数据无效(model 不在 supported 列表)

- **WHEN** localStorage 的 `model` 字段值为 `gpt-5`(不在 `/api/chat/models` 返回的列表)
- **THEN** 忽略该值,fallback 到 server default `deepseek-chat`
- **AND** 不抛错(优雅降级)

#### Scenario: localStorage reasoningEffort 不在模型 supported 列表

- **WHEN** 当前 model `supportsReasoning=true`,localStorage 的 `reasoningEffort=low` 不在 `model.reasoningEfforts` 中
- **THEN** 忽略该值,fallback 到数组中第一个元素(默认 `low`)

### Requirement: 后端 SendRequest.reasoning_effort 透传

`POST /api/chat/send` SHALL 接受 `reasoning_effort` 字段(`null`/缺省 = 不传,使用 server default),并在 `ChatController` 调用 `ChatStreamService.create(...)` 时把该值传给 `WebAgentRuntime`,最终写入 `AgentLoop.reasoningEffort` volatile 字段。

#### Scenario: 客户端不传 reasoning_effort

- **WHEN** 客户端发 `{"content":"hi","model":"deepseek-reasoner"}`(无 `reasoning_effort` 字段)
- **THEN** `AgentLoop.reasoningEffort` 保持 `null`(后续由 Provider 内部 fallback)
- **AND** ProviderRequest.reasoningEffort = `null`

#### Scenario: 客户端传 reasoning_effort=high

- **WHEN** 客户端发 `{"content":"hi","model":"deepseek-reasoner","reasoning_effort":"high"}`
- **THEN** `AgentLoop.reasoningEffort = "high"`
- **AND** ProviderRequest.reasoningEffort = `"high"`
- **AND** `OpenAiCompatibleMapper` 把 `reasoning_effort: "high"` 写入请求 body(原硬编码 `medium` 改读 req)

### Requirement: 后端 ModelsResponse.Model.reasoningEfforts

`GET /api/chat/models` SHALL 返回 `{"models":[{"id":"...","name":"...","supportsReasoning":true,"reasoningEfforts":["low","medium","high"]}]}`,其中 `reasoningEfforts` 数组根据模型能力动态返回:`deepseek-chat` 返回 `[]`,`deepseek-reasoner` 返回 `["low","medium","high"]`,OpenAI o1/o3/o4 返回 `["low","medium","high"]`,Anthropic claude-4 thinking 返回 `["low","medium","high"]`。

#### Scenario: supported-models 配置默认

- **WHEN** `application-web.yml` 配置 `agent.chat.supported-models: [deepseek-chat, deepseek-reasoner]`
- **THEN** `/api/chat/models` 返回 `[{id:"deepseek-chat", supportsReasoning:false, reasoningEfforts:[]}, {id:"deepseek-reasoner", supportsReasoning:true, reasoningEfforts:["low","medium","high"]}]`

### Requirement: 后端 ProviderRequest.reasoningEffort 透传

`ProviderRequest` SHALL 新增 `reasoningEffort: String` 字段(`null` = 不传)。`AgentLoop` SHALL 在每次构造 `ProviderRequest` 时把 volatile `reasoningEffort` 写入该字段。`OpenAiCompatibleProvider` / `DeepSeekProvider` / `AnthropicProvider` SHALL 各自按规则把该字段映射到上游请求 body。

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