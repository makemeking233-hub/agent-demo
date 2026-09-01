# Tasks: 内置 WebSearch 工具（add-web-search-tool）

> TDD 顺序：每项先写测试（红）→ 实现 → 转绿 → commit。

## 1. Provider 接口与模型

- [x] 1.1 先写 `WebSearchProviderTest`（接口/record 契约）→ 实现 `WebSearchProvider` 接口 + `WebSearchResult`/`Source` record → 转绿

## 2. DeepSeek 原生搜索 provider

- [x] 2.1 先写 `DeepSeekWebSearchProviderTest`（WireMock 模拟 `POST /anthropic/v1/messages` 返回 `web_search_tool_result` 块）→ 实现 `DeepSeekWebSearchProvider`（复用 `DEEPSEEK_API_KEY`，body 带 `web_search_20250305` server tool）→ 转绿
- [x] 2.2 严格模式测试：响应不含 `web_search_tool_result` 块 → provider 抛错（不降级为文本抓取）

## 3. Tavily 检索端点 provider

- [x] 3.1 先写 `TavilyWebSearchProviderTest`（mock `POST https://api.tavily.com/search` 返回 `results[]`）→ 实现 `TavilyWebSearchProvider`（`TAVILY_API_KEY`，映射 title/url/content/score）→ 转绿
- [x] 3.2 失败路径测试：无 `TAVILY_API_KEY` / HTTP 失败 → 返回错误结果

## 4. provider 自动选择

- [x] 4.1 先写 `WebSearchProviderFactoryTest` → 实现 `WebSearchProviderFactory`（未显式配置时：deepseek 系模型→`deepseek`，其他→`tavily`；显式配置优先）→ 转绿

## 5. WebSearchTool 工具

- [x] 5.1 先写 `WebSearchToolTest`（`Tool<String,String>` 协议：name/description/inputSchema/parseArguments/execute/权限 allow）→ 实现 `WebSearchTool`（execute 调选中 provider，结构化结果渲染为文本注入上下文）→ 转绿

## 6. 配置与装配

- [x] 6.1 `AgentConfig` 加 `Search(String provider, int maxResults, int timeoutMs)` record + `defaults()`（默认 provider=""→自动推断 / maxResults=5 / timeoutMs=60000）
- [x] 6.2 `ConfigLoader.mergeSearch` 解析 `search:` 段 + `EnvKeys.TAVILY_API_KEY` + `ConfigLoaderTest` 转绿
- [x] 6.3 `AgentLoopFactory.buildTools` 注册 `WebSearchTool`（provider 实例化 + config 注入；CLI/web 共用）

## 7. 文档 + 验证 + 提交 + 归档

- [x] 7.1 `docs/design/design.md` 补 WebSearch 简介；README 提及
- [x] 7.2 `mvn -pl agent-core clean verify` 全绿（含 jacoco LINE≥80% / BRANCH≥70%）
- [x] 7.3 写 4 件套测试文档 `docs/test-agent-demo/2026-09-02-web-search/`（test-design/cases/report/review）+ `test-guide.md` 登记
- [x] 7.4 中文 Conventional Commits 分 commit + push（按 §2.2 commit 即 push）
- [x] 7.5 `openspec archive add-web-search-tool`（delta spec 合并到 `openspec/specs/search/spec.md`）
