# search Specification

## Purpose
TBD - created by archiving change add-web-search-tool. Update Purpose after archive.
## Requirements
### Requirement: WebSearch 工具可调用

系统 SHALL 提供一个模型可调用的 `web_search` 工具，接收查询词（`query`）返回结构化检索结果（标题 / URL / 摘要 / 日期），并将结果注入模型上下文。

#### Scenario: 正常检索返回结构化结果

- **WHEN** 模型调用 `web_search` 且 `query` 非空
- **THEN** 工具返回若干条结构化来源（title / url / snippet / publishedAt），供模型引用

#### Scenario: 空查询返回错误

- **WHEN** 模型调用 `web_search` 但 `query` 为空或空白
- **THEN** 工具返回错误结果（isError=true），提示需提供查询词

### Requirement: DeepSeek 原生搜索 provider

系统 SHALL 在 `search.provider=deepseek` 时使用 DeepSeek Anthropic 兼容 Messages API（`POST {baseURL}/messages`）携带原生 `web_search_20250305` 服务器工具执行搜索，并解析返回的结构化 `web_search_tool_result` 块（url / title / page_age / citations）为来源结果。

#### Scenario: DeepSeek 返回结构化搜索块

- **WHEN** provider=deepseek 且 DeepSeek 返回含 `web_search_tool_result` 块的响应
- **THEN** 系统解析该块得到去重后的来源（url / title / date / snippet），且不把生成文本当作答案

#### Scenario: DeepSeek 未触发原生搜索

- **WHEN** provider=deepseek 但响应不含 `web_search_tool_result` 块
- **THEN** 系统按失败处理（严格模式），返回明确错误而非降级为文本抓取

### Requirement: Tavily 检索端点 provider

系统 SHALL 在 `search.provider=tavily` 时调用 Tavily 检索端点（`POST https://api.tavily.com/search`），解析返回的 `results[]`（title / url / content / score）为来源结果，结果数受 `maxResults` 限制。

#### Scenario: Tavily 返回结果

- **WHEN** provider=tavily 且 Tavily 返回 `results[]`
- **THEN** 系统把每条 result 映射为来源（title / url / content→snippet），并截断到 `maxResults`

#### Scenario: Tavily 无 key 或调用失败

- **WHEN** provider=tavily 但 `TAVILY_API_KEY` 缺失或 HTTP 失败
- **THEN** 系统返回带指引的错误结果，不抛未捕获异常

### Requirement: provider 自动选择

系统 SHALL 在未显式配置 `search.provider` 时按当前模型自动选择：识别为 `deepseek` 系模型自动启用 `deepseek` 原生搜索，其他模型回退 `tavily`；显式配置优先于自动推断。

#### Scenario: deepseek 模型自动用原生搜索

- **WHEN** 未配置 `search.provider` 且当前模型为 deepseek（provider.type 为 deepseek 或模型名以 deepseek 开头）
- **THEN** 系统选择 `deepseek` 原生搜索 provider

#### Scenario: 非 deepseek 模型回退 tavily

- **WHEN** 未配置 `search.provider` 且当前模型非 deepseek（如 minimax）
- **THEN** 系统选择 `tavily` provider

#### Scenario: 显式配置优先

- **WHEN** 显式配置了 `search.provider`（如 tavily）且当前模型为 deepseek
- **THEN** 系统使用配置的 provider，不按模型推断

### Requirement: 失败时 Fail-Closed

系统 SHALL 在搜索 provider 无凭据、调用失败或超时的情况下返回带有指引的错误结果（isError=true），不得静默返回空结果或抛未捕获异常。

#### Scenario: 无凭据

- **WHEN** 选中的 provider 缺少其 API key（deepseek 无 key / tavily 无 `TAVILY_API_KEY`）
- **THEN** 系统返回说明缺失凭据及处理指引的错误结果

#### Scenario: 网络/超时失败

- **WHEN** provider 调用超时或返回错误
- **THEN** 系统返回错误结果并保留原始错误信息，不影响 agent 主流程

