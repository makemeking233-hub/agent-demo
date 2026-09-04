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
| `2026-09-02-session-switch-selenium/` | web 会话切换功能 Selenium 自动化验证（真实会话列表 / 点击加载历史 / 切换更新） | 2026-09-02 | 5 | ✅ 全绿 | ✅ | 已归档 |
| `2026-09-04-workspace-picker/` | add-workspace-picker-modal 工作区目录选择器测试（后端 fs API + 前端 Modal + Sidebar 集成） | 2026-09-04 | 51（28 Java + 23 vitest） | ✅ 全绿 | ✅ | 已归档 |
| `2026-09-04-workspace-picker-v2/` | polish-workspace-picker-dsh-style Modal 重写为 DSH 风格（左侧导航树 + history 栈 + 列头排序 + 底部路径框 + quick-access API） | 2026-09-04 | 14（4 Java + 10 vitest） | ✅ 全绿 | ✅ | 已归档 |

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

### 2.7 `2026-09-04-workspace-picker-v2/` — polish-workspace-picker-dsh-style Modal 重写为 DSH 风格

- **测试目标**：把 `WorkspacePickerModal` 从单栏条目录表重写为 DSH 资源管理器风格（顶部 ←/→/↑ + 面包屑；主区域左 200px 导航树 + 右文件列表带列头排序；底部"文件夹"路径框 + 工作区名称框）；新增后端 `GET /api/fs/quick-access` 接口支持左导航树。
- **执行要点**：后端 `mvn -pl agent-web test`（FsControllerTest 新增 4 quick-access 用例，19/19 全绿）+ 前端 `npx vitest run`（82/82，新增 10：fs.test 4 + Modal 6）+ `mvn -pl agent-web verify` jacoco 门禁 BUILD SUCCESS（"All coverage checks have been met"）。
- **关键发现**：User 选了"保留 name 输入框"+ "B + DSH 风格"路径；File System Access API 在我们场景下拿不到绝对路径，所以放弃 C 方案改回 A 方案 Modal 仿 DSH；history 栈纯前端 + 列头排序 useMemo + grid 布局是性能/视觉兼顾的选择；旧 beforeEach 缺 getQuickAccess 默认值导致首跑 16 失败，补充后通过；`listDir` mock 缺越界校验让"路径框非法"测试失败，补充 mock 后通过。
- **四件套**：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` ✅
- **归档状态**：已归档。

### 2.6 `2026-09-04-workspace-picker/` — add-workspace-picker-modal 工作区目录选择器测试

- **测试目标**：对 `add-workspace-picker-modal` change 做完整验证，覆盖后端 fs API（`/api/fs/home|list|mkdir|drives` 4 端点 + 路径安全边界）、前端 `WorkspacePickerModal`（仿 DSH 文件选择器交互）、Sidebar 嵌入 Modal 端到端链路（点 `+` → 弹 Modal → 浏览 → 选中 → 改 name → 提交 → `onCreateWorkspace`）；同时确认 jacoco 门禁（LINE≥80% / BRANCH≥70%）通过。
- **执行要点**：后端 `mvn -pl agent-web -am test`（149 + 既有 322 = 471 全绿）+ 前端 `npx vitest run`（72 全绿）+ `mvn -pl agent-web verify`（jacoco check-coverage BUILD SUCCESS）；新增 28 Java 单测（HomePathGuardTest 13 + FsControllerTest 15）+ 23 vitest 用例（fs.test 12 + WorkspacePickerModal 14 + Sidebar 9 其中 1 新增端到端集成）。
- **关键发现**：`mkdir 模式`首版只校验直接父目录，测试 `mkdirCreatesNestedDirectories` 暴露"嵌套 mkdir"逻辑缺失，沿 parent 链向上找第一个 existing 祖先后才通过；`vi.mock` factory 不能引用顶层 var，用 `vi.hoisted` 包装 mock 对象解决；PowerShell 把 `-DskipNpm=true` 误解析为 lifecycle phase，需用 `cmd.exe /c` 调用 mvn 绕过。
- **四件套**：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` ✅
- **归档状态**：已归档。

### 2.5 `2026-09-02-session-switch-selenium/` — web 会话切换 Selenium 自动化验证

- **测试目标**：端到端验证 `add-session-switch`（web 侧边栏真实会话列表 + 点击切换加载历史）——确认修复后点会话能真正切换（此前占位列表 + currentSessionId 未传 ChatPanel 导致"切换不了"）。
- **执行要点**：python 3.12 + selenium 4.48 + webdriver-manager（清华镜像安装）+ Chrome 151（chromedriver 自动匹配）；打开 `http://127.0.0.1:18080/`；用例 S1 会话列表非空（60 个）、S2 点击[hi]加载历史、S3 切换[go]更新、S4 对话区随会话更新；共 5 条 PASS（0 FAIL）。
- **关键发现**：python 无 selenium，官方 pip 源 SSL 失败 → 清华镜像成功；本机 chromedriver(142) 与 Chrome(151) 不匹配 → webdriver-manager 自动下载 151.0.7922.138；会话列表为真实 `/api/sessions` 数据。
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
