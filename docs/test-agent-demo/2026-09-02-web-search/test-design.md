# agent-demo 内置 WebSearch 工具（add-web-search-tool）测试设计

> 测试对象：add-web-search-tool——`agent-core/src/main/java/com/example/agent/tools/websearch/`（`WebSearchProvider` 接口 + `WebSearchResult`/`Source` record + `DeepSeekWebSearchProvider` + `TavilyWebSearchProvider` + `WebSearchProviderFactory`）+ `tools/WebSearchTool.java` + `AgentConfig.Search`/`ConfigLoader.mergeSearch`/`EnvKeys` + `AgentLoopFactory.buildTools` 注册
> 测试命令：`mvn -pl agent-core test`（全量用例）/ `mvn -pl agent-core clean verify`（含 jacoco 覆盖率门禁）
> 测试类型：单元测试（JUnit 5 + WireMock 桩 + `@TempDir` 临时目录）
> 批次目录：`docs/test-agent-demo/2026-09-02-web-search/`
> 状态：add-web-search-tool 变更收尾测试（对应 `openspec/changes/add-web-search-tool/`）
> 输出语言：中文

---

## 1. 测试范围与目标

### 1.1 被测系统

add-web-search-tool 为 `agent-core` 引入一个**内置联网搜索工具** `web_search`，通过可插拔的 `WebSearchProvider` 抽象把「DeepSeek 原生搜索」与「Tavily 检索端点」两种后端收敛为统一的结构化结果。

| 层 | 组件 | 说明 |
|----|------|------|
| 抽象契约 | `tools/websearch/WebSearchProvider.java` | 搜索 provider 接口：`search(query, maxResults, timeout)` 返回 `WebSearchResult`，失败（无 key / HTTP 失败 / 超时 / 无结构化结果）抛 `IllegalStateException` |
| 抽象契约 | `tools/websearch/WebSearchResult.java` | 结果 record：`sources`（按 url 去重）+ `truncated`（是否被截断） |
| 抽象契约 | `tools/websearch/Source.java` | 单条来源 record：`url` / `title` / `snippet` / `publishedAt`（后三者可为空） |
| 后端实现 | `tools/websearch/DeepSeekWebSearchProvider.java` | DeepSeek 原生搜索：POST Anthropic 兼容 `/anthropic/v1/messages`，携带原生 `web_search_20250305` server tool；严格模式（无 `web_search_tool_result` 块即抛异常）；按 url 去重；复用 `DEEPSEEK_API_KEY` |
| 后端实现 | `tools/websearch/TavilyWebSearchProvider.java` | Tavily 检索端点：POST `https://api.tavily.com/search`，body `{api_key, query, max_results}`，解析 `results[]`；按 `maxResults` 截断；`TAVILY_API_KEY` |
| 工厂 | `tools/websearch/WebSearchProviderFactory.java` | 按配置选择 provider：显式 `search.provider` 优先；未配置时按模型推断（type=deepseek 或模型名以 deepseek 开头 → deepseek，否则 tavily） |
| 工具接入 | `tools/WebSearchTool.java` | 把选中的 provider 暴露为模型可调用的 `web_search` 工具（只读、`checkPermissions=allow`、Fail-Closed） |
| 配置接入 | `AgentConfig.Search` / `ConfigLoader.mergeSearch` / `EnvKeys` | `search` 配置段（provider / maxResults / timeoutMs）加载与默认值合并；`DEEPSEEK_SEARCH_BASE_URL` / `TAVILY_API_KEY` 环境变量键 |
| 注册接入 | `AgentLoopFactory.buildTools` | 在工具注册表中注册 `web_search` |

### 1.2 测试目标

1. **契约**：`WebSearchProvider` 接口签名正确（`search` 方法返回 `WebSearchResult`），`Source`/`WebSearchResult` record 字段暴露正确，接口可用 lambda 实现。
2. **DeepSeek 后端**：正确解析 `web_search_tool_result` + `citations` 关联 snippet；按 url 去重；严格模式下无结果块抛异常；缺 key 抛异常。
3. **Tavily 后端**：正确解析 `results[]` 到 sources；结果截断 + `truncated` 标志；缺 key 抛异常；HTTP 500 抛异常。
4. **工厂选择**：自动选择（deepseek 系模型→deepseek、其他→tavily）；显式配置优先。
5. **工具协议**：`web_search` 的协议元数据（name/description/isReadOnly/category/checkPermissions）、input schema（query 必填 + maxResults 可选）、参数解析、结果渲染、provider 失败转错误结果。
6. **配置解析**：`search` 段覆盖默认值、缺省保持默认值。
7. **注册回归**：`AgentLoopFactory.buildTools` 注册 `web_search`；既有 250 条 agent-core 用例无回归。

### 1.3 不在范围

- 真实 DeepSeek / Tavily 端到端联网调用（本批用 WireMock 桩 + lambda provider 隔离，不依赖真实 API key 与网络）。
- `AgentLoop` 端到端调用 `web_search` 的集成行为（属既有回归范围，本批不做新集成用例）。
- 超时（`Duration` 超时链路）的专项测试（provider 层已透传 timeout，本批以 WireMock 桩为主，未单测 timeout 触发路径）。
- Web 前端 / CLI 交互（另有 `2026-08-30-web-ui-e2e` 等批次覆盖）。

---

## 2. 测试环境

| 项 | 值/要求 |
|------|--------|
| 操作系统 | Windows 10（本机） |
| JDK | 17 |
| Maven | 3.9 |
| 测试框架 | JUnit 5（Jupiter） |
| HTTP 桩 | WireMock（`WireMockServer` 随机端口 + `stubFor` + `aResponse`） |
| 断言 | JUnit Assertions（`assertEquals` / `assertThrows` / `assertTrue` / `assertFalse` / `assertInstanceOf`）；`AgentLoopFactoryTest` 用 AssertJ |
| 临时目录 | JUnit `@TempDir`（`ConfigLoaderTest` 的 config.yaml 夹具） |
| 被测模块 | `agent-core`（`-pl agent-core`） |
| 覆盖率门禁 | jacoco：LINE ≥ 80% / BRANCH ≥ 70% |

---

## 3. 测试前置条件

```text
1. 工作目录为仓库根 E:\claude-projects\agent-demo
2. JDK 17 与 Maven 3.9 已配置（本机）
3. agent-core 源码与测试均已就位（add-web-search-tool 变更已实现）
```

运行测试命令：

```bash
mvn -pl agent-core test
```

> `test` 只跑全量用例；`clean verify` 额外触发 jacoco 覆盖率门禁（LINE≥80% / BRANCH≥70%）。

---

## 4. 总体测试计划（用例矩阵）

> 本批 WebSearch 工具测试共 **27 条**用例：新增 5 个测试类（25 条）+ `ConfigLoaderTest` 扩展 2 条。另扩展 `AgentLoopFactoryTest` 断言 `web_search` 注册（不单独计新用例）。另含 **250 条既有回归用例**（见 §7 回归说明）。

| TC 编号 | 名称 | 前置 | 关键步骤 | 预期 | 优先级 |
|:-------:|------|------|---------|------|:------:|
| WS-01 | Provider 接口契约 | 反射读取 `WebSearchProvider` | 断言是 interface；`getMethod("search", String, int, Duration)` 返回 `WebSearchResult` | 接口签名正确 | P0 |
| WS-02 | Source record 字段暴露 | 构造 `Source` | 读 `url`/`title`/`snippet`/`publishedAt` | 与构造入参一致 | P1 |
| WS-03 | WebSearchResult 包装 | 构造 `WebSearchResult` | 读 `sources` 与 `truncated` | sources 大小=1、url 正确、truncated=true | P1 |
| WS-04 | Provider 契约可 lambda 实现 | lambda 实现 provider | `search` 返回空结果 | sources=0、truncated=false | P1 |
| WS-05 | DeepSeek 解析结果块 | WireMock 返回含 `web_search_tool_result` + `citations` | `search` → 映射 | 2 条来源；title/url/snippet/page_age 正确；truncated=false | P0 |
| WS-06 | DeepSeek 按 url 去重 | WireMock 返回重复 url | `search` | sources=1、url 唯一 | P0 |
| WS-07 | DeepSeek 严格模式 | WireMock 返回纯 text（无结果块） | `search` | 抛 `IllegalStateException`，消息含 `web_search_tool_result` | P0 |
| WS-08 | DeepSeek 缺 key | 构造 `DeepSeekWebSearchProvider(null)` | `search` | 抛 `IllegalStateException` | P1 |
| WS-09 | Tavily 解析 results | WireMock 返回 `results[]` | `search` | 2 条来源；title/url/snippet 正确；publishedAt 空；truncated=false | P0 |
| WS-10 | Tavily 结果截断 | WireMock 返回 3 条、maxResults=2 | `search` | sources=2、truncated=true | P0 |
| WS-11 | Tavily 缺 key | 构造 `TavilyWebSearchProvider(null)` | `search` | 抛 `IllegalStateException` | P1 |
| WS-12 | Tavily HTTP 失败 | WireMock 返回 500 | `search` | 抛 `WebClientResponseException` | P1 |
| WS-13 | deepseek 系模型选 deepseek | `AgentConfig.defaults()`（type=deepseek） | `WebSearchProviderFactory.create` | 返回 `DeepSeekWebSearchProvider` | P0 |
| WS-14 | 非 deepseek 回退 tavily | type=minimax | `create` | 返回 `TavilyWebSearchProvider` | P0 |
| WS-15 | 其他 type + deepseek 模型名 | type=openai、model=deepseek-v3 | `create` | 返回 `DeepSeekWebSearchProvider` | P1 |
| WS-16 | 显式配置优先 | type=deepseek + `search.provider=tavily` | `create` | 返回 `TavilyWebSearchProvider` | P0 |
| WS-17 | 显式 deepseek + 非 deepseek 模型 | type=minimax + `search.provider=deepseek` | `create` | 返回 `DeepSeekWebSearchProvider` | P1 |
| WS-18 | 工具协议元数据 | lambda provider | 读 name/description/isReadOnly/category/checkPermissions | name=`web_search`；只读；category=READ；`PermissionDecision.allow()` | P0 |
| WS-19 | input schema | lambda provider | 读 `inputSchema` | type=object；含 query 与 maxResults；required=[query] | P0 |
| WS-20 | 解析 query + maxResults | lambda provider | `parseArguments("{\"query\":\"天气\",\"maxResults\":3}")` | query=天气、maxResults=3 | P1 |
| WS-21 | 缺 maxResults 默认 null | lambda provider | `parseArguments("{\"query\":\"天气\"}")` | query=天气、maxResults=null | P1 |
| WS-22 | 空白 query 拒绝 | lambda provider | `parseArguments` 空白 query | 抛 `IllegalArgumentException` | P1 |
| WS-23 | 渲染 sources | provider 返回 1 条来源 | `execute(...).block()` | 非 error；输出含 title/url/snippet/date | P0 |
| WS-24 | provider 失败转 error | provider 抛 `IllegalStateException` | `execute(...).block()` | isError=true；模型内容含「缺少 API key」 | P0 |
| WS-25 | 空白 query 转 error | lambda provider | `execute` 空白 query | isError=true | P1 |
| WS-26 | search 配置覆盖默认 | yaml `search.provider=tavily, maxResults=3, timeoutMs=30000` | `ConfigLoader.load` | provider=tavily、maxResults=3、timeoutMs=30000 | P1 |
| WS-27 | 缺 search 配置保持默认 | yaml 无 search 段 | `ConfigLoader.load` | provider=""、maxResults=5、timeoutMs=60000 | P1 |

### 4.1 优先执行顺序

1. **P0（核心链路，冒烟）**：WS-01、WS-05、WS-06、WS-07、WS-09、WS-10、WS-13、WS-14、WS-16、WS-18、WS-19、WS-23、WS-24。
2. **P1（边界/辅助）**：WS-02、WS-03、WS-04、WS-08、WS-11、WS-12、WS-15、WS-17、WS-20、WS-21、WS-22、WS-25、WS-26、WS-27。

---

## 5. 测试设计与实现方案

### 5.1 测试分层

| 层 | 测试类 | 覆盖 | 新建/扩展 |
|----|--------|------|:---------:|
| 抽象契约 | `tools/websearch/WebSearchProviderTest` | Provider 接口签名、Source/WebSearchResult record、lambda 实现 | 新建（4） |
| 后端实现 | `tools/websearch/DeepSeekWebSearchProviderTest` | 结果块解析、url 去重、严格模式、缺 key | 新建（4） |
| 后端实现 | `tools/websearch/TavilyWebSearchProviderTest` | results 解析、截断、缺 key、HTTP 500 | 新建（4） |
| 工厂 | `tools/websearch/WebSearchProviderFactoryTest` | 自动选择、显式优先 | 新建（5） |
| 工具接入 | `tools/WebSearchToolTest` | Tool 协议、input schema、参数解析、渲染、错误 | 新建（8） |
| 配置解析 | `config/ConfigLoaderTest` | search 段覆盖默认 / 缺省保持默认 | 扩展（+2） |
| 注册接入 | `core/AgentLoopFactoryTest` | `buildTools` 注册 `web_search` | 扩展（断言） |

### 5.2 夹具与桩设计

- **WebSearchProviderTest / WebSearchToolTest**：用 lambda 实现 `WebSearchProvider`（`(q, max, t) -> new WebSearchResult(...)`），无需真实后端；`WebSearchToolTest` 的 `ToolContext` 用 `new ToolContext(Path.of("/tmp"), null, () -> false)` 构造，provider 通过构造注入 `WebSearchTool(provider, 5, 60000)`。
- **DeepSeekWebSearchProviderTest**：`@BeforeEach` 起 `WireMockServer(0)` 随机端口，`DeepSeekWebSearchProvider("sk-test", "http://localhost:<port>/anthropic/v1")` 指向桩；`stubMessages` 桩 `/anthropic/v1/messages` 返回手写 JSON（`web_search_tool_result` + `text.citations`）。
- **TavilyWebSearchProviderTest**：`WireMockServer(0)` + `TavilyWebSearchProvider("tvly-test", "http://localhost:<port>/search")`；桩 `/search` 返回 `results[]` 或 HTTP 500。
- **WebSearchProviderFactoryTest**：`AgentConfig.defaults()` + `withProvider(type, model)` / `withSearch(base, provider)` 私有构造器拼装不同 provider/search 组合，`assertInstanceOf` 断言选出的实现类型。
- **ConfigLoaderTest**：`@TempDir` 写临时 `config.yaml`，断言 `cfg.search()` 的 provider/maxResults/timeoutMs。

### 5.3 与既有回归的关系

`AgentLoopFactory.buildTools` 注册 `web_search` 后，既有 250 条 agent-core 测试（含 `AgentLoopFactoryTest.buildToolsRegistersExpectedTools` 扩展到断言 `web_search`）继续运行，作为**兼容性回归**验证工具注册数量与既有工具（Shell / Ls / ReadFile / WriteFile / EditFile 等）无冲突。

---

## 6. 测试数据与夹具

| 类别 | 说明 |
|------|------|
| lambda provider | `(q, max, t) -> new WebSearchResult(List.of(...), false)`；空结果 / 单来源 / 抛异常三种变体 |
| DeepSeek 响应桩 | `content[]` 含 `web_search_tool_result.content[]`（`web_search_result` 项：url/title/page_age）+ `text.citations[]`（url/cited_text） |
| Tavily 响应桩 | `results[]`（title/url/content/score），3 条用于截断用例；HTTP 500 桩 |
| 配置桩 | yaml `search: provider/maxResults/timeoutMs`；缺省 yaml |
| 工厂配置 | `AgentConfig.defaults()` + `withProvider(type, model)` + `withSearch(base, provider)` |
| 工具上下文 | `new ToolContext(Path.of("/tmp"), null, () -> false)` |

---

## 7. 退出标准（DoD）

| # | 标准 | 度量 |
|:--:|------|------|
| 1 | 新增 27 条用例全部通过 | `WebSearchProviderTest` 4 + `DeepSeekWebSearchProviderTest` 4 + `TavilyWebSearchProviderTest` 4 + `WebSearchProviderFactoryTest` 5 + `WebSearchToolTest` 8 + `ConfigLoaderTest` 2 全绿 |
| 2 | `AgentLoopFactoryTest` 断言 `web_search` 注册通过 | `buildToolsRegistersExpectedTools` 全绿 |
| 3 | 全量回归 277 条通过（27 新增 + 250 既有无回归） | `Tests run: 277, Failures: 0, Errors: 0, Skipped: 0` |
| 4 | jacoco 覆盖率门禁通过 | LINE≥80% / BRANCH≥70%，输出 "All coverage checks have been met" |
| 5 | 构建成功 | `BUILD SUCCESS` |
| 6 | 缺陷 0 | 无新增缺陷 |

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 依赖真实 DeepSeek / Tavily 端点导致用例不确定 | 用例不稳定/挂起 | 用 WireMock 随机端口桩 HTTP 响应；Tool 层用 lambda provider，全程不碰真实网络 |
| DeepSeek 响应结构复杂（citations 关联 snippet） | 映射错误难定位 | 手写覆盖「结果块 + text.citations」的完整 JSON 桩，断言 title/url/snippet/page_age 四字段 |
| provider 选择逻辑分支多（显式 vs 推断） | 选择错误 | 工厂测试用 `assertInstanceOf` 覆盖 5 条路径（deepseek 推断/回退/模型名推断/显式优先/显式 deepseek） |
| Fail-Closed 语义（provider 异常被吞成 error） | 误判为成功 | `WebSearchToolTest` 显式断言 `isError()` 且模型内容含错误信息 |
| jacoco 门禁失败 | 无法 verify | 全量跑 `mvn -pl agent-core clean verify`，不单独跳过覆盖率 |

---

## 9. 结论与建议

本计划给出 add-web-search-tool（内置 WebSearch 工具）的 27 条用例矩阵（5 个新测试类 25 条 + ConfigLoaderTest 2 条），覆盖抽象契约（Provider 接口 + record）、两个后端（DeepSeek 原生搜索严格模式 + Tavily 检索端点）、工厂自动选择/显式优先、工具协议/渲染/Fail-Closed、配置解析。实现采用 JUnit 5 + WireMock 桩 + lambda provider + `@TempDir`，与既有 250 条回归互补。

建议落地顺序：
1. 先跑契约用例（`WebSearchProviderTest`）与工厂用例（`WebSearchProviderFactoryTest`）。
2. 跑两个后端 WireMock 用例（DeepSeek → Tavily）。
3. 跑工具用例（`WebSearchToolTest`）+ 配置扩展（`ConfigLoaderTest`）。
4. 最终 `mvn -pl agent-core clean verify` 全量验证 + jacoco 门禁。

---

## 10. 执行验证结果（2026-09-02）

> 本计划已实际落地验证，执行结果与环境适配见同目录 `test-report.md`，过程复盘见 `test-review.md`。

### 10.1 执行结果概览

| 维度 | 结果 |
|------|------|
| 新增用例 | 27 条全绿（5 个新测试类 25 条 + ConfigLoaderTest 2 条） |
| 注册断言 | `AgentLoopFactoryTest` 断言 `web_search` 注册通过 |
| 全量回归 | `Tests run: 277, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS` |
| 覆盖率 | jacoco 输出 "All coverage checks have been met"（LINE≥80% / BRANCH≥70%） |
| 缺陷 | 0 |
