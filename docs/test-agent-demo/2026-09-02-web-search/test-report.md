# agent-demo 内置 WebSearch 工具（add-web-search-tool）测试 —— 测试报告

> 所属批次：`2026-09-02-web-search`
> 类型：③ 测试报告文档
> 测试设计 / 用例：同目录 `test-design.md` / `test-cases.md`
> 测试日期：2026-09-02
> 测试命令：`mvn -pl agent-core test`（全量）/ `mvn -pl agent-core clean verify`（含 jacoco 门禁）

---

## 1. 测试结论（TL;DR）

| 维度 | 结果 | 说明 |
|------|------|------|
| 构建 | ✅ 通过 | `mvn -pl agent-core clean verify` BUILD SUCCESS |
| 新增用例 | ✅ **27 条全绿** | 5 个新测试类 25 条 + `ConfigLoaderTest` 2 条 |
| 注册断言 | ✅ 通过 | `AgentLoopFactoryTest` 断言 `web_search` 注册 |
| 全量回归 | ✅ **277 用例全绿** | `Tests run: 277, Failures: 0, Errors: 0, Skipped: 0` |
| 覆盖率 | ✅ 达标 | jacoco "All coverage checks have been met"（LINE≥80% / BRANCH≥70%） |
| 缺陷 | ✅ 0 | 无新增缺陷 |

---

## 2. 测试环境

| 项 | 值 |
|----|-----|
| 操作系统 | Windows 10（本机） |
| JDK | 17 |
| Maven | 3.9 |
| 测试框架 | JUnit 5（Jupiter）+ WireMock + `@TempDir` |
| 被测模块 | `agent-core`（`-pl agent-core`） |
| 覆盖率门禁 | jacoco：LINE≥80% / BRANCH≥70% |
| 运行命令 | `mvn -pl agent-core test` / `mvn -pl agent-core clean verify` |

---

## 3. 执行结果

### 3.1 分测试类结果

| 测试类 | 用例数 | 结果 | 覆盖 |
|--------|:------:|:----:|------|
| `tools/websearch/WebSearchProviderTest` | 4 | ✅ 全绿 | Provider 接口契约 / Source / WebSearchResult / lambda 实现 |
| `tools/websearch/DeepSeekWebSearchProviderTest` | 4 | ✅ 全绿 | 结果块解析 / url 去重 / 严格模式 / 缺 key |
| `tools/websearch/TavilyWebSearchProviderTest` | 4 | ✅ 全绿 | results 解析 / 截断 / 缺 key / HTTP 500 |
| `tools/websearch/WebSearchProviderFactoryTest` | 5 | ✅ 全绿 | 自动选择 / 显式优先 |
| `tools/WebSearchToolTest` | 8 | ✅ 全绿 | Tool 协议 / input schema / 参数解析 / 渲染 / Fail-Closed |
| `config/ConfigLoaderTest` | +2 | ✅ 全绿 | search 段覆盖默认 / 缺省保持默认 |
| **WebSearch 合计** | **27** | **✅ 全绿** | 25 新增 + 2 配置扩展 |
| 既有 agent-core 其余测试 | 250 | ✅ 全绿 | 含 `AgentLoopFactoryTest` 注册断言回归 |
| **全量合计** | **277** | **✅ 全绿** | `Tests run: 277, Failures: 0, Errors: 0, Skipped: 0` |

> 实测输出（节选）：

```text
[INFO] Tests run: 277, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] All coverage checks have been met.
```

### 3.2 覆盖的关键行为

- **契约**：`WebSearchProvider.search(String, int, Duration)` 返回 `WebSearchResult`；`Source`/`WebSearchResult` record 字段语义正确；接口可 lambda 实现。
- **DeepSeek 后端**：解析 `web_search_tool_result` + `text.citations` 关联 snippet；按 url 去重；严格模式（无结果块抛异常）；缺 key 抛异常。
- **Tavily 后端**：解析 `results[]`（title/url/content→snippet）；`maxResults` 截断 + `truncated` 标志；缺 key 抛异常；HTTP 500 抛 `WebClientResponseException`。
- **工厂**：显式 `search.provider` 优先；未配置时 type=deepseek 或模型名以 deepseek 开头 → deepseek，否则 tavily。
- **工具**：`web_search` 协议元数据（name/description/isReadOnly/category/checkPermissions）、input schema（query 必填 + maxResults 可选）、参数解析（含空白 query 拒绝）、结果渲染（标题/URL/摘要/日期）、provider 失败转错误结果（Fail-Closed）。
- **配置**：`search` 段覆盖默认值 / 缺省保持默认值。
- **注册回归**：`AgentLoopFactory.buildTools` 注册 `web_search`；既有 250 条用例无回归。

---

## 4. 环境适配过程

| # | 项 | 说明 |
|:--:|------|------|
| 1 | HTTP 桩隔离 | DeepSeek / Tavily 后端测试用 `WireMockServer(0)` 随机端口 + `stubFor` 桩端点，避免依赖真实 API key 与网络 |
| 2 | 随机端口规避冲突 | 测试构造 `http://localhost:<port>/anthropic/v1` / `http://localhost:<port>/search` 动态指向桩，不硬编码端口 |
| 3 | lambda provider | `WebSearchToolTest` / `WebSearchProviderTest` 用 lambda 实现 provider，不依赖真实后端 |
| 4 | 临时配置夹具 | `ConfigLoaderTest` 用 `@TempDir` 写临时 `config.yaml`，不读宿主真实配置文件 |
| 5 | 单模块运行 | `-pl agent-core` 单独跑，避免多模块依赖编译问题 |

> 本批无阻塞性环境适配问题；测试在 Windows 10 + JDK 17 + Maven 3.9 本机环境一次跑通。

---

## 5. 缺陷清单

| # | 问题 | 严重级 | 状态 |
|:--:|------|:------:|------|
| — | 无 | — | 本批测试未发现缺陷 |

> 缺陷 0：provider 契约、两个后端（DeepSeek 严格模式 / Tavily 检索）、工厂选择、工具协议与 Fail-Closed、配置解析均符合 `openspec/changes/add-web-search-tool/` 的 spec 预期，未发现需要修复的功能缺陷。

---

## 6. 覆盖率

| 项 | 结果 |
|----|------|
| jacoco 门禁 | ✅ 通过 |
| 输出 | `All coverage checks have been met` |
| LINE | ≥ 80%（达标） |
| BRANCH | ≥ 70%（达标） |

> 覆盖率门禁在 `mvn verify` 阶段强制执行，全量 277 用例通过后门禁放行，未出现覆盖率红线。

---

## 7. 结论与建议

本次 add-web-search-tool（内置 WebSearch 工具）测试**全部通过**：新增 27 条用例全绿，全量 277 条 agent-core 测试零失败，jacoco 覆盖率门禁达标，缺陷 0。provider 抽象契约、DeepSeek 原生搜索（严格模式 + 去重）、Tavily 检索端点、工厂自动选择/显式优先、工具协议与 Fail-Closed、配置解析均得到验证，既有 250 条用例无回归。

**下一步建议**：
1. 后续接入真实网络做一次端到端冒烟（health=ok 前提下验证 `web_search` 实际返回结果）。
2. 可补 provider 层 `timeout` 触发路径（超时异常转 `IllegalStateException` 或子类）的专项用例。
3. 后续新增 provider（如 Bing / SerpAPI）沿用本批 WireMock 桩 + lambda provider 隔离模式，保持用例确定性。

> 详细复盘见同目录 `test-review.md`。
