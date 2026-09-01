# agent-demo 内置 WebSearch 工具（add-web-search-tool）测试 —— 用例输出

> 所属批次：`2026-09-02-web-search`
> 类型：② 用例输出文档（单元测试用例）
> 对应测试设计：同目录 `test-design.md`（§4 用例矩阵，本文件用例编号与之对齐）
> 执行结果：同目录 `test-report.md`

---

## 1. 用例一览

本批 WebSearch 工具测试共 **27 条**用例（来源：`test-design.md` §4 用例矩阵），其中**新增 25 条**（5 个新测试类）与 **`ConfigLoaderTest` 扩展 2 条**。全部落地为 JUnit 5 单元测试；`AgentLoopFactoryTest` 扩展断言 `web_search` 注册（不单独计新用例）。

| TC 编号 | 名称 | 前置 | 关键步骤 | 预期 | 优先级 | 落地 |
|:-------:|------|------|---------|------|:------:|:----:|
| WS-01 | Provider 接口契约 | 反射读取 `WebSearchProvider` | 断言是 interface；`getMethod("search", String, int, Duration)` | 接口签名正确，返回 `WebSearchResult` | P0 | ✅ |
| WS-02 | Source record 字段暴露 | 构造 `Source` | 读四字段 | 与构造入参一致 | P1 | ✅ |
| WS-03 | WebSearchResult 包装 | 构造 `WebSearchResult` | 读 `sources`/`truncated` | sources=1、url 正确、truncated=true | P1 | ✅ |
| WS-04 | Provider 契约可 lambda 实现 | lambda 实现 provider | `search` | sources=0、truncated=false | P1 | ✅ |
| WS-05 | DeepSeek 解析结果块 | WireMock 返回 `web_search_tool_result` + `citations` | `search` → 映射 | 2 条来源；title/url/snippet/page_age 正确；truncated=false | P0 | ✅ |
| WS-06 | DeepSeek 按 url 去重 | WireMock 返回重复 url | `search` | sources=1、url 唯一 | P0 | ✅ |
| WS-07 | DeepSeek 严格模式 | WireMock 返回纯 text（无结果块） | `search` | 抛 `IllegalStateException`，消息含 `web_search_tool_result` | P0 | ✅ |
| WS-08 | DeepSeek 缺 key | `DeepSeekWebSearchProvider(null)` | `search` | 抛 `IllegalStateException` | P1 | ✅ |
| WS-09 | Tavily 解析 results | WireMock 返回 `results[]` | `search` | 2 条来源；title/url/snippet 正确；publishedAt 空；truncated=false | P0 | ✅ |
| WS-10 | Tavily 结果截断 | WireMock 返回 3 条、maxResults=2 | `search` | sources=2、truncated=true | P0 | ✅ |
| WS-11 | Tavily 缺 key | `TavilyWebSearchProvider(null)` | `search` | 抛 `IllegalStateException` | P1 | ✅ |
| WS-12 | Tavily HTTP 失败 | WireMock 返回 500 | `search` | 抛 `WebClientResponseException` | P1 | ✅ |
| WS-13 | deepseek 系模型选 deepseek | `AgentConfig.defaults()` | `create` | 返回 `DeepSeekWebSearchProvider` | P0 | ✅ |
| WS-14 | 非 deepseek 回退 tavily | type=minimax | `create` | 返回 `TavilyWebSearchProvider` | P0 | ✅ |
| WS-15 | 其他 type + deepseek 模型名 | type=openai、model=deepseek-v3 | `create` | 返回 `DeepSeekWebSearchProvider` | P1 | ✅ |
| WS-16 | 显式配置优先 | type=deepseek + `search.provider=tavily` | `create` | 返回 `TavilyWebSearchProvider` | P0 | ✅ |
| WS-17 | 显式 deepseek + 非 deepseek 模型 | type=minimax + `search.provider=deepseek` | `create` | 返回 `DeepSeekWebSearchProvider` | P1 | ✅ |
| WS-18 | 工具协议元数据 | lambda provider | 读 name/description/isReadOnly/category/checkPermissions | name=`web_search`；只读；category=READ；`allow()` | P0 | ✅ |
| WS-19 | input schema | lambda provider | 读 `inputSchema` | type=object；含 query 与 maxResults；required=[query] | P0 | ✅ |
| WS-20 | 解析 query + maxResults | lambda provider | `parseArguments` | query=天气、maxResults=3 | P1 | ✅ |
| WS-21 | 缺 maxResults 默认 null | lambda provider | `parseArguments` | query=天气、maxResults=null | P1 | ✅ |
| WS-22 | 空白 query 拒绝 | lambda provider | `parseArguments` 空白 query | 抛 `IllegalArgumentException` | P1 | ✅ |
| WS-23 | 渲染 sources | provider 返回 1 条来源 | `execute(...).block()` | 非 error；输出含 title/url/snippet/date | P0 | ✅ |
| WS-24 | provider 失败转 error | provider 抛异常 | `execute(...).block()` | isError=true；模型内容含「缺少 API key」 | P0 | ✅ |
| WS-25 | 空白 query 转 error | lambda provider | `execute` 空白 query | isError=true | P1 | ✅ |
| WS-26 | search 配置覆盖默认 | yaml `search.provider=tavily` 等 | `ConfigLoader.load` | provider=tavily、maxResults=3、timeoutMs=30000 | P1 | ✅ |
| WS-27 | 缺 search 配置保持默认 | yaml 无 search 段 | `ConfigLoader.load` | provider=""、maxResults=5、timeoutMs=60000 | P1 | ✅ |

> 落地列：✅ = 已实现为自动化单元测试用例（本批 27 条全部落地）。

---

## 2. 已落地用例的实现明细

### 2.1 `WebSearchProviderTest`（4 条，新建，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `providerIsInterfaceWithSearchMethod` | WS-01 | `WebSearchProvider.class.isInterface()`；`getMethod("search", String, int, Duration)` 返回 `WebSearchResult` |
| `sourceRecordExposesFields` | WS-02 | `Source` 四字段与构造入参一致 |
| `webSearchResultWrapsSourcesAndTruncatedFlag` | WS-03 | `sources().size()=1`、`get(0).url()` 正确、`truncated()=true` |
| `providerContractCanBeImplementedByLambda` | WS-04 | lambda 实现返回空结果，`sources().size()=0`、`truncated()=false` |

### 2.2 `DeepSeekWebSearchProviderTest`（4 条，新建，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `parsesWebSearchToolResultBlocks` | WS-05 | 2 条来源；title/url/snippet/page_age 正确；`truncated=false` |
| `dedupesByUrl` | WS-06 | `sources().size()=1`、url 唯一 |
| `throwsWhenNoWebSearchToolResultBlock` | WS-07 | `assertThrows(IllegalStateException)`，消息含 `web_search_tool_result` |
| `throwsWhenApiKeyMissing` | WS-08 | `assertThrows(IllegalStateException)` |

### 2.3 `TavilyWebSearchProviderTest`（4 条，新建，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `mapsResultsToSources` | WS-09 | 2 条来源；title/url/snippet 正确；`publishedAt=""`；`truncated=false` |
| `truncatesToMaxResults` | WS-10 | `sources().size()=2`、`truncated=true` |
| `throwsWhenApiKeyMissing` | WS-11 | `assertThrows(IllegalStateException)` |
| `throwsOnHttpFailure` | WS-12 | `assertThrows(WebClientResponseException)` |

### 2.4 `WebSearchProviderFactoryTest`（5 条，新建，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `deepseekModelChoosesDeepSeekProvider` | WS-13 | `assertInstanceOf(DeepSeekWebSearchProvider.class, ...)` |
| `nonDeepseekModelFallsBackToTavily` | WS-14 | `assertInstanceOf(TavilyWebSearchProvider.class, ...)` |
| `deepseekModelNameWithOtherTypeChoosesDeepSeek` | WS-15 | `assertInstanceOf(DeepSeekWebSearchProvider.class, ...)` |
| `explicitProviderWinsOverInference` | WS-16 | `assertInstanceOf(TavilyWebSearchProvider.class, ...)` |
| `explicitDeepSeekWithNonDeepseekModel` | WS-17 | `assertInstanceOf(DeepSeekWebSearchProvider.class, ...)` |

### 2.5 `WebSearchToolTest`（8 条，新建，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `exposesProtocolMetadata` | WS-18 | `name()="web_search"`；`isReadOnly=true`；`category()=READ`；`checkPermissions=allow()` |
| `inputSchemaHasRequiredQueryAndOptionalMaxResults` | WS-19 | `type="object"`；含 query 与 maxResults；`required=[query]` |
| `parseArgumentsExtractsQueryAndMaxResults` | WS-20 | query=天气、maxResults=3 |
| `parseArgumentsDefaultsMaxResultsWhenAbsent` | WS-21 | query=天气、maxResults=null |
| `parseArgumentsRejectsBlankQuery` | WS-22 | `assertThrows(IllegalArgumentException)` |
| `executeRendersSources` | WS-23 | 非 error；输出含 title/url/snippet/date |
| `executeReturnsErrorOnProviderFailure` | WS-24 | `isError=true`；`toModelContent()` 含「缺少 API key」 |
| `executeReturnsErrorOnBlankQuery` | WS-25 | `isError=true` |

### 2.6 `ConfigLoaderTest`（2 条，扩展，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `yamlSearchOverridesDefaults` | WS-26 | `search().provider()="tavily"`、`maxResults()=3`、`timeoutMs()=30000` |
| `yamlSearchMissingKeepsDefaults` | WS-27 | `search().provider()=""`、`maxResults()=5`、`timeoutMs()=60000` |

> 另有 `defaultsWhenNoFile` 已含 `search` 默认值断言（provider=""、maxResults=5、timeoutMs=60000），本批未重复计新用例。

### 2.7 `AgentLoopFactoryTest`（注册断言，扩展）

| 用例方法 | 断言要点 |
|---------|---------|
| `buildToolsRegistersExpectedTools` | `tools.getRaw("web_search")` 非空；`tools.list().size() >= 6` |

---

## 3. 用例实现要点

### 3.1 契约层（`WebSearchProviderTest`）

- 用反射锁定 `WebSearchProvider` 接口签名（`search(String, int, Duration)` → `WebSearchResult`），防止后续改动破坏契约。
- `Source` / `WebSearchResult` 用构造 + 访问器断言 record 字段语义。
- lambda 实现证明接口对轻量 provider 友好，`WebSearchToolTest` 大量复用该模式。

### 3.2 后端层（WireMock 桩）

| provider | 桩端点 | 关键 JSON 结构 | 隔离手段 |
|---------|--------|---------------|---------|
| DeepSeek | `POST /anthropic/v1/messages` | `content[]` 含 `web_search_tool_result.content[]`（url/title/page_age）+ `text.citations[]`（url/cited_text） | 随机端口 + 手写响应体；严格模式用纯 text 响应验证抛异常 |
| Tavily | `POST /search` | `results[]`（title/url/content/score） | 随机端口 + 3 条结果验证截断 + HTTP 500 验证异常 |

### 3.3 工厂层（`WebSearchProviderFactoryTest`）

- 用私有构造器 `withProvider(type, model)` / `withSearch(base, provider)` 在 `AgentConfig` 上拼装不同 provider/search 组合。
- `assertInstanceOf` 断言选出的具体实现类型，覆盖 5 条选择路径（推断 deepseek / 回退 tavily / 模型名推断 / 显式优先 / 显式 deepseek）。

### 3.4 工具层（`WebSearchToolTest`）

- 用 lambda provider + `ToolContext(Path.of("/tmp"), null, () -> false)` 全程无真实网络。
- 协议元数据、input schema、参数解析、渲染、Fail-Closed 错误五类行为均有断言。
- Fail-Closed 用 `assertTrue(r.isError())` + `toModelContent()` 含错误信息锁定「provider 异常被转成错误结果而非未捕获异常」。

---

## 4. 兼容性回归说明

本批新增用例覆盖 **WebSearch 新路径**（provider 契约 + 两个后端 + 工厂 + 工具 + 配置）。`AgentLoopFactory.buildTools` 新增 `web_search` 注册后，**既有 250 条 agent-core 测试**继续运行，验证工具注册数量、既有工具（Shell / Ls / ReadFile / WriteFile / EditFile 等）无冲突、无回归。

| 既有回归用例 | 关联点 |
|-------------|--------|
| `core/AgentLoopFactoryTest` | `buildToolsRegistersExpectedTools` 扩展到断言 `web_search` 注册 + 工具总数下限 |
| `tools/ToolRegistryTest` | 工具注册表行为（`getRaw` / `list`）不受新增工具影响 |
| `core/AgentLoopToolContextTest` 等 | 工具上下文与工具调用链路回归 |

> 本批全量执行结果为 `Tests run: 277, Failures: 0, Errors: 0, Skipped: 0`（250 既有 + 27 新增），详见 `test-report.md`。
