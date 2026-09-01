# agent-demo 内置 WebSearch 工具（add-web-search-tool）测试完整复盘

> 主题：对 add-web-search-tool（内置 WebSearch 工具）做一次完整的单元测试收尾
> 测试周期：2026-09-02
> 复盘范围：**设计 → 用例输出 → 执行 → 报告输出** 全流程
> 测试框架：JUnit 5 + WireMock + lambda provider + `@TempDir`
> 复盘角色：测试工程师 / QA

---

## 1. 背景与目标

### 1.1 为什么做这次测试

add-web-search-tool 变更为 `agent-core` 引入**内置联网搜索工具** `web_search`：新增 `WebSearchProvider` 接口 + `WebSearchResult`/`Source` record 的抽象契约，以及 `DeepSeekWebSearchProvider`（Anthropic 兼容 `/messages` + 原生 `web_search_20250305` 严格模式）、`TavilyWebSearchProvider`（`POST https://api.tavily.com/search` 解析 `results[]`）两个后端，通过 `WebSearchProviderFactory` 按配置选择，再由 `WebSearchTool` 暴露为模型可调用工具，最后在 `AgentLoopFactory.buildTools` 注册。这些新增代码需要**单元测试**验证契约、后端解析、工厂选择、工具协议与 Fail-Closed 语义，确保变更在归档前质量达标。

### 1.2 测试目标

1. 验证 `WebSearchProvider` 接口契约与 `Source`/`WebSearchResult` record 语义。
2. 验证两个后端（DeepSeek 严格模式 + 去重、Tavily 截断 + HTTP 失败）的解析与错误处理。
3. 验证工厂自动选择（deepseek 系模型→deepseek、其他→tavily）与显式配置优先。
4. 验证 `WebSearchTool` 的协议元数据、input schema、参数解析、渲染、Fail-Closed。
5. 跑全量 agent-core 回归 + jacoco 覆盖率门禁。
6. 产出四件套文档并归档到 `test-guide.md`。

### 1.3 关键约束（前置认知）

| 约束 | 说明 |
|------|------|
| 被测模块 | `agent-core`，单模块 `-pl agent-core` 运行 |
| 网络隔离 | DeepSeek / Tavily 均依赖真实 HTTP 端点，须用 WireMock 桩 / lambda provider 隔离 |
| Fail-Closed | provider 异常由 `WebSearchTool.execute` 捕获转 `ToolResult.error`，不抛未捕获异常 |
| 覆盖率门禁 | `mvn verify` 强制 LINE≥80% / BRANCH≥70% |

---

## 2. 被测对象分析（测试设计前置）

### 2.1 抽象契约

| 组件 | 职责 | 测试要点 |
|------|------|---------|
| `WebSearchProvider` | 接口：`search(query, maxResults, timeout)` → `WebSearchResult`，失败抛 `IllegalStateException` | 接口签名、lambda 可实现性 |
| `WebSearchResult` | record：`sources`（去重）+ `truncated` | 包装 sources + 截断标志 |
| `Source` | record：`url`/`title`/`snippet`/`publishedAt` | 四字段语义 |

### 2.2 两个后端实现

| 后端 | 端点 | 核心行为 |
|------|------|---------|
| `DeepSeekWebSearchProvider` | `POST {base}/messages`（Anthropic 兼容） | 携带 `web_search_20250305` server tool；解析 `web_search_tool_result` + `citations`；按 url 去重；严格模式（无结果块抛异常）；复用 `DEEPSEEK_API_KEY` |
| `TavilyWebSearchProvider` | `POST https://api.tavily.com/search` | body `{api_key, query, max_results}`；解析 `results[]`；按 `maxResults` 截断；`TAVILY_API_KEY` |

### 2.3 工厂与接入点

- `WebSearchProviderFactory.create(cfg)`：显式 `search.provider` 优先；否则 type=deepseek 或模型名以 deepseek 开头 → deepseek，否则 tavily。
- `WebSearchTool`：name=`web_search`、只读、`checkPermissions=allow`、input schema（query 必填 + maxResults 可选）、`execute` 渲染 + Fail-Closed。
- `AgentLoopFactory.buildTools`：注册 `web_search`。

---

## 3. 测试流程（设计 → 用例 → 执行 → 报告）

### 3.1 阶段一：测试设计

- 产出 `test-design.md`（27 条用例矩阵：25 新增 + 2 配置扩展）。
- 设计视角：**抽象契约 → 后端实现 → 工厂 → 工具接入 → 配置** 五层覆盖，用 WireMock + lambda provider + `@TempDir` 隔离外部依赖。
- 划分优先级：P0 冒烟（契约 / 后端解析与严格模式 / 工厂自动选择 / 工具协议 / 渲染 / Fail-Closed）、P1 边界（缺 key / HTTP 失败 / 参数解析 / 配置）。

### 3.2 阶段二：用例落地

| 文件 | 用例数 | 新建/扩展 |
|------|:------:|:---------:|
| `tools/websearch/WebSearchProviderTest.java` | 4 | 新建 |
| `tools/websearch/DeepSeekWebSearchProviderTest.java` | 4 | 新建 |
| `tools/websearch/TavilyWebSearchProviderTest.java` | 4 | 新建 |
| `tools/websearch/WebSearchProviderFactoryTest.java` | 5 | 新建 |
| `tools/WebSearchToolTest.java` | 8 | 新建 |
| `config/ConfigLoaderTest.java` | +2 | 扩展 |
| `core/AgentLoopFactoryTest.java` | 断言 | 扩展 |

### 3.3 阶段三：执行

```bash
mvn -pl agent-core test
mvn -pl agent-core clean verify
```

执行分两层：**WebSearch 用例**（27 条）与 **既有回归**（250 条）。

### 3.4 阶段四：报告输出

- 结果写入 `test-report.md`：277 用例全绿 + jacoco 达标 + 缺陷 0。
- 关键交付：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` 四件套 + `test-guide.md` 登记归档。

---

## 4. 执行结果

### 4.1 自动化测试结果（实测）

| 层级 | 用例数 | 结果 |
|------|:------:|------|
| `WebSearchProviderTest` | 4 | ✅ 全绿 |
| `DeepSeekWebSearchProviderTest` | 4 | ✅ 全绿 |
| `TavilyWebSearchProviderTest` | 4 | ✅ 全绿 |
| `WebSearchProviderFactoryTest` | 5 | ✅ 全绿 |
| `WebSearchToolTest` | 8 | ✅ 全绿 |
| `ConfigLoaderTest`（search 扩展） | 2 | ✅ 全绿 |
| **WebSearch 合计** | **27** | **✅ 全绿** |
| 既有 agent-core 其余测试 | 250 | ✅ 全绿 |
| **全量合计** | **277** | **✅ 全绿**（`Tests run: 277, Failures: 0, Errors: 0, Skipped: 0`） |

### 4.2 覆盖率验证

jacoco 门禁输出 `All coverage checks have been met`（LINE≥80% / BRANCH≥70%），`BUILD SUCCESS`。

---

## 5. 遇到的问题与解决（复盘点）

### 5.1 两个后端的网络依赖隔离

**现象/风险**：`DeepSeekWebSearchProvider` 与 `TavilyWebSearchProvider` 均通过 `WebClient` 调真实 HTTP 端点，测试若直连会依赖真实 API key 与网络，导致用例挂起/不确定/泄漏凭据。

**解决**：两个后端测试均用 `WireMockServer(0)` 随机端口，测试构造 `http://localhost:<port>/anthropic/v1` 或 `/search` 动态指向桩；`stubFor` 返回手写 JSON，完全隔离真实端点。

> **复盘经验**：HTTP 依赖一律用 WireMock 随机端口桩；基址通过构造参数注入（`DeepSeekWebSearchProvider(apiKey, baseUrl)` / `TavilyWebSearchProvider(apiKey, endpoint)`），生产用默认端点、测试用桩端点，二者互不干扰。

### 5.2 DeepSeek 响应结构复杂（citations 关联 snippet）

**现象/风险**：DeepSeek 的结构化结果分两部分——`web_search_tool_result.content[]` 提供 url/title/page_age，`text.citations[]` 提供 url/cited_text（摘要）；snippet 需按 url 关联。映射逻辑若不覆盖 citations 会丢摘要。

**解决**：`parsesWebSearchToolResultBlocks` 用覆盖「结果块 + text.citations」的完整 JSON 桩，断言四字段（title/url/snippet/page_age）全部正确，锁定 snippet 关联逻辑。

> **复盘经验**：对多字段关联的映射逻辑，测试桩要同时覆盖「主结构 + 关联源」，并逐字段断言，避免只断言数量漏掉字段级映射错误。

### 5.3 严格模式 vs 常规降级的语义区分

**现象/风险**：DeepSeek 端未触发原生搜索时返回纯 text（生成文本），若 provider 降级把生成文本当答案会污染搜索语义。

**解决**：`throwsWhenNoWebSearchToolResultBlock` 用纯 text 响应验证 `IllegalStateException`（消息含 `web_search_tool_result`），锁定「无结构化结果即失败」的严格模式，不降级为文本抓取。

> **复盘经验**：严格模式 / Fail-Closed 的「失败分支」要写显式断言（`assertThrows` + 消息关键字），而非只断言成功路径，才能证明不会静默降级。

### 5.4 Fail-Closed 在 Tool 层的吞异常语义

**现象/风险**：`WebSearchTool.execute` 捕获 provider 异常转 `ToolResult.error`，若测试只断言「不抛异常」会漏掉「错误结果带指引」的语义。

**解决**：`executeReturnsErrorOnProviderFailure` 断言 `isError()=true` 且 `toModelContent()` 含「缺少 API key」，锁定错误结果对模型可见。

> **复盘经验**：吞异常转错误结果的工具，要断言「错误结果内容」，而非仅断言「未抛未捕获异常」。

---

## 6. 发现的缺陷与处置

### 6.1 缺陷清单

| # | 缺陷 | 严重级 | 处置 |
|:--:|------|:------:|------|
| — | 无 | — | 本批未发现缺陷 |

> provider 契约、两个后端（DeepSeek 严格模式 / Tavily 检索）、工厂选择、工具协议与 Fail-Closed、配置解析均符合 spec 预期，无需修复。

---

## 7. 复盘总结

### 7.1 做得好的地方

1. **外部依赖隔离到位**：后端用 WireMock 随机端口桩、工具层用 lambda provider、配置用 `@TempDir`，全部用例确定、可重复、不碰真实网络与宿主环境。
2. **契约用反射锁定**：`providerIsInterfaceWithSearchMethod` 反射断言接口签名，防止后续改动破坏 `WebSearchProvider` 契约。
3. **严格模式与 Fail-Closed 显式化**：严格模式（无结果块抛异常）与 Tool 层吞异常转 error 均写成可读断言，而非只断言成功路径。
4. **工厂选择分支全覆盖**：5 条用例覆盖推断 deepseek / 回退 tavily / 模型名推断 / 显式优先 / 显式 deepseek，无遗漏。
5. **lambda 实现复用**：provider 契约的可 lambda 特性让 `WebSearchToolTest` 全程用 lambda 桩，避免为每个工具用例引入 mock 框架。

### 7.2 可改进/遗留项

1. **timeout 触发路径未专项覆盖**：provider 层透传 `Duration` timeout，本批以 WireMock 桩为主，未单测「超时抛异常」的路径；可补一个慢响应桩验证。
2. **真实端到端冒烟未做**：本批纯单测，未在真实 key + 网络下验证 `web_search` 实际返回结果；可后续补一次运行期冒烟。
3. **DeepSeek 响应空 `content[]` 的边界**：`mapResponse` 对 `content` 非数组抛异常，但 `content` 为空数组的路径未单独断言（与「无结果块」共用抛异常路径，可视为覆盖）；可补显式用例。

### 7.3 对后续的建议

1. 后续新增搜索 provider（如 Bing / SerpAPI）沿用本批 WireMock 随机端口 + lambda provider 隔离模式。
2. 补 provider 层 timeout 触发路径的专项用例。
3. 在 health=ok + key 就位前提下，补一次 `web_search` 真实端到端冒烟。
4. 归档 add-web-search-tool 后，`test-guide.md` 已登记本批次，后续测试按 §2.6 规范继续追加。

---

## 8. 交付物清单

| 交付物 | 路径 |
|--------|------|
| 测试设计 | `docs/test-agent-demo/2026-09-02-web-search/test-design.md` |
| 用例输出 | `docs/test-agent-demo/2026-09-02-web-search/test-cases.md` |
| 测试报告 | `docs/test-agent-demo/2026-09-02-web-search/test-report.md` |
| 过程复盘 | `docs/test-agent-demo/2026-09-02-web-search/test-review.md`（本文件） |
| 测试指南登记 | `docs/test-agent-demo/test-guide.md`（§1 登记表 + §2 详情小节） |
| 新增测试源码（只读，非本批改动） | `agent-core/src/test/java/com/example/agent/tools/websearch/*.java`、`tools/WebSearchToolTest.java` |

> **复盘日期**：2026-09-02
> **执行者**：测试工程师 / QA
