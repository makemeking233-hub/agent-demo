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
| `2026-09-04-pwa/` | add-pwa-support 完整 PWA（manifest + Workbox SW + 离线 UI + HTTPS 自签证书） | 2026-09-04 | 13 vitest（manifest 4 + pwa-update 3 + offline-banner 6）+ Playwright 3 用例（配置已落地未跑） | ✅ 全绿 | ✅ | 已归档 |
| `2026-09-04-reasoning-thinking/` | add-reasoning-thinking-streaming 推理过程流式（DeepSeek / OpenAI o1 / Anthropic 三 provider reasoning 解析 + SSE thinking 透传 + 折叠 UI） | 2026-09-04 | 30（21 Java + 9 vitest） | ✅ 全绿 | ✅ | 已归档 |
| `2026-09-04-true-streaming/` | add-true-streaming 真流式改造（Provider bodyToFlux + 跨帧行重组 + 正文逐 token 透传 + 复合 sink 转发 + 多流隔离） | 2026-09-04 | 20 新增改动（另 353 core + 158 web + 104 vitest 回归） | ⚠️ 用例全绿，jacoco 门禁既有欠账 | ✅ | 待归档 |
| `2026-09-13-rich-markdown/` | add-rich-markdown-rendering 富 Markdown 渲染（GFM 表格 / 公式 / 代码高亮 / 图片 / 安全基线 / 流式时序 + 后端 GET /api/fs/raw） | 2026-09-13 | 45（21 后端 + 24 前端；另 182 web + 143 vitest 回归） | ✅ 本 change 用例全绿；前端套件 1 例失败继承自 main（已在干净 main 复现） | ✅ | 已归档 |
| `2026-09-13-add-models-dropdown-v0/` | add-models-dropdown-v0 模型+思考强度下拉（AgentLoop 透传 + 三 Provider effort 适配 + Web 后端 reasoningEffort + CLI /effort + 前端 Dropdown/ModelSelect/ReasoningEffortSelect + localStorage 持久化） | 2026-09-13 | 17（10 Java 新增 + 7 vitest Dropdown；前端集成/localStorage 测试未写） | ✅ Java 全绿 / ⚠️ 前端 vitest 沙箱 npm ci 失败未跑 | ✅ | 已实施未归档 |

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

### 2.9 `2026-09-04-reasoning-thinking/` — add-reasoning-thinking-streaming 推理过程流式

- **测试目标**：把 `deepseek-reasoner` / `OpenAI o1` / `Anthropic Claude extended thinking` 等 reasoning model 的推理过程真正流到前端：后端 SSE 增 `message_delta.delta_type="thinking"`、前端 `<ThinkingCollapse />` 折叠展示、thinking 进 history + 单独计费。
- **执行要点**：后端 agent-core 343/343 全绿（+21 新测试：StreamChunkThinkingDelta 5 + DeepSeek reasoning 2 + OpenAi o1 7 + Anthropic 7）；后端 agent-web 153/153 全绿（无回归）；前端 16/16 / 104/104 全绿（+9 新测试：ThinkingCollapse 5 + MessageBubble.thinking 4）。
- **关键发现**：`StreamChunk` sealed 新增 `ThinkingDelta` record（permit 第 8 种）共享 visitor 模式，老 visitor 通过默认空方法兼容；`SseSessionLogSink` 已有 `MessageDelta("thinking", ...)` 透传逻辑（v0.1 spec 就绪），AgentLoop 改后自动激活；`Message.Assistant` 4-arg 兼容 2-arg 老调用（reasoning=List.of(), tokens=0），无破坏性。
- **四件套**：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` ✅
- **归档状态**：已归档。

### 2.8 `2026-09-04-pwa/` — add-pwa-support 完整 PWA 改造

- **测试目标**：把 agent-demo Web 端升级为 Chrome/Edge 可安装的 PWA，涵盖 manifest 注册、Service Worker 运行时缓存（`/assets/*` CacheFirst + `/index.html` NetworkFirst + `/api/**` NetworkOnly）、离线 UI（Snackbar + Composer 禁用 + 路由级 fallback）、新版本检测（auto skipWaiting + 立即刷新）、HTTPS 自签证书支持。
- **执行要点**：前端 95/95 vitest 全绿（13 既有 + 12 新增：manifest 4 + pwa-update 3 + offline-banner 6）；后端 153/153 mvn test 全绿（核心路径，1 skip = E2E）；Playwright + lighthouse-ci 配置文件已落地（本地无 Chrome GUI 跑不动）。
- **关键发现**：`vite-plugin-pwa@0.20.5` peer 依赖只支持 Vite 3/4/5，升到 `1.3.0` 兼容 Vite 6；Vosk 模型 5.79MB 超 Workbox 默认 2MB precache 上限，加 `maximumFileSizeToCacheInBytes: 10MB` 解决；`SslCertificateGenerator` 用 keytool 子进程 + PKCS12 而非 sun.security.x509.* 反射；`Composer` 拆 `ComposerInner` + 外层 `OnlineProvider` 包裹让 7 个旧测试自动有 provider。
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

### 2.10 `2026-09-13-rich-markdown/` — add-rich-markdown-rendering 富 Markdown 渲染

- **测试目标**：把 Web 对话区从「`react-markdown` 裸用 + 6 条 CSS 规则」升级为「完整富 Markdown 渲染」——覆盖 GFM 表格 / 删除线 / 任务列表、KaTeX 数学公式、`rehype-highlight` 代码高亮、远程与本地图片（含新增 `GET /api/fs/raw`）、原始 HTML 安全基线、`useDeferredValue` 流式时序。
- **执行要点**：后端 `mvn -pl agent-web verify`（203 / 203 全绿，跳过 1；含 `FsControllerRawTest` 16 + `FsControllerRawHttpTest` 5 新增；HTTP 层独立覆盖中文文件名 RFC 5987、CSP `sandbox` 头不被容器过滤）+ 前端 `npx vitest run`（新增 `MessageBubble.markdown.test.tsx` 24 用例分 7 组；同步 main 前 19 文件 / 167 例全绿，同步后 20 文件 / 174 例中 1 例失败继承自 main 的 Dropdown 键盘导航用例，已在干净 main 复现）；`npx tsc --noEmit` 错误数 7（与项目基线持平，本 change 未引入新 TS 错误）；`npm run build` 后主 JS 562,484 B（gzip 174.02 kB）/ KaTeX 拆为独立懒加载块 261.76 kB（gzip 77.92 kB）；主 bundle 增量符合 design.md Open Questions 第 2 条预估。
- **关键发现**：（1）design.md D3 原本打算「只注册 14 个常用语言省体积」前提被实测推翻——`rehype-highlight` 顶层静态 `import {common}` 引用关系真实存在、Rollup 摇不掉，自选子集不但不省反而**净减功能**，改回默认 `common` 后主包从 612.87 kB 降到 561.31 kB（**小 51 kB**）。（2）`react-markdown` 默认的 `defaultUrlTransform` 按 `:` 与 `/` 相对位置判断危险协议、**看不见反斜杠**，`C:\...` 被清空；`mdast-util-to-hast` 又会把链接目标规范化成 URI（反斜杠被 `%5C` 编码）——必须自写 `urlTransform` 并对本地路径 `decodeURIComponent`、远程 URL 不解码。（3）`rehype-highlight` 对每个 `pre>code` 无条件加 `hljs` 类（需显式 `no-highlight` 才不加），故「未注册语言降级」的判据只能是「没有 token span」，原测试断言过度指定已修正。（4）新增 `.module.css` import 把 tsc 从 27 顶到 28，根因是项目缺 `src/vite-env.d.ts`（**假报错**），补 `/// <reference types="vite/client" />` 后降到 7（详见 `AGENTS.md §2.7.7`）。（5）一个既有测试因高亮拆分代码文本为多 token span 而合理失败——改为断言 `<code>` 整体文本一字未少，属正常连带而非放宽断言。（6）**T8 流式渲染时序做不到红→绿**——`useDeferredValue` 作用是调度优先级，jsdom 无可观测调度差异，真实收益需浏览器大消息实测；3 个用例是**回归护栏**（增量即时可见 / 快速追加不丢内容 / 长文混排不崩），加实现前后均全绿；这是测试方法学上的诚实交代而非缺陷。
- **四件套**：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` ✅
- **归档状态**：已归档。
### 2.11 `2026-09-13-add-models-dropdown-v0/` — add-models-dropdown-v0 模型 + 思考强度下拉

- **测试目标**：验证 `add-models-dropdown-v0` change 落地——前端 UI 下拉切换模型 + 思考强度（对齐 dsh web `ModelSelect` 体验简化版）；后端 reasoningEffort 透传到三 Provider（OpenAI 透传 / Anthropic 折算 budget_tokens / DeepSeek 忽略）；CLI `/effort <low|medium|high>` 命令；localStorage 跨会话持久化。
- **执行要点**：worktree `feat/add-models-dropdown-v0` 隔离作业（§2.7）；Java 后端 10 个新测试（AgentLoop + OpenAi Mapper + Anthropic + SlashCommand + ModelsController）+ Java 既有测试全绿（agent-core 418/0/0, agent-web 单测 170/0/0, 排除 E2E）；前端 vitest 沙箱 npm ci 失败未跑；7 个 commit 已 push 到 origin（main 干净）。
- **关键发现**：`ChatRequest` record 7 字段签名不破坏（避免改 20 处现有调用方），改用 `extra: Map<String, Object>` 透传 `reasoning_effort`；`ModelsResponse.Model` 加 `reasoningEfforts` 字段（v0.1 固定三档）；`ChatStreamService.create` 新增 5 参重载，向后兼容 1-4 参调用方；`App.tsx` 提升 model/effort state 到顶层共享给 TopBar 和 ChatPanel，避免状态不同步；`OpenAiCompatibleMapper` 显式读 `extra.get("reasoning_effort") instanceof String` 替代原 `containsKey` 检查，语义更清晰。
- **遗留**：前端集成 / localStorage 持久化测试未实施；沙箱 npm ci 失败导致 vitest 未跑；E2E 失败（`WebIntegrationTest` 404 / `UiLayoutE2ETest` 中文编码）已对照 main 基线确认非本 change 引起。
- **四件套**：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md` ✅
- **归档状态**：已实施未归档（branch `feat/add-models-dropdown-v0` 已 push，merge 后 archive）。

---

## 3. 如何登记下一次测试

新测试完成后按以下步骤登记（遵守 `AGENTS.md §2.6`）：

1. 在 `docs/test-agent-demo/` 新建批次目录：`<YYYY-MM-DD>-<语义名>/`
2. 补齐四件套：`test-design.md` / `test-cases.md` / `test-report.md` / `test-review.md`
3. 在 **§1 登记表**追加一行（批次目录、主题/目标、日期、用例数、结果、四件套✅、状态=已归档）
4. 在 **§2** 追加该批次的详情小节（目标、执行要点、关键发现、四件套、归档状态）

> 登记示例（新批次追加到 §1 表末行）：
> | `2026-XX-XX-<语义名>/` | <本次测试主题> | <日期> | <N> | <✅/⚠️> | ✅ | 已归档 |
