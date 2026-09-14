# fix-websearch-key-priority

## Why

`web_search` 工具在 web 场景下用 `cfg.provider().apiKey()` 作为 DeepSeek 鉴权凭据，与主对话 `ChatController.send` 的 key 优先级不同：主对话读 `DEEPSEEK_API_KEY` env > `agent.provider.api-key`（application-local.yml）> yaml，web_search 只读 yaml。结果：用户只在 application-local.yml 配 key 时，主对话能跑通，web_search 报 401 Unauthorized。

复现链：`/anthropic/v1/messages` 端点（DeepSeekWebSearchProvider line 32）使用 `cfg.provider().apiKey()`（WebSearchProviderFactory line 37）；`cfg` 在 WebRuntimeConfig line 38-45 由 ConfigLoader 加载，但 ConfigLoader 不读 Spring application-local.yml 的 `agent.provider.api-key`（它只读 `~/.agent-demo/config.yaml`）。

## What Changes

- `WebSearchProviderFactory.create(cfg, deepseekKey, tavilyKey, deepseekBaseUrl)`：新增 4 参重载接受显式 keys；保留 `create(cfg)` 重载向后兼容（CLI 场景行为不变）
- `AgentLoopFactory.buildTools(cfg, deepseekKey, tavilyKey, deepseekBaseUrl)`：新增 4 参重载；保留 `buildTools(cfg)` 重载
- `WebRuntimeConfig.webToolRegistry()`：传 env-merged keys 给 `buildTools(cfg, ...)`，与 `webLlmProvider()` 同 key 优先级（DEEPSEEK_API_KEY env > agent.provider.api-key > yaml）
- `WebSearchProviderFactoryTest`：5 个新用例（CLI / Web / key 来源优先级）
- `AgentLoopFactoryTest`：扩展断言 web_search tool 注册仍正常

## Impact

- 受影响模块：`agent-core/tools/websearch`、`agent-core/core/AgentLoopFactory`、`agent-web/config/WebRuntimeConfig`
- API/接口：不破坏向后兼容（新增重载，老调用方零改动）
- 数据/Schema：无
- 行为变化：web 场景下 web_search 与主对话共享 key 来源（修复 401）

## Out of Scope

- 不改 DeepSeek provider（OpenAiCompatibleProvider）的 key 读取
- 不改 Tavily provider 的 key 来源（仍读 `TAVILY_API_KEY` env，web 场景走 Spring env）
- 不新增 web_search baseUrl 配置（用户没要求；deepseek search base URL 走 `DEEPSEEK_SEARCH_BASE_URL` env，行为与 CLI 一致）