# agent-demo Web UI 端到端（E2E）测试计划

> 测试对象：`agent-web/frontend` 前端 SPA（React 18 + Vite 6 + TypeScript + Tailwind v4）经 Spring Boot 托管后的 Web UI
> 自动化框架：**Selenium 4.25（Java）** + WebDriverManager
> 状态：v0.1 M11 计划
> 输出语言：中文
> 目标：对 Web UI 做**总体端到端测试**，覆盖三栏布局外壳、主题切换、会话选择、消息输入、对话区渲染、SSE 流式、中止、日志查看等关键用户链路

---

## 1. 测试范围与目标

### 1.1 被测系统

| 层 | 组件 | 说明 |
|----|------|------|
| 前端（被测主体） | `agent-web/frontend` | React SPA：`App` + `TopBar` + `Sidebar` + `ChatPanel` + `Composer` + `ThemeToggle` + `MessageBubble` + `ToolCallCard` + `PermissionCard` + `LogsPanel` |
| 后端（支撑） | `agent-web` Spring Boot | 托管静态资源 + REST API + SSE 流 |
| 协议 | `web-ui` / `web-ui-layout` spec | 三栏布局、主题切换、SSE 事件、slash 命令、trusted-host、日志 API |

### 1.2 测试目标

1. **布局外壳**：三栏布局（顶栏 + 左侧会话 + 中间对话 + 底部输入）正确渲染。
2. **主题切换**：亮/暗切换生效且持久化到 `localStorage`。
3. **会话列表**：加载占位会话、按工作区分组、可选中高亮、折叠/展开。
4. **消息输入**：多行输入、Ctrl+Enter 发送、Shift+Enter 换行、空输入禁用发送。
5. **对话区**：用户/助手消息渲染、markdown、工具调用卡片三态、权限卡片、abort 按钮。
6. **SSE 流式**：从后端接收事件并在 UI 上递增渲染。
7. **日志查看**：`/logs` 会话列表 + 事件流视图。
8. **关键 API 集成**：`/api/health`、`/api/chat/send`、`/api/chat/abort`、`/api/sessions/current`。

### 1.3 不在范围

- 后端逻辑正确性（有专门的单元/集成测试覆盖，见 `test-report.md`）
- 浏览器性能/安全性渗透
- 移动端/响应式适配
- Playwright（用户已指定 Selenium；Playwright 作为备选不在本次范围）

---

## 2. 测试环境

| 项 | 值/要求 |
|------|--------|
| JDK | 17 |
| 浏览器 | **Chrome 151.0.7922.174**（本机实测） |
| WebDriver | **chromedriver（与 Chrome 版本匹配）**——由 WebDriverManager 自动从 `googlechromelabs.github.io/chrome-for-testing` 下载 |
| WebDriverManager | 5.9.2 |
| Selenium | 4.25.0（selenium-java） |
| 被测服务 | `agent-web` 后端已跑在 `http://127.0.0.1:18080` |
| 运行模式 | `E2E_HEADLESS=true` 时 headless；否则开发者可见（默认 false） |

> ⚠️ **关键环境约束**：本机已缓存的是 `chromedriver 142`，与 Chrome `151` **不匹配**，直接用会报 `session not created`。且 `msedgedriver` 下载源 `msedgedriver.azureedge.net` **不可达**（SSL 失败）。因此 E2E 必须：
>
> 1. 使用 **ChromeDriver**（而非 EdgeDriver）
> 2. 让 WebDriverManager 从 `googlechromelabs.github.io`（可访问，实测 200）下载**匹配的 chromedriver**
> 3. 通过系统属性 `e2e.web.base=http://127.0.0.1:18080` 指定后端地址

---

## 3. 测试前置条件（运行前必须满足）

```bash
# 1. 后端已在 18080 运行（本机已验证）
curl http://127.0.0.1:18080/api/health
# → {"status":"ok","version":"0.1.0-SNAPSHOT",...}

# 2. E2E 测试用 ChromeDriver（WebDriverManager 自动下载）
```

运行 E2E 测试命令：

```bash
# 本机 developer 可见模式（默认）
mvn -pl agent-web test -Dtest='*E2E*' -DskipNpm=true \
  -De2e.web.base=http://127.0.0.1:18080

# CI headless 模式
E2E_HEADLESS=true mvn -pl agent-web test -Dtest='*E2E*' -DskipNpm=true \
  -De2e.web.base=http://127.0.0.1:18080
```

> 说明：`E2EBase` 默认 `WEB_BASE` = `http://127.0.0.1:18080`（可用 `-De2e.web.base` 覆盖）。

---

## 4. 总体测试计划（用例矩阵）

> 每条用例对应 `web-ui` / `web-ui-layout` spec 的一个可测场景。全部用 Selenium（Java）实现在 `agent-web` 的 e2e 测试包。

| TC 编号 | 名称 | 前置 | 关键步骤 | 预期 | 优先级 |
|:-------:|------|------|---------|------|:------:|
| TC-E2E-UI-001 | 三栏布局外壳渲染 | 后端可达 | 打开 `/` | 顶栏 + 左侧会话 + 中间对话区 + 底部输入框全出现 | P0 |
| TC-E2E-UI-002 | 顶栏元素 | 后端可达 | 检查顶栏 | 品牌 logo + "agent-demo" + 新建会话按钮 + 主题切换 + 设置按钮 | P1 |
| TC-E2E-UI-003 | 主题默认态 | 后端可达 | 读取 body dataset | 切换按钮存在；主题为 light/dark 之一（prefers-color-scheme 或 localStorage） | P1 |
| TC-E2E-UI-004 | 主题切换亮↔暗 | 后端可达 | 点击 toggle | body 主题反转；按钮 aria-label 反转；localStorage 同步 | P0 |
| TC-E2E-UI-005 | 主题刷新持久化 | 后端可达 | 切换 → 刷新页面 | 刷新后主题不变（localStorage 生效） | P0 |
| TC-E2E-UI-006 | 会话列表占位数据 | 后端可达 | 查看左侧 | 按工作区分组（agent-demo / open-source），含多条会话 | P1 |
| TC-E2E-UI-007 | 会话选中高亮 | 后端可达 | 点击某会话项 | 该项高亮，其余取消 | P1 |
| TC-E2E-UI-008 | 侧栏折叠/展开 | 后端可达 | 点击折叠按钮 | 侧栏收起为"展开侧栏"按钮；再点展开 | P1 |
| TC-E2E-UI-009 | 新建会话 | 后端可达 | 点击顶栏"新建会话" | 左侧新增"新会话"并选中 | P1 |
| TC-E2E-UI-010 | 空输入禁用发送 | 后端可达 | 输入框为空 | 发送按钮 disabled | P0 |
| TC-E2E-UI-011 | 输入文字启用发送 | 后端可达 | 输入非空 | 发送按钮 enabled，显示字符数 | P1 |
| TC-E2E-UI-012 | Shift+Enter 换行 | 后端可达 | 输入时按 Shift+Enter | 不发送，插入换行 | P1 |
| TC-E2E-UI-013 | Ctrl+Enter 发送 | 后端可达 | 输入后按 Ctrl+Enter | 发送消息，输入框清空 | P0 |
| TC-E2E-UI-014 | slash 命令提示 | 后端可达 | 输入 `/` | 显示 `/help /clear /history /resume /quit` 提示 | P1 |
| TC-E2E-UI-015 | /help 展示命令 | 后端可达 | 发送 `/help` | 对话区显示可用命令列表 | P1 |
| TC-E2E-UI-016 | /clear 清空会话 | 后端可达 | 发送 `/clear` | 对话区清空 | P1 |
| TC-E2E-UI-017 | 空对话占位提示 | 后端可达 | 打开新会话 | 显示"开始对话，或输入 /help…" | P2 |
| TC-E2E-UI-018 | 助手消息渲染 | 后端可达（AAA 桩） | 触发一次回复 | 消息气泡显示助手文本 | P1 |
| TC-E2E-UI-019 | markdown 渲染 | 后端可达 | 助手消息含 markdown | 渲染为粗体/代码块等 | P2 |
| TC-E2E-UI-020 | abort 按钮出现 | 流式进行中 | 发送消息等待回复 | busy 时 Composer 显示 abort 按钮 | P1 |
| TC-E2E-UI-021 | abort 后恢复输入 | 后端可达 | 流结束 | abort 按钮消失，输入框可用 | P2 |
| TC-E2E-UI-022 | 健康检查可见 | 后端可达 | 访问 `/api/health` | 200 + status ok（API 层，非 UI） | P2 |
| TC-E2E-UI-023 | 日志查看页 /logs | 后端可达 | 访问 `/logs` | 会话列表渲染（若无数据则空态） | P2 |
| TC-E2E-UI-024 | SPA 路由回落 | 后端可达 | 访问 `/sessions/xxx` | 返回 index.html（非 404） | P2 |

### 4.1 优先执行顺序

1. **P0（冒烟，最先）：001、004、005、010、013**
2. **P1（核心布局/交互）：002、003、006-009、011、012、014-018、020**
3. **P2（增强/边界）：017、019、021-024**

---

## 5. 测试设计与实现方案

### 5.1 基类改造（`E2EBase`）

现有 `E2EBase` 用 **EdgeDriver + WebDriverManager 联网下载 msedgedriver**，因下载源不可达而失败。**改造为：**

- **ChromeDriver**（WebDriverManager 自动下载匹配 chromedriver）
- 支持 `E2E_HEADLESS` 环境变量切 headless
- 保留 `navigateToHome()`（等 `textarea` 出现）、`WEB_BASE` 可配置、共享 `WebDriverWait`
- 增加浏览器级断言 helper（读 `localStorage`、`document.body.dataset`）

### 5.2 组件定位策略（Selenium 选择器）

以组件实际渲染的 DOM 为准，优先 `data-testid` / `aria-label` / CSS class：

| 目标 | 选择器 |
|------|--------|
| 输入框（Composer） | `cssSelector("textarea")` |
| 发送按钮 | `button` 内含 `<Send>` 图标（`aria-label`/class） |
| abort 按钮 | `button`（busy 时 Composer 内 `onAbort`） |
| 主题切换 | `button[aria-label*='主题']` |
| 新建会话 | `button[aria-label='新建会话']` |
| 设置 | `button[aria-label='设置']` |
| 侧栏折叠 | `button[aria-label='折叠侧栏' / '展开侧栏']` |
| 会话项 | `.sidebar .item`（含 title + preview） |
| 顶栏品牌 | `.topbar .brand` |
| 消息气泡 | `MessageBubble` 渲染的 role 元素 |
| 对话区列表 | `.panel .list` |

### 5.3 前端单测与 E2E 的关系

前端已有 `vitest` + `@testing-library` 单测（`MessageBubble`、`ToolCallCard`、`LogsPanel`、`sse-client`）。**单测**验证组件逻辑，**E2E** 验证真实浏览器 + 真实后端联动。二者互补，E2E 聚焦跨链路（UI ↔ API ↔ SSE）。

---

## 6. 测试数据与夹具

| 类别 | 说明 |
|------|------|
| 会话占位数据 | `App.tsx` 内置 `PLACEHOLDER_SESSIONS`（5 条，分组 agent-demo/open-source） |
| 主题键 | `localStorage['agent-demo:theme']` = `"light"`/`"dark"` |
| 后端地址 | `-De2e.web.base=http://127.0.0.1:18080` |
| 驱动 | WebDriverManager 自动下载；离线时可 `-Dwdm.chromeDriverUrl=...` 指向镜像 |
| 后端用例 | 真实后端可访问；若走 API 桩则由测试内 stub（本计划聚焦已跑起来的真实服务） |

---

## 7. 退出标准（DoD）

| # | 标准 | 度量 |
|:--:|------|------|
| 1 | **P0 用例全部通过** | E2E 测试绿色 |
| 2 | **P1 用例 ≥ 80% 通过** | 关键链路可用 |
| 3 | 主题切换、选区、输入发送、abort 链路无阻塞缺陷 | 人工复核 + 自动化 |
| 4 | E2E 在 headless（CI）与可见模式都能跑 | 双模式验证 |
| 5 | 后端可达性校验通过（无 key 时 degraded，不阻断 UI 渲染） | `/api/health` |
| 6 | 测试计划与本计划的 TC 矩阵一一对应 | 覆盖矩阵核对 |

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| **chromedriver 与 Chrome 版本不匹配**（本机 142 vs 151） | E2E 无法启动浏览器 | 用 WebDriverManager 从 `chrome-for-testing` 下载匹配版；或将 Chrome 固定到 142 匹配缓存 |
| **msedgedriver 下载源不可达** | Edge E2E 失败 | 改用 ChromeDriver 而非 EdgeDriver |
| 后端需要真实 apiKey 才能跑完整回复链路 | 多数 UI 用例依赖回复 | 用 `/help` `/clear` 等无需 key 的 slash 命令测 UI；回复链路用健康检查/降级态覆盖 |
| `npm run build` 在 CI 偶发 EPERM（esbuild 进程锁） | Maven 构建失败 | `-DskipNpm=true` 跳过前端构建；已终止残留 vite/esbuild 进程 |
| 无网络环境无法下载 driver | E2E 无法跑 | 预下载 driver 到 `~/.wdm`；或 `-Dwdm.chromeDriverUrl` 指定镜像 |

---

## 9. 结论与建议

本计划给出了覆盖 Web UI **全部关键用户链路**的 24 条 E2E 用例，P0=5 条作为冒烟基线。实现采用 **Selenium(Java) + ChromeDriver + WebDriverManager**，直接复用现有 `agent-web` 的 e2e 测试包与 `E2EBase`。**关键的环境适配点**是用 ChromeDriver 替代 EdgeDriver（规避 msedgedriver 下载源不可达），并让 WebDriverManager 自动获取与 Chrome 匹配的 chromedriver。

建议落地顺序：
1. 先改造 `E2EBase` 为 ChromeDriver（打通"能启动浏览器"）。
2. 补齐 P0 冒烟 5 条（三栏、主题切换×2、空输入禁用、Ctrl+Enter 发送）。
3. 再补 P1/P2 用例。
4. 最终 `mvn verify` 全绿 + 人工复核关键链路。

---

## 10. 执行验证结果（2026-08-30）

> 本计划已实际落地验证。E2E 运行环境与结果如下。

### 10.1 环境适配（关键）

- **驱动器**：`E2EBase` 从 **EdgeDriver → ChromeDriver**。原因：本机 `msedgedriver` 下载源 `msedgedriver.azureedge.net` **不可达**（SSL 失败），而 `chrome-for-testing` 源可达（实测 200）。
- **版本**：本机 Chrome `151.0.7922.174`；WebDriverManager 自动下载匹配的 `chromedriver 151.0.7922.138`（缓存于 `~/.wdm`）。
- **被测服务**：`agent-web` 已跑在 `http://127.0.0.1:18080`（`/api/health` → 200）。

### 10.2 自动化测试工具链

| 项 | 值 |
|----|-----|
| 框架 | Selenium 4.25（selenium-java） |
| 驱动管理 | WebDriverManager 5.9.2（ChromeDriver） |
| 后端校验 | `E2EBase.setUpWebDriver` 先探 `/api/health` 200 |
| 运行命令 | `mvn -pl agent-web test -Dtest='*E2ETest' -DskipNpm=true` |

### 10.3 执行结果

| 测试类 | 用例数 | 结果 | 覆盖 |
|--------|:------:|:----:|------|
| `ThemeToggleE2ETest` | 3 | ✅ 全绿 | 主题默认态、toggle 切换、刷新持久化 |
| `UiLayoutE2ETest` | 14 | ✅ 全绿 | 三栏布局、顶栏、会话列表/选中/折叠/新建、输入禁用/计数、Ctrl+Enter/Shift+Enter、slash 提示、/help、空占位、SPA 路由回落 |
| **合计** | **17** | **✅ 全绿** | P0 冒烟 + P1 核心布局/交互 |

> 运行命令实测输出：`Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

### 10.4 发现的缺陷与遗留问题

| # | 问题 | 严重级 | 建议 |
|:--:|------|:------:|------|
| E1 | **`/logs` 路由返回 404**（SPA 客户端路由回落未对该路径生效；`/sessions/*` 回落正常） | 🟡 | 若日志页是 v0.1 目标，后端需补 `/logs` 的 SPA 回落映射；否则 /logs 页不可达 |
| E2 | **Composer 发送按钮无 `aria-label`**（仅 CSS module hash 类名） | 🟢 | 建议补 `data-testid`/`aria-label` 便于稳定定位（非阻塞，已用 XPath sibling 绕过） |
| E3 | 本机 chromedriver 缓存为 142（与 Chrome 151 不匹配），需 WebDriverManager 联网下载 151 | 🟢 | CI 应预置匹配 driver 或走 `chrome-for-testing` 镜像 |
| E4 | `agent-web/pom.xml` 中残留**临时 `testExcludes`**（跳过 `SseSessionLogSinkTest`/`ChatStreamServiceTest`/`WebAgentRuntimeTest`） | 🟡 | 这些是并行会话为绕过 `SessionLogSink` 编译问题而加，E2E 打通后应核验是否能恢复 |

### 10.5 覆盖率对照

本计划 24 条用例中**已落地 17 条（71%）**，P0 全落地。未落地 7 条（P1/P2）为：markdown 渲染、abort 按钮出现/恢复、健康检查可见、日志页、会话选中精确高亮断言（部分被 UiLayout 简化覆盖）。建议下一轮补齐。

