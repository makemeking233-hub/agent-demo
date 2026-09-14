# web-search Spec Deltas (fix-websearch-key-priority)

## ADDED Requirements

### Requirement: web 场景 web_search 与主对话共享 key 优先级

`web_search` 工具在 web 场景下 SHALL 使用与主对话（`ChatController.send`）一致的 DeepSeek API key 优先级：`DEEPSEEK_API_KEY` 环境变量 > `agent.provider.api-key` Spring property（`application-local.yml`）> `cfg.provider().apiKey()`（`~/.agent-demo/config.yaml`）。

#### Scenario: Web 场景用户在 application-local.yml 配 key 但 yaml 是占位符

- **WHEN** `~/.agent-demo/config.yaml` 的 `provider.apiKey` 是 `REPLACE_ME_WITH_YOUR_DEEPSEEK_KEY` 占位符，且 `application-local.yml` 配置了 `agent.provider.api-key: sk-real-...`，且 `DEEPSEEK_API_KEY` 环境变量未设置
- **THEN** web 场景调 `web_search` 应使用 `sk-real-...`（来自 application-local.yml），不应使用 yaml 占位符

#### Scenario: Web 场景用户设 DEEPSEEK_API_KEY 环境变量

- **WHEN** `DEEPSEEK_API_KEY=sk-env-...` 环境变量已设置，yaml 是占位符，application-local.yml 未配
- **THEN** web 场景调 `web_search` 应使用 `sk-env-...`，与主对话一致

#### Scenario: CLI 场景行为不变

- **WHEN** CLI 场景下 `cfg.provider().apiKey()` 已经被 `ConfigLoader.applyEnv()` 用 `DEEPSEEK_API_KEY` 环境变量覆盖
- **THEN** web_search 应使用覆盖后的 key；不引入新逻辑路径

### Requirement: WebSearchProviderFactory 支持显式 key 注入

`WebSearchProviderFactory.create(cfg, deepseekKey, tavilyKey, baseUrl)` SHALL 接受显式 keys：null 表示「沿用 cfg + 系统环境变量」；非 null 表示「覆盖」。原 `create(cfg)` 重载 SHALL 保持字节级向后兼容。

#### Scenario: 显式 key 覆盖 cfg

- **WHEN** 调用 `create(cfg, "sk-explicit", null, null)`
- **THEN** 生成的 `DeepSeekWebSearchProvider` 用 `"sk-explicit"` 作为 apiKey，不读 `cfg.provider().apiKey()`

#### Scenario: null 走默认路径

- **WHEN** 调用 `create(cfg, null, null, null)`
- **THEN** 行为与原 `create(cfg)` 完全一致（向下委托）

### Requirement: AgentLoopFactory.buildTools 支持显式 keys

`AgentLoopFactory.buildTools(cfg, deepseekKey, tavilyKey, baseUrl)` SHALL 把 keys 透传给 `WebSearchProviderFactory.create()`。原 `buildTools(cfg)` 重载 SHALL 保持字节级向后兼容。

#### Scenario: Web 场景 buildTools 注入 env-merged key

- **WHEN** WebRuntimeConfig 调 `buildTools(cfg, env-merged-key, env-tavily, env-base-url)`
- **THEN** 注册的 `WebSearchTool` 用 env-merged-key 调 DeepSeek，行为与主对话一致

#### Scenario: CLI 场景 buildTools(cfg) 不变

- **WHEN** CLI 调 `buildTools(cfg)`
- **THEN** 行为与原版完全一致（向下委托）