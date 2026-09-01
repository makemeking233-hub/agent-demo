# agent-demo 测试指南（Test Guide）

> 用途：**登记每次测试的目标与归档情况**，作为 `docs/test-agent-demo/` 测试文档仓库的总索引。
> 对应规范：`AGENTS.md §2.6 测试文档组织规范`（每次测试一个 `YYYY-MM-DD-<语义名>` 时间戳子目录 + 四件套）。
> 维护规则：**每完成一次测试并归档，在此登记一行**；登记后存档，不再改动。

---

## 1. 测试批次登记表

| 批次目录 | 测试主题/目标 | 执行日期 | 用例数 | 结果 | 四件套 | 状态 |
|---------|--------------|:--------:|:------:|:----:|:------:|:----:|
| `2026-08-29-agent-v01-full-test/` | agent-demo v0.1 全面测试（REPL / Provider / AgentLoop / 工具 / 权限 / 会话 / 记忆 / 压缩 / CLI / 冒烟） | 2026-08-29 | 137（34 类） | ✅ 全绿 | ✅ | 已归档 |
| `2026-08-30-web-ui-e2e/` | Web 前端 UI 端到端测试（三栏布局 / 主题切换 / 会话列表 / 输入 / slash 命令 / SPA 路由回落） | 2026-08-30 | 17（E2E） | ✅ 全绿 | ✅ | 已归档 |
| `2026-08-30-plugin-system/` | add-plugin-system Plugin 插件框架测试（Plugin / PluginContext / ExtensionPoints / PluginManager + Mcp / Skills / Memory 三插件） | 2026-08-30 | 12（6 新增 + 6 既有） | ✅ 全绿 | ✅ | 已归档 |
| `2026-09-02-web-search/` | add-web-search-tool 内置 WebSearch 工具测试（WebSearchProvider 契约 / DeepSeek 原生搜索 / Tavily 检索 / 工厂选择 / Tool 协议） | 2026-09-02 | 27（新增，另 250 既有回归） | ✅ 全绿 | ✅ | 已归档 |

---

## 2. 批次详情

### 2.1 `2026-08-29-agent-v01-full-test/` — v0.1 全面测试

- **测试目标**：对 agent-demo v0.1 做一次全面验证，确认可构建、可运行、核心链路可用，并找出质量风险。
- **执行要点**：`mvn clean verify`（137 用例全绿）+ 运行期冒烟；确认用户反馈「输入就报错」已修复。
- **关键发现**：jacoco 覆盖率门禁引用已废弃包 `com.example.agent.agent.*` 导致门禁失守（🔴 最高风险）；全局 LINE 68.3% / BRANCH 55.4% 低于目标。
- **四件套**：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` ✅
- **归档状态**：已归档。

### 2.2 `2026-08-30-web-ui-e2e/` — Web 前端 UI E2E 测试

- **测试目标**：为 Web 前端（React + Vite + TS SPA，经 Spring Boot 托管）设计总体 E2E 测试计划，并用 Selenium 自动化跑通关键用户链路。
- **执行要点**：`E2EBase` 改造为 ChromeDriver（规避 msedgedriver 下载源不可达）；`ThemeToggleE2ETest`(3) + `UiLayoutE2ETest`(14) 共 17 用例全绿。
- **关键发现**：`/logs` 路由返回 404（后端 SPA 回落缺该前缀，已修复）；`HealthController.isProviderConfigured()` 只查 `DEEPSEEK_API_KEY` 环境变量，导致 key 就位也误报 degraded（已修复）；依赖真实 LLM/SSE 的用例在 web,local 启动 + key 就位下**额外验证了回复链路通**（health=ok / send=200 / SSE 返回中文回复）。
- **四件套**：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` ✅
- **归档状态**：已归档。

### 2.3 `2026-08-30-plugin-system/` — add-plugin-system Plugin 插件框架测试

- **测试目标**：对 add-plugin-system（Plugin 插件框架）做单元测试收尾，验证框架核心（Plugin / PluginContext / ExtensionPoints / PluginManager）生命周期、失败隔离、去重与上下文注入，以及 Mcp / Skills / Memory 三个插件的核心行为。
- **执行要点**：`mvn -pl agent-core clean verify`；新增 `McpPluginTest`(2) + `SkillsPluginTest`(2) + `MemoryPluginTest`(2) 共 6 条 + 既有 `PluginManagerTest`(6) 复核；全量 250 用例全绿（`Tests run: 250, Failures: 0, Errors: 0, Skipped: 0`）。
- **关键发现**：jacoco 覆盖率门禁达标（`All coverage checks have been met`，LINE≥80% / BRANCH≥70%）；缺陷 0；既有 244 条（含 v0.4 `McpClientTest`/`SkillCatalogTest`/`MemoryRecallTest` 等 deprecated wrapper 兼容路径）全部通过。
- **四件套**：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` ✅
- **归档状态**：已归档。

### 2.4 `2026-09-02-web-search/` — add-web-search-tool 内置 WebSearch 工具测试

- **测试目标**：对 add-web-search-tool（内置 WebSearch 工具）做单元测试收尾，验证 `WebSearchProvider` 契约 + `WebSearchResult`/`Source` record、DeepSeek 原生搜索（Anthropic 兼容 `/messages` + `web_search_20250305` 严格模式 + 去重）与 Tavily 检索端点（`results[]` 解析 + 截断）、`WebSearchProviderFactory` 自动选择/显式优先、`WebSearchTool` 协议与 Fail-Closed、`search` 配置解析。
- **执行要点**：`mvn -pl agent-core test` + `mvn -pl agent-core clean verify`；新增 `WebSearchProviderTest`(4) + `DeepSeekWebSearchProviderTest`(4) + `TavilyWebSearchProviderTest`(4) + `WebSearchProviderFactoryTest`(5) + `WebSearchToolTest`(8) 共 25 条 + 扩展 `ConfigLoaderTest`(+2) = 27 条；另扩展 `AgentLoopFactoryTest` 断言注册 `web_search`；全量 277 用例全绿（`Tests run: 277, Failures: 0, Errors: 0, Skipped: 0`）。
- **关键发现**：jacoco 覆盖率门禁达标（`All coverage checks have been met`，LINE≥80% / BRANCH≥70%）；缺陷 0；既有 250 条用例无回归。
- **四件套**：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` ✅
- **归档状态**：已归档。

---

## 3. 如何登记下一次测试

新测试完成后按以下步骤登记（遵守 `AGENTS.md §2.6`）：

1. 在 `docs/test-agent-demo/` 新建批次目录：`<YYYY-MM-DD>-<语义名>/`
2. 补齐四件套：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md`
3. 在 **§1 登记表**追加一行（批次目录、主题/目标、日期、用例数、结果、四件套✅、状态=已归档）
4. 在 **§2** 追加该批次的详情小节（目标、执行要点、关键发现、四件套、归档状态）

> 登记示例（新批次追加到 §1 表末行）：
> | `2026-XX-XX-<语义名>/` | <本次测试主题> | <日期> | <N> | <✅/⚠️> | ✅ | 已归档 |
