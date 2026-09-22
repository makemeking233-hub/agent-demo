# provider-catalog Specification

## Purpose
TBD - created by archiving change add-provider-catalog-abstract. Update Purpose after archive.
## Requirements
### Requirement: ProviderGroup DTO

后端 SHALL 定义 `ProviderGroup` record,字段:`id: String`(如 `deepseek`)、`name: String`(如 `DeepSeek`)、`models: List<ModelEntry>`。由 Jackson `@JsonProperty` 序列化为 JSON。`ModelsResponse.providers` 数组元素类型即 `ProviderGroup`。

#### Scenario: ProviderGroup JSON 序列化

- **WHEN** `ProviderGroup(id="openai", name="OpenAI", models=[o1, o3-mini])` 序列化
- **THEN** 输出 `{"id":"openai","name":"OpenAI","models":[{"id":"o1",...},{"id":"o3-mini",...}]}`
- **AND** 字段名严格匹配 yaml 配置(`id` / `name` / `models`)

### Requirement: ModelEntry DTO

后端 SHALL 定义 `ModelEntry` record,字段:`id: String`、`name: String`、`supportsReasoning: boolean`、`reasoningEfforts: List<ReasoningEffort>`(空数组 = 不支持 reasoning effort 选项)。

#### Scenario: ModelEntry reasoningEfforts 数组空表示不支持

- **WHEN** `ModelEntry(id="deepseek-chat", supportsReasoning=false, reasoningEfforts=[])`
- **THEN** 序列化为 `{"id":"deepseek-chat","name":"DeepSeek Chat","supportsReasoning":false,"reasoningEfforts":[]}`
- **AND** 前端 ReasoningEffortSelect 接收此 model 时返回 null

### Requirement: ReasoningEffort DTO

后端 SHALL 定义 `ReasoningEffort` record,字段:`id: String`、`name: String`、`description: String?`(可选,默认 null)。对齐 dsh `ModelReasoningEffort` 形态。

#### Scenario: ReasoningEffort 三档固定形态

- **WHEN** provider `openai` 的 model `o1` 配置 `reasoning-efforts: [{id:low,name:Low}, {id:medium,name:Medium}, {id:high,name:High}]`
- **THEN** 序列化为 `reasoningEfforts:[{"id":"low","name":"Low"},{"id":"medium","name":"Medium"},{"id":"high","name":"High"}]`
- **AND** `description` 字段缺省时不输出(`@JsonInclude.NON_NULL`)

### Requirement: ModelCatalog 抽象

后端 SHALL 定义 `ModelCatalog` 不可变类,提供以下查询方法:
- `List<ProviderGroup> providers()`:返回所有 provider
- `Optional<ProviderGroup> provider(String id)`:按 id 查单个 provider
- `Optional<ModelEntry> model(String providerId, String modelId)`:按 provider+model 查单个 model
- `List<ReasoningEffort> supportedEfforts(String providerId, String modelId)`:查某 model 的 reasoningEfforts(缺省返回空列表)

`ModelCatalog` SHALL 由 `ProviderCatalogService` 启动时构造一次,之后不变。

#### Scenario: 启动加载后查询

- **WHEN** Spring 启动 + `ProviderCatalogService` 构造 `ModelCatalog`
- **THEN** 任意时点 `catalog.model("openai","o1")` 返回同一 `ModelEntry` 实例
- **AND** `catalog.model("nonexistent","x")` 返回 `Optional.empty()`

#### Scenario: 启动校验 supports-reasoning 与 reasoning-efforts 一致

- **WHEN** yml 配置 `supports-reasoning: false` 但 `reasoning-efforts` 非空
- **THEN** Spring 启动抛 `IllegalStateException`
- **AND** 错误消息含字段路径(如 `provider[openai].models[o1-mini].supports-reasoning=false 但 reasoning-efforts 非空`)

### Requirement: ProviderCatalogService 配置加载

`ProviderCatalogService` SHALL 实现 `ApplicationRunner`,Spring 启动时从 `Environment` 读 `agent.chat.providers` 配置并构造 `ModelCatalog` 单例 bean。

#### Scenario: yml providers 配置加载

- **WHEN** `application-web.yml` 含 `agent.chat.providers: [{id:deepseek, name:DeepSeek, models:[...]}, ...]`
- **THEN** 启动完成后 `modelCatalog` bean 含所有 provider
- **AND** 启动时间 < 100ms(无 I/O)

#### Scenario: 旧 key supported-models 兼容

- **WHEN** yml 同时含 `agent.chat.supported-models: [deepseek-chat, deepseek-reasoner]`(change A 旧 key)但 `agent.chat.providers` 缺省
- **THEN** 启动时从 `supported-models` 列表平铺转换为单 provider `deepseek`(默认)
- **AND** 标记 `@Deprecated` 的 fallback 日志输出一次

### Requirement: 默认 provider/model 推断

后端 SHALL 支持从 `model` 名称推断 `provider`(`o1` / `gpt-` → `openai`、`claude-` → `anthropic`、`deepseek-` → `deepseek`),并从 `agent.chat.default-provider` / `agent.chat.default-model` yml 配置兜底。

#### Scenario: 从 model 推断 provider

- **WHEN** 客户端发 `{"model":"o1"}`(无 provider)
- **THEN** 后端推断 `provider = "openai"`
- **AND** `AgentLoop.provider = "openai"`

#### Scenario: 推断失败 fallback 到 default-provider

- **WHEN** 客户端发 `{"model":"unknown-future-model"}`(无法推断)
- **THEN** `AgentLoop.provider = "deepseek"`(从 yml `default-provider` 配置)
- **AND** 警告日志输出 "无法从 model 推断 provider, 使用 default-provider=deepseek"

### Requirement: ChatStreamService 透传 provider

`ChatStreamService.create(sessionId, provider, model, mode, workspace, reasoningEffort)` SHALL 新增重载,把 provider + model + reasoningEffort 三参数都传给 `WebAgentRuntime`。`ChatController.send` SHALL 解析 `req.provider()` 后调用此重载。

#### Scenario: send 同时传三参数

- **WHEN** `SendRequest = {provider:"openai", model:"o1", reasoningEffort:"high"}`
- **THEN** `ChatStreamService.create` 调用 `WebAgentRuntime.create(..., "openai", "o1", "high")`
- **AND** `WebAgentRuntime` 调 `agentLoop.setSelection(new ModelSelection("openai","o1","high"))`

