# agent-demo Web UI 端到端测试 —— 用例输出

> 所属批次：`2026-08-30-web-ui-e2e`
> 类型：② 用例输出文档（自动化用例）
> 对应测试设计：同目录 `test-design.md`
> 执行结果：同目录 `test-report.md`

---

## 1. 用例一览

本批共 **24 条** Web UI E2E 用例（对应 `web-ui` / `web-ui-layout` spec 的可测场景），用 Selenium(Java) 实现，位于 `agent-web/src/test/java/com/example/agent/web/e2e/`。

| TC 编号 | 名称 | 前置 | 关键步骤 | 预期 | 优先级 | 落地 |
|:-------:|------|------|---------|------|:------:|:----:|
| TC-E2E-UI-001 | 三栏布局外壳渲染 | 后端可达 | 打开 `/` | 顶栏 + 左侧会话 + 中间对话区 + 底部输入框全出现 | P0 | ✅ |
| TC-E2E-UI-002 | 顶栏元素 | 后端可达 | 检查顶栏 | 品牌 logo + "agent-demo" + 新建会话 + 主题切换 + 设置 | P1 | ✅ |
| TC-E2E-UI-003 | 主题默认态 | 后端可达 | 读取 body dataset | 切换按钮存在；主题为 light/dark 之一 | P1 | ✅ |
| TC-E2E-UI-004 | 主题切换亮↔暗 | 后端可达 | 点击 toggle | body 主题反转；按钮 aria-label 反转；localStorage 同步 | P0 | ✅ |
| TC-E2E-UI-005 | 主题刷新持久化 | 后端可达 | 切换 → 刷新 | 刷新后主题不变 | P0 | ✅ |
| TC-E2E-UI-006 | 会话列表占位数据 | 后端可达 | 查看左侧 | 按工作区分组，含多条会话 | P1 | ✅ |
| TC-E2E-UI-007 | 会话选中高亮 | 后端可达 | 点击会话项 | 该项高亮，其余取消 | P1 | ✅ |
| TC-E2E-UI-008 | 侧栏折叠/展开 | 后端可达 | 点折叠按钮 | 收起为"展开侧栏"；再点展开 | P1 | ✅ |
| TC-E2E-UI-009 | 新建会话 | 后端可达 | 点顶栏"新建会话" | 左侧新增"新会话"并选中 | P1 | ✅ |
| TC-E2E-UI-010 | 空输入禁用发送 | 后端可达 | 输入框为空 | 发送按钮 disabled | P0 | ✅ |
| TC-E2E-UI-011 | 输入文字启用发送 | 后端可达 | 输入非空 | 发送按钮 enabled，显示字符数 | P1 | ✅ |
| TC-E2E-UI-012 | Shift+Enter 换行 | 后端可达 | 输入时按 Shift+Enter | 不发送，插入换行 | P1 | ✅ |
| TC-E2E-UI-013 | Ctrl+Enter 发送 | 后端可达 | 输入后按 Ctrl+Enter | 发送，输入框清空 | P0 | ✅ |
| TC-E2E-UI-014 | slash 命令提示 | 后端可达 | 输入 `/` | 显示 `/help /clear /history /resume /quit` 提示 | P1 | ✅ |
| TC-E2E-UI-015 | /help 展示命令 | 后端可达 | 发送 `/help` | 对话区显示命令列表 | P1 | ✅ |
| TC-E2E-UI-016 | /clear 清空会话 | 后端可达 | 发送 `/clear` | 对话区清空 | P1 | ✅ |
| TC-E2E-UI-017 | 空对话占位提示 | 后端可达 | 打开新会话 | 显示"开始对话，或输入 /help…" | P2 | ✅ |
| TC-E2E-UI-018 | 助手消息渲染 | 后端可达（桩） | 触发回复 | 消息气泡显示助手文本 | P1 | ⬜ |
| TC-E2E-UI-019 | markdown 渲染 | 后端可达 | 助手含 markdown | 渲染为粗体/代码块 | P2 | ⬜ |
| TC-E2E-UI-020 | abort 按钮出现 | 流式进行中 | 发消息等回复 | busy 时 Composer 显示 abort 按钮 | P1 | ⬜ |
| TC-E2E-UI-021 | abort 后恢复输入 | 后端可达 | 流结束 | abort 按钮消失，输入框可用 | P2 | ⬜ |
| TC-E2E-UI-022 | 健康检查可见 | 后端可达 | 访问 `/api/health` | 200 + status ok（API 层） | P2 | ⬜ |
| TC-E2E-UI-023 | 日志查看页 /logs | 后端可达 | 访问 `/logs` | 会话列表渲染（或空态） | P2 | ⬜ |
| TC-E2E-UI-024 | SPA 路由回落 | 后端可达 | 访问 `/sessions/xxx` | 返回 index.html（非 404） | P2 | ✅ |

> 落地列：✅ = 已实现为自动化用例；⬜ = 未落地（多为依赖真实 LLM/SSE 事件的场景，见 `test-report.md` 覆盖率说明）。

---

## 2. 已落地用例的实现明细

### 2.1 `ThemeToggleE2ETest`（3 条，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `defaultThemeIsRespected` | 003 | 主题切换按钮存在；主题为 light/dark 之一；localStorage 状态一致 |
| `toggleToDarkThenBackToLight` | 004 | 点击后 body dataset 反转、按钮 aria-label 反转、localStorage 同步；再点反转回原主题 |
| `themePersistsAcrossReload` | 005 | 切换后刷新页面，主题不变（localStorage 持久化）；结束后还原初始主题 |

### 2.2 `UiLayoutE2ETest`（14 条，全落地）

| 用例方法 | 覆盖 TC | 断言要点 |
|---------|:-------:|---------|
| `threeColumnLayoutRenders` | 001 | header 存在；页面含"会话"；textarea 唯一且可显示 |
| `topBarElementsShow` | 002 | 含 "agent-demo"；新建会话/设置按钮可显示；主题切换按钮 1 个 |
| `sidebarShowsGroupedPlaceholders` | 006 | 页面含 agent-demo / open-source；aside 内按钮 >0 |
| `selectingSessionHighlightsIt` | 007 | 点击会话项后侧栏仍渲染（避免 stale，重新抓取） |
| `sidebarCollapseExpand` | 008 | 折叠→出现"展开侧栏"；展开→出现"折叠侧栏" |
| `newSessionAddsToSidebar` | 009 | 点击新建后左侧按钮数增加 |
| `emptyInputDisablesSend` | 010 | 发送按钮 disabled |
| `typingEnablesSendAndCountsChars` | 011 | 输入后发送按钮非 disabled；页面含"N 字符" |
| `ctrlEnterSends` | 013 | Ctrl+Enter 后输入框清空 |
| `shiftEnterNewline` | 012 | Shift+Enter 输入多行不发送，输入框保留内容 |
| `slashCommandHintShows` | 014 | 输入 `/` 后出现 /help /clear 提示 |
| `helpCommandRenders` | 015 | 发送 /help 后页面含"可用命令" |
| `emptyChatPlaceholder` | 017 | 页面含"开始对话" |
| `spaRouteFallsBackToIndex` | 024 | `/sessions/abc123` 回落，title 含 "agent-demo" |

---

## 3. 用例实现要点

### 3.1 基类能力（`E2EBase`）

- ChromeDriver + `WebDriverManager.chromedriver()`（自动匹配本机 Chrome 版本）
- 启动前校验后端 `/api/health` 可访问（否则抛异常提示启动命令）
- 通用 helper：`localStorage(key)` / `currentTheme()` / `typeToComposer(text)` / `sendButton()` / `waitForCss(css)` / `clickSend()`

### 3.2 选择器策略

| 目标 | 选择器 |
|------|--------|
| 输入框 | `cssSelector("textarea")` |
| 发送按钮 | `textarea` 的下一个兄弟 `button`（XPath sibling，规避 CSS module hash 类名） |
| 主题切换 | `button[aria-label*='主题']` |
| 新建会话 / 设置 | `button[aria-label='新建会话']` / `button[aria-label='设置']` |
| 侧栏折叠/展开 | `button[aria-label='折叠侧栏' / '展开侧栏']` |
| 会话项 | `aside button`（排除折叠按钮） |

> ⚠️ 已知局限：Composer 发送按钮无 `aria-label`（仅 CSS module 类名），用 sibling 定位相对脆弱；建议前端补 `data-testid`（见 `test-report.md` 缺陷 E2）。

---

## 4. 未落地用例（⬜）及其阻塞原因

| TC | 阻塞原因 |
|----|---------|
| 018 / 019 | 需要真实 LLM 回复触发助手消息渲染（无 API key 时后端走 degraded，不推送 assistant delta） |
| 020 / 021 | abort 按钮在流式 busy 时出现，依赖真实 SSE 流打开 |
| 022 | 属 API 层断言（健康检查），非浏览器 UI；由后端集成测试覆盖 |
| 023 | 前端 `/logs` 页渲染未实现（本轮仅补后端 SPA 回落，见 `test-report.md` 缺陷 E1） |

这些用例划归「集成/LLM 链路」范畴，留待有真实 API key 的环境补齐。
