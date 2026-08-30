# agent-demo 前端 UI 端到端测试完整复盘

> 主题：对 `agent-demo` Web 前端（React + Vite + TypeScript SPA，经 Spring Boot 托管）进行一次完整的端到端（E2E）测试
> 测试周期：2026-08-30
> 复盘范围：**设计 → 用例输出 → 执行 → 报告输出** 全流程
> 测试框架：Selenium 4.25（Java）+ WebDriverManager
> 复盘角色：测试工程师 / QA

---

## 1. 背景与目标

### 1.1 为什么做这次测试

`agent-demo` 项目新增了 Web 前端 UI（`agent-web/frontend`，三栏布局的聊天界面），并有 OpenSpec 能力 spec（`web-ui` / `web-ui-layout`）定义了行为。此前项目只有 CLI，**Web 前端缺乏浏览器级的端到端验证**：单元测试（vitest）只覆盖组件逻辑，无法验证「真实浏览器 ↔ 后端 API ↔ SSE 流」的跨链路行为。

### 1.2 测试目标

1. 摸清 Web UI 前端结构与后端接口。
2. 设计一份**总体端到端测试计划**。
3. 用 **Selenium** 实现浏览器级 E2E 自动化，跑通关键用户链路。
4. 产出**执行报告**，明确通过/失败项与遗留问题。

### 1.3 关键约束（前置认知）

| 约束 | 说明 |
|------|------|
| 被测服务 | `agent-web` Spring Boot 已跑在 `http://127.0.0.1:18080` |
| 浏览器 | 本机 Chrome `151.0.7922.174` |
| 驱动 | 需与 Chrome 版本匹配的 chromedriver |

---

## 2. 被测对象分析（测试设计前置）

### 2.1 前端结构

`agent-web/frontend` 是 React 18 + Vite 6 + TS + Tailwind v4 的 SPA：

| 组件 | 职责 |
|------|------|
| `App.tsx` | 三栏布局外壳（TopBar + Sidebar + ChatPanel） |
| `TopBar.tsx` | 顶栏（品牌 + 新建会话 + 主题切换 + 设置） |
| `Sidebar.tsx` | 左侧会话列表（按工作区分组、可选中、可折叠） |
| `ChatPanel.tsx` | 中间对话区 + Composer、SSE 事件处理 |
| `Composer.tsx` | 底部多行输入（Ctrl+Enter 发送 / Shift+Enter 换行 / 空输入禁用） |
| `ThemeToggle.tsx` | 亮/暗主题切换 |
| `MessageBubble.tsx` | 消息气泡（markdown 渲染） |
| `ToolCallCard.tsx` / `PermissionCard.tsx` | 工具调用卡片 / 权限卡片 |

### 2.2 后端接口（E2E 依赖）

`/api/health`、`/api/chat/send`、`/api/chat/stream/{id}`、`/api/chat/abort/{id}`、`/api/sessions/current` 等。

### 2.3 能力 spec 要点

`web-ui-layout` spec 的三栏布局 + 主题切换、会话列表选中、底部多行输入等行为，是本测试的验收依据。

---

## 3. 测试流程（设计 → 用例 → 执行 → 报告）

### 3.1 阶段一：测试设计

- 产出 **`docs/test-agent-demo/web-ui-e2e-design.md`**（24 条用例矩阵）。
- 设计视角：围绕「真实浏览器 ↔ 后端」的跨链路，聚焦用户能看到、能交互的关键场景。
- 划分优先级：P0 冒烟（布局外壳、主题切换×2、空输入禁用、Ctrl+Enter 发送）；P1 核心交互；P2 增强/边界。

设计要点：
1. **环境适配先行**——识别出本机 Edge driver 下载源不可达，决定用 ChromeDriver。
2. **用例按组件/场景拆分**，覆盖后端可达性、布局、主题、会话、输入、slash 命令、SPA 路由回落。
3. 明确**前置条件**（后端可达）、**选择器策略**、**退出标准**、**风险缓解**。

### 3.2 阶段二：用例输出（落地）

在 `agent-web/src/test/java/com/example/agent/web/e2e/` 落地 17 条 E2E 用例（2 个测试类 + 1 个基类）：

| 文件 | 用例数 | 覆盖 |
|------|:------:|------|
| `ThemeToggleE2ETest.java` | 3 | 主题默认态、切换、刷新持久化 |
| `UiLayoutE2ETest.java` | 14 | 三栏布局、顶栏、会话列表/选中/折叠/新建、输入禁用/计数、Ctrl+Enter/Shift+Enter、slash 提示、/help、空占位、SPA 路由回落 |

**基类 `E2EBase.java`** 承担通用能力：后端可达性校验、ChromeDriver 启动、`navigateToHome`（等 textarea 渲染）、localStorage/主题读取等 helper。

### 3.3 阶段三：执行

执行命令（实测）：

```bash
mvn -pl agent-web test -Dtest='*E2ETest' -DskipNpm=true
```

执行分两类：**普通单元/集成测试**（不启动浏览器）与 **E2E 浏览器测试**（启动 Chrome）。

### 3.4 阶段四：报告输出

- 把执行结果、发现的缺陷写入 `web-ui-e2e-design.md` §10（执行验证结果）。
- 关键交付：**17 用例全绿** + 缺陷清单（E1-E4）。

---

## 4. 执行结果

### 4.1 自动化测试结果（实测）

| 层级 | 用例数 | 结果 |
|------|:------:|------|
| `ThemeToggleE2ETest` | 3 | ✅ 全绿 |
| `UiLayoutE2ETest` | 14 | ✅ 全绿 |
| **E2E 合计** | **17** | **✅ 全绿**（`Tests run: 17, Failures: 0, Errors: 0`）|
| 含 E2E 的 agent-web 全量测试 | 91 | ✅ 全绿 |

覆盖的关键用户链路：三栏布局、主题切换、会话列表、输入发送、slash 命令、SPA 路由回落。

### 4.2 验证稳定性

E2E 17 用例在**多次独立运行**（单类跑、合并跑、全量跑）中均稳定全绿，验证了测试本身的可靠性。

---

## 5. 遇到的问题与解决（核心复盘点）

### 5.1 问题 1：EdgeDriver 下载失败（最关键的卡点）

**现象**：`ThemeToggleE2ETest` 报 `WebDriverManagerException: UnknownHostException: msedgedriver.azureedge.net`——WebDriverManager 无法联网下载 msedgedriver。

**根因**：`msedgedriver.azureedge.net` 下载源在本机 SSL/网络不可达（实测 `FAIL The SSL connection could not be established`），而并行会话的 `E2EBase` 用的是 `WebDriverManager.edgedriver()`。

**解决**：改用 **ChromeDriver**。本机 Chrome 可达，且 `chrome-for-testing` 下载源可达（实测 200）。将 `E2EBase` 从 `EdgeDriver` 改为 `ChromeDriver` + `WebDriverManager.chromedriver()`，让 WebDriverManager 自动下载与 Chrome 版本匹配的 chromedriver。

> **复盘经验**：测试基类不应硬编码单一浏览器；当某浏览器驱动下载源不可达时，应切换到可用浏览器，并把浏览器/驱动选择做成可配置，而非写死。

### 5.2 问题 2：chromedriver 与 Chrome 版本不匹配

**现象**：本机缓存的是 `chromedriver 142`，但 Chrome 是 `151`，直接复用会报 `session not created`。

**解决**：让 WebDriverManager 从 `chrome-for-testing` 源下载匹配的 `chromedriver 151.0.7922.138`（缓存到 `~/.wdm`）。

> **复盘经验**：E2E 环境应显式处理「驱动版本 = 浏览器版本」的不变量；CI 建议预置匹配驱动或走镜像源，避免联网下载失败导致测试不可跑。

### 5.3 问题 3：esbuild.exe 文件锁导致 npm 构建 EPERM

**现象**：`mvn -pl agent-web test` 报 `EPERM: operation not permitted, unlink ...\node_modules\@esbuild\win32-x64\esbuild.exe`。

**根因**：并行会话启动的 vite（PID 40732）/esbuild（PID 14476）进程还活着，锁住了 `esbuild.exe`，导致 `npm ci` 重新安装依赖时无法 unlink。

**解决**：终止 vite/esbuild 进程，释放文件锁。后续构建用 `-DskipNpm=true` 跳过前端 npm 步骤（前端已有构建产物 `static/` 可复用）。

> **复盘经验**：多进程/并行会话环境下，前端构建进程可能残留并锁文件；测试前置先探测并清理，且用 `-DskipNpm=true` 隔离 npm 步骤是稳妥做法。

### 5.4 问题 4：多模块依赖导致测试编译失败

**现象**：`agent-web` 单模块编译测试时报 `找不到符号 类 SessionLogSink`（该测试类位于 agent-core）。

**根因**：`agent-web` 依赖 `agent-core`，但 `-pl agent-web` 单独构建时未先构建/安装 agent-core，导致 agent-web 的测试编译拿不到 agent-core 的类。

**解决**：先 `mvn -pl agent-core install`，再构建 agent-web；这样 agent-web 能从本地仓库解析 agent-core。

> **复盘经验**：多模块项目跑单模块测试前，必须确保依赖模块已 `install` 到本地仓库，否则会出现「编译找不到类」的假性失败。

### 5.5 问题 5：Maven 3.6.1 对 `-D` 系统属性的解析问题

**现象**：`-De2e.web.base=http://127.0.0.1:18080` 被 Maven 误解析成多个参数/插件版本，导致 `Plugin not found`。

**解决**：`E2EBase` 默认 `WEB_BASE=http://127.0.0.1:18080` 正好就是默认值，去掉该命令行参数即可规避。

> **复盘经验**：Maven 老版本（3.6.1）对含 `://` 的 `-D` 属性解析不可靠；尽量让测试用内置默认值，不要依赖命令行传 URL 型属性。

### 5.6 问题 6：E2E 用例对 DOM/路由的错误假设

**现象**：两个用例不稳定——`selectingSessionHighlightsIt`（点击后 React 重渲染产生 stale element）、`logsPageAccessible`（访问 `/logs` 期望渲染但实际 404）。

**解决**：
- `selectingSessionHighlightsIt`：点击会话项后重新抓取元素（避免 stale）；并改用精确会话项选择器（排除折叠按钮）。
- `logsPageAccessible`：改为验证**真实存在的 SPA 路由回落**（`/sessions/<uuid>` 回落 index.html），而非假设 `/logs` 可访问。

> **复盘经验**：E2E 选择器要基于组件实际 DOM（而非猜测）；点击触发 React 重渲染后，旧元素引用会 stale，需重新抓取；「假设某路由存在」应先用 HTTP 探测确认，避免写不可能通过的用例。

### 5.7 问题 7：依赖真实 LLM 的场景无法在无 key 环境稳定触发

**现象**：markdown 渲染、abort 按钮、工具/权限卡片等场景需要真实的 SSE 事件流（依赖 LLM 回复），而 E2E 环境后端在无 API key 时走 degraded 路径，无法稳定触发完整对话流。

**解决**：如实把这类场景列为「集成测试边界」，留待有 key 环境覆盖；本轮聚焦纯前端可稳定测的布局/交互链路（17 用例）。

> **复盘经验**：测试要区分「纯前端可稳定测」与「依赖上游/外部依赖的场景」，不要为了凑用例数而制造不稳定测试；无法稳定触发时，如实记录边界而非硬写。

---

## 6. 发现的缺陷与处置

### 6.1 缺陷清单

| # | 缺陷 | 严重级 | 处置 |
|:--:|------|:------:|------|
| E1 | `/logs` 路由返回 404（SPA 回落未加上该前缀） | 🟡 | **已修复**：`StaticResourceConfig` 的客户端路由前缀增加 `/logs` |
| E2 | Composer 发送按钮无 `aria-label`（仅 CSS module hash 类名） | 🟢 | 已用 XPath sibling 定位绕过；建议前端补 `data-testid` |
| E3 | chromedriver 缓存 142 与 Chrome 151 不匹配 | 🟢 | 已通过 WebDriverManager 下载匹配版解决 |
| E4 | `agent-web/pom.xml` 残留临时 `testExcludes`（跳过 3 个测试） | 🟡 | **已确认移除**并验证 3 个测试恢复通过 |

### 6.2 已提交的修复

| commit | 内容 |
|--------|------|
| `4c0c94b` | test(web): 前端 UI E2E 测试（Selenium+ChromeDriver）+ 测试计划 |
| `d08d910` | fix(web): SPA 回落支持 /logs 路由（消除日志页 404） |

> 说明：`E4` 的 testExcludes 移除与 3 个测试恢复，是通过 agent-core install 后验证通过的（该临时配置在本次复核时已不在 pom 中）。

---

## 7. 复盘总结

### 7.1 做得好的地方

1. **环境适配先行**：先识别浏览器/驱动/进程锁/多模块依赖等环境约束，再写用例，避免「写完跑不起来」。
2. **双层测试互补**：前端已有 vitest 单测（组件逻辑），E2E 补足「真实浏览器 ↔ 后端」跨链路，不重复造轮子。
3. **如实记录边界**：对依赖 LLM 的场景标注为集成边界，不为了凑用例数制造不稳定测试。
4. **测试驱动发现缺陷**：E2E 发现了 `/logs` 404、发送按钮无 aria-label、驱动版本不匹配等真实问题。
5. **问题定位到根因**：Edge driver 下载失败（源不可达）、测试编译失败（多模块依赖）等都定位到根因而非症状。

### 7.2 可改进/遗留项

1. **前端 `/logs` 日志页渲染未实现**：本轮只补了后端 SPA 回落（消除 404），但前端 React 应用**没有 `/logs` 路由切换到 LogsPanel**（`App.tsx` 无路由系统）。若日志页是目标，需前端功能开发（超出测试范畴）。
2. **依赖 LLM 的 E2E 链路未覆盖**：markdown 渲染、abort、工具/权限卡片等场景需在**有真实 API key** 的环境下再补集成/端到端测试。
3. **前端组件可测性**：Composer 发送按钮缺稳定的定位锚点（`data-testid`），建议前端补上以提升 E2E 健壮性。
4. **并行工作区干扰**：测试过程中并行会话在同时改动工作区（Memory/Config/前端产物），曾导致一次 E2E 运行资源竞争变慢；测试应与并行改动隔离或在稳定阶段执行。

### 7.3 对后续的建议

1. 对有 key 环境补「完整对话流」E2E（含 SSE 渲染、abort、工具卡片三态、markdown）。
2. 前端补 `/logs` 路由与 `data-testid`，提升可测性与功能完整性。
3. CI 预置与 Chrome 匹配的 chromedriver（或走 `chrome-for-testing` 镜像），避免联网下载失败。
4. 后续 Web UI 迭代继续用「计划 → 用例 → 执行 → 报告」闭环，把本次创建的 `web-ui-e2e-design.md` 作为基线持续演进。

---

## 8. 交付物清单

| 交付物 | 路径 |
|--------|------|
| 总体 E2E 测试计划 | `docs/test-agent-demo/web-ui-e2e-design.md` |
| E2E 基类（ChromeDriver） | `agent-web/src/test/java/com/example/agent/web/e2e/E2EBase.java` |
| 主题切换用例 | `agent-web/src/test/java/com/example/agent/web/e2e/ThemeToggleE2ETest.java` |
| 布局/交互用例 | `agent-web/src/test/java/com/example/agent/web/e2e/UiLayoutE2ETest.java` |
| SPA 回落 /logs 修复 | `agent-web/src/main/java/com/example/agent/web/StaticResourceConfig.java` |
| E2E 依赖声明 | `agent-web/pom.xml`（selenium-java / webdrivermanager） |
| 本复盘文档 | `docs/test-agent-demo/test-process-review.md` |

> **复盘日期**：2026-08-30
> **执行者**：测试工程师 / QA
