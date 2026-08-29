## Context

agent-demo 的 `agent-web` 前端是一个 Vite + React 18 SPA（`agent-web/frontend`），当前用 inline style 实现了单栏聊天流（`App.tsx`/`ChatPanel.tsx`）。参考实现是 `E:\claude-projects\deepseek-harness`（DeepSeek Harness monorepo），其前端（`packages/client/*`）使用 **CSS Modules + `--dsw-*` 设计 token + 亮/暗双主题** 实现三栏布局。本次将 agent-demo 前端重构为对齐该视觉与交互体系，同时**保持后端 /api 与 SSE 契约不变**。

约束：JDK 17 / Maven 3.9 后端不动；前端引入 `lucide-react` 图标；`mvn -pl agent-web verify`（含 frontend-maven-plugin 的 `npm ci && npm run build` + jacoco 门禁）必须仍通过。

## Goals / Non-Goals

**Goals:**
- 完整三栏布局：顶栏（品牌 + 操作按钮）+ 左侧会话列表（分组、可点击选中高亮）+ 中间对话区 + 底部多行输入。
- 复刻 DeepSeek Harness 的 `--dsw-*` token 体系（灰蓝 neutral-bluish 主色板 + deepseek 蓝 accent + amber/green/red 状态色），CSS Modules 实现。
- 亮/暗双主题，顶栏一键切换；主题状态落 `localStorage`。
- 增强交互：Ctrl+Enter 发送 / Shift+Enter 换行、abort 按钮、权限卡片三态、slash 命令（`/help` 等）。
- 前端测试（MessageBubble / ToolCallCard）按新实现更新并通过；后端测试不受影响。

**Non-Goals:**
- 不改 `/api/**` 后端接口、SSE 协议、Java 代码。
- 不接真实 session 历史接口（v0.1 仅 current session 占位）；左侧会话列表用**静态占位数据 + 可点击选中态**。
- 不实现设置页深层表格（仅顶栏设置入口占位）；不做权限 modal UI（沿用 in-chat 卡片）。

## Decisions

### D1: CSS Modules + 复刻 `--dsw-*` token，而非 Tailwind
参考 Harness 原生做法（`ui-theme/src/styles/` 的 `base.css` / `design-platform.css`），直接在 agent-demo 前端引入**同一套 `--dsw-static-*` / `--dsw-alias-*` token**，组件用 CSS Modules 引用。

- 理由：最大化还原 Harness 观感；token 体系天然支持亮/暗双主题；避免引入 Tailwind 这类额外的构建依赖。
- 考虑过：Tailwind（之前所选）。否决：Harness 源码本身用 token + CSS Modules，复刻 token 更能贴近原版且主题切换更自然。
- 落地：新建 `frontend/src/styles/tokens.css`（亮色 token，从 `design-platform.css` 抽取）+ `frontend/src/styles/tokens-dark.css`（暗色，`body[data-ds-dark-theme]` 映射）。`main.tsx` 引入 tokens.css；组件 `.module.css` 引用 `var(--dsw-*)`。

### D2: 主题切换用 `data-ds-dark-theme` 挂在 `<body>` 上的 class
与 Harness 一致：`document.body.dataset.dsDarkTheme = isDark`，CSS 里亮色 `body {}` / 暗色 `body[data-ds-dark-theme] {}`。

- 理由：零框架、纯 CSS 变量驱动、切换即时生效、状态可持久化到 `localStorage`。
- 考虑过：React Context + theme 变量。否决：CSS 变量更轻，且与 Harness 机制一致。
- 落地：`ThemeToggle` 组件切 `body.dataset` + `localStorage`；`useTheme()` hook 读初值（`prefers-color-scheme` 或 `localStorage`）。

### D3: 三栏布局用 CSS grid（`grid-template-columns: sidebar center details`）
参考 `AppFrame.tsx` / `AppFrame.module.css`：grid 三列，侧栏固定宽（可拖拽 handle，v0.1 先做固定宽 + 可折叠），居中 flex column，details 暂作 tool 详情占位（可折叠）。

- 理由：与 Harness 布局一致；grid 天然支持可拖拽列宽与折叠。
- v0.1 简化：侧栏不做拖拽 resize，只做「折叠/展开」按钮；details 列暂时折叠隐藏（工具详情后续再填充）。

### D4: 左侧会话列表用静态占位 + 可点击选中高亮
后端 v0.1 只实现 `GET /api/sessions/current`（占位返回 null），无历史 session 列表接口。为不阻塞 UI 美化，左侧列表用**静态示例数据**（分组树状：工作区 → 会话），支持点击切换选中态（纯前端 state），并高亮当前选中的工作区/会话。

- 理由：聚焦 UI 观感与交互骨架；后端 session 历史属 v0.2。
- 考虑过：接真实 session 接口。否决：后端无该接口，会阻塞。

### D5: 组件拆分
- `App.tsx`：三栏布局壳（TopBar + Sidebar + 主区）。
- `ChatPanel.tsx`：对话流（MessageBubble / ToolCallCard / PermissionCard 渲染 + SSE 消费逻辑保留）。
- 新增 `TopBar.tsx` / `Sidebar.tsx` / `Composer.tsx`（底部输入）/ `ThemeToggle.tsx`。
- 重构 `MessageBubble.tsx`（Markdown，用户右/助手左）/ `ToolCallCard.tsx`（TerminalBlock 风格三态）/ `PermissionCard.tsx`（权限卡片）。

## Risks / Trade-offs

- **风险：现有前端测试（MessageBubble.test / ToolCallCard.test）因 DOM/类名变化而失败** → 按新实现更新这两个测试的断言（文本/元素仍可测，类名改为 CSS Module 稳定 key）。
- **风险：引入外部 token 大量 CSS 变量，需与 Vite 构建兼容** → `tokens.css` 走 `import './styles/tokens.css'`，vite 正常打包；`mvn verify` 前端 build 验证。
- **风险：三栏布局在小屏溢出** → 侧栏窄屏自动折叠（参考 `SIDEBAR_AUTO_COLLAPSE` 思路），details 默认折叠。
- **风险：静态会话列表与真实后端 session 无对应** → 明确标注「占位数据」，后续接真实接口时只改 Sidebar 数据源。

## Migration Plan

1. 新增 `styles/tokens.css` / `styles/tokens-dark.css`，`main.tsx` 引入。
2. 重构布局与组件（App / ChatPanel / MessageBubble / ToolCallCard / PermissionCard），新增 TopBar/Sidebar/Composer/ThemeToggle。
3. 新增图标依赖 `lucide-react`。
4. 更新前端测试断言。
5. `cd frontend && npm test` + `npm run build` 通过；`mvn -pl agent-web -am verify`（含 jacoco 门禁）通过。
6. `git commit` 前端改动 + build 产物 + `git push`。

回滚：本次仅前端源码与 build 产物；回滚只需 `git revert` 相关 commit，后端零改动。
