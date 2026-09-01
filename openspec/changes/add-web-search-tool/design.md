## Context

agent-demo 当前没有网络搜索能力：`tools/` 下只有本地工具（ReadFile/WriteFile/EditFile/Ls/Shell）。本项目复用 WebFlux `WebClient`（`McpClient` 已在跑）。参考 DeepSeek Harness（DSH）的网络搜索实现：DSH 用 DeepSeek 的 Anthropic 兼容 Messages API + 原生 `web_search` server tool，由服务端搜索并返回结构化 `web_search_tool_result` 块；同时 DSH 也支持独立检索端点（Exa/Perplexity 等）。本 change 给 agent-demo 加内置 `WebSearch` 工具，两条路径都做、可切换。

## Goals / Non-Goals

**Goals:**
- 内置 `WebSearch` 工具（`web_search(query)`），模型可调用，返回结构化结果（标题/URL/摘要/日期）注入上下文。
- 可插拔 provider：`WebSearchProvider` 接口 + `deepseek`（原生搜索）+ `tavily`（独立端点）两个实现，预留 `bing`/`serpapi` 扩展点。
- **provider 自动选择**：未显式配置时，识别为 `deepseek` 系模型自动启用 deepseek 原生搜索，其他模型回退 `tavily`；显式配置优先。
- 复用 `DEEPSEEK_API_KEY`（deepseek 路径不新增 key）；tavily 路径新增 `TAVILY_API_KEY`。
- 沿用项目 Fail-Closed：无 key / provider 失败时返回明确错误，不静默降级为空结果。

**Non-Goals:**
- 不做页面抓取（fetch）/正文提取。
- 不做搜索缓存 / 多轮搜索编排。
- 不引入第三方搜索 SDK（用现有 WebFlux WebClient + Jackson）。
- 不改 agent-web 模块。

## Decisions

### D1: `WebSearchProvider` 接口（可插拔）
```java
public interface WebSearchProvider {
    WebSearchResult search(String query, int maxResults, Duration timeout);
}
public record WebSearchResult(List<Source> sources, boolean truncated) {}
public record Source(String url, String title, String snippet, String publishedAt) {}
```
- 解析：接口 + record，provider 实现注入 config/key；不绑定 LLM 层。
- 考虑过直接继承 Tool 接口。否决：Tool 只管调用协议，检索编排（超时/截断/多 provider）应在 provider 层。

### D2: `DeepSeekWebSearchProvider`（原生搜索，复刻 DSH）
- 用 WebClient POST `{baseURL}/messages`（Anthropic 兼容），baseURL 默认 `https://api.deepseek.com/anthropic/v1`（**不**复用 LLM 的 chat-completions `https://api.deepseek.com`）。
- body 带 `model`、`max_tokens`、`tools:[{type:"web_search_20250305"}]`（原生 server tool）、系统提示 `Perform a web search for the query: <query>` + 最新用户消息。
- 解析响应中的 `web_search_tool_result` 块：`url←url`、`title←title`、`publishedAt←page_age`；`citations[]` 按 URL 关联 snippet。
- **严格模式**：响应不含 `web_search_tool_result` 块 → 抛错（不降级为文本抓取）。
- `maxUses` 限原生搜索次数；结果按 URL 去重，seam 用 `maxResults` 截断。
- 复用 `DEEPSEEK_API_KEY`（不新增密钥），Anthropic 兼容端点使用独立 `DEEPSEEK_SEARCH_BASE_URL` 环境变量回退。

### D3: `TavilyWebSearchProvider`（独立检索端点）
- WebClient POST `https://api.tavily.com/search`，body `{api_key, query, max_results}`。
- 解析 `results[]`：`title`、`url`、`content`（→ snippet）、`score`。
- key 用 `TAVILY_API_KEY`；结果数由 `maxResults` 控制。

### D4: provider 解析顺序（自动选择）
1. 显式配置 `search.provider` → 用配置的（`deepseek`/`tavily`）。
2. 未配置 → 按模型推断：`cfg.provider().type().equals("deepseek")` **或** 模型名以 `deepseek` 开头 → `deepseek`；否则 → `tavily`。
3. 均不可用 → 默认 `tavily`（保证工具可用，若也无 key 则 Fail-Closed）。

### D5: `WebSearchTool` 工具实现
- `implements Tool<String, String>`：`name()="web_search"`，`description()` 说明检索网络；`inputSchema()` 含 `query`（必填）+ `maxResults`（可选）。
- `parseArguments` 用 Jackson 解析 `{query,maxResults}` → 输入 record；`execute` 调选中的 provider，把 `WebSearchResult` 渲染为可读文本（标题 + URL + 摘要 + 日期）注入上下文。
- `checkPermissions` → `allow`（只读；外网请求由网络/沙箱环境约束）。
- `isReadOnly` → `true`。

### D6: Fail-Closed 失败策略
- 无 key（deepseek 无 key / tavily 无 `TAVILY_API_KEY`）→ 返回带指引的错误结果（`isError=true`）。
- provider 异常 / HTTP 失败 / 超时 → 错误结果，不抛未捕获异常（沿用 MCP `callTool` 容忍风格）。
- 严格模式不降级：DeepSeek 未返回结构化块时按失败处理，不吞成文本。

### D7: 配置与装配
- `AgentConfig.Search(String provider, int maxResults, int timeoutMs)` record，默认 `provider=""`（空→自动推断）/ `maxResults=5` / `timeoutMs=60000`。
- `ConfigLoader.mergeSearch` 解析 `search:` 段（yaml）；`AgentConfig.defaults()` 加默认 Search。
- `EnvKeys.TAVILY_API_KEY = "TAVILY_API_KEY"`；(可加 `DEEPSEEK_SEARCH_BASE_URL`)。
- `ToolRegistry`: 在 `buildTools` 里注册 `new WebSearchTool(...)`（provider 实例化 + config 注入）。CLI/web 共用同一装配。

### D8: 权限
- 只读、无本地副作用 → `allow`（`PermissionDecision.allow()`），不触发 ask。

## Risks / Trade-offs

- **DeepSeek 原生搜索 = 一次完整模型轮次**（延迟 + 生成 token 开销）→ 仅 `deepseek` 模型自动启用；`maxTokens=4096`、`maxUses=5`、结果 `maxResults` 截断；文档写明成本。
- **Anthropic 兼容端点与 LLM 基址不同** → 复用 `DEEPSEEK_API_KEY` 但**不**复用 `$DEEPSEEK_BASE_URL`，独立 `DEEPSEEK_SEARCH_BASE_URL` 回退，避免误用 chat-completions 基址。
- **key 缺失 / 网络失败** → Fail-Closed 明确错误；无 key 时工具可用但调用返回指引，不阻断 agent。
- **结果不可信** → 只解析结构化块（DeepSeek）/JSON（Tavily），**不吃生成文本**作为答案。
- **模型/provider 变化导致自动选择漂移** → 自动推断只在未显式配置时生效，显式配置稳定。

## Migration Plan

1. 新建 `agent-core/.../tools/websearch/`：`WebSearchProvider` 接口 + `WebSearchResult`/`Source` record + `DeepSeekWebSearchProvider` + `TavilyWebSearchProvider` + `WebSearchProviderFactory`（自动选择）。
2. 新建 `agent-core/.../tools/WebSearchTool.java`（`implements Tool<String,String>`）。
3. `AgentConfig` 加 `Search` record + `ConfigLoader.mergeSearch` + `EnvKeys.TAVILY_API_KEY` + `defaults()`。
4. `AgentLoopFactory.buildTools` 注册 `WebSearchTool`。
5. 写测试：`WebSearchProviderTest`（WireMock/mock DeepSeek Messages + Tavily 端点）、`WebSearchProviderFactoryTest`（自动选择）、`WebSearchToolTest`（协议 + 结果渲染）。
6. 文档：docs/design 补 WebSearch 简介；README 提及。
回滚：删 `tools/websearch/` + `WebSearchTool.java` + `AgentConfig.Search` 字段，1 个 commit revert 干净（局限 agent-core）。

## Open Questions

- WebSearch 是否**默认对所有会话启用**？—— 倾向默认启用（工具始终注册），无 key 时 Fail-Closed 返回指引；若担心误触发/成本，可加 `search.enabled` 开关。
- 是否需要给 web_search 加权限 ask（而非 allow）？—— 本设计定 `allow`（只读外网请求）；若后续要控制网络访问，可在 `PermissionPolicy` 层加。
