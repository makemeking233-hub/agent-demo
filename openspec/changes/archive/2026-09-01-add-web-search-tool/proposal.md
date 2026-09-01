## Why

agent-demo 目前没有网络搜索能力：内置工具只有 ReadFile/WriteFile/EditFile/Ls/Shell，`tools/` 下没有任何 web/search 相关工具，只能通过 Shell 执行 `curl` 或配置 MCP server 间接触网。补一个内置 `WebSearch` 工具，让模型能直接检索网络并拿到结构化结果注入上下文。

## What Changes

- 新增内置 `WebSearch` 工具（`web_search(query)`），模型可调用；返回结构化结果（标题/URL/摘要/日期）注入上下文。
- **双 provider 可配置**（`search.provider`）：
  - `deepseek`：复用 `DEEPSEEK_API_KEY`，走 DeepSeek Anthropic 兼容 `POST /anthropic/v1/messages` + 原生 `web_search_20250305` server tool，解析 `web_search_tool_result` 块（复刻 DSH `dsh-web-search-deepseek` 路径）。
  - `tavily`：独立检索端点 `POST /search`，解析 `results[]`（轻量，无生成开销）。
  - 预留 provider 扩展点，后续可加 `bing` / `serpapi`。
- **provider 自动选择**：未显式配置 `search.provider` 时按当前模型自动推断——`deepseek` 系模型（`cfg.provider().type()=="deepseek"` 或模型名以 `deepseek` 开头）自动启用 `deepseek` 原生搜索；其他模型（如 `minimax`）回退 `tavily`。显式配置优先于自动推断。
- 新增配置：`AgentConfig.Search`（`provider` / `maxResults` / `timeoutMs`）+ `EnvKeys.TAVILY_API_KEY`；`ConfigLoader` 解析 `search:` 段。
- 权限：只读，`checkPermissions` 返回 `allow`（网络请求本身由系统沙箱/网络环境约束）。
- 失败策略：沿用 Fail-Closed——无 key 或 provider 失败时该工具返回明确错误信息，不静默降级为空结果。

## Capabilities

### New Capabilities
- `web-search`：内置网络搜索工具（`WebSearchTool` + 可插拔 provider + 结构化结果注入上下文）。

### Modified Capabilities
- （无：`openspec/specs/` 下无既有 web-search spec；本次新增。）

## Impact

- `agent-core`: 新增 `tools/WebSearchTool.java` + `tools/websearch/`（`WebSearchProvider` 接口、`DeepSeekWebSearchProvider`、`TavilyWebSearchProvider`）+ `ToolRegistry` 注册。
- `agent-core/config`: `AgentConfig.Search` record + `ConfigLoader.mergeSearch` + `EnvKeys.TAVILY_API_KEY`。
- provider 用现有 WebFlux `WebClient`（与 `McpClient` 同款），不引入新依赖、不新增 API key 类型（`DEEPSEEK_API_KEY` 复用 / `TAVILY_API_KEY` 新增）。
- 测试：`WebSearchProviderTest`（mock DeepSeek Messages + Tavily 端点）、`WebSearchToolTest`（工具协议 + 结果映射）。
- 无破坏性 API 变更（新增模块，`AgentConfig` 加字段需同步 defaults/ConfigLoader）。
