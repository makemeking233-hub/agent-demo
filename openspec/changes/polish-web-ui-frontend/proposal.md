## Why

agent-demo 的 Web UI（`agent-web` 前端）当前是极简 inline 样式：单栏聊天流 + 底部单行输入，无顶栏、无左侧会话列表、无图标，视觉与 DeepSeek Harness 的成熟产品体验差距大，且 `send` 后无会话管理入口，用户难以在浏览器里获得类 Claude Code / DSH 的交互。本次将其前端外观重构为对齐 DeepSeek Harness 的完整三栏布局与视觉体系，提升可用性与观感。

## What Changes

- **布局重构**：单栏改为完整三栏 —— 顶栏（品牌 logo + 应用名 + [新建会话][主题切换][设置]按钮）、左侧会话列表（分组树状、可点击选中高亮）、中间对话区（消息流 + 工具卡片 + 权限卡片）、底部多行输入（Ctrl+Enter 发送 / Shift+Enter 换行 + 权限/模型状态条）。
- **设计 token 体系**：复刻 DeepSeek Harness 的 `--dsw-*` 设计 token（灰蓝 neutral-bluish 主色板 + deepseek 蓝 accent + amber/green/red 状态色），亮/暗双主题，顶栏切换开关。
- **样式方案**：从 inline style 改为 **CSS Modules + token 变量**（贴合 DFS 原生），并引入 `lucide-react` 图标库。
- **组件重构**：`MessageBubble`（Markdown 渲染）、`ToolCallCard`（类 TerminalBlock 三态卡片）、`PermissionCard`（权限请求卡片）、新增 `Sidebar` / `TopBar` / `Composer` / `ThemeToggle`。
- **交互增强**：会话列表可点击切换（纯前端 state，不等真实 session 接口）、流式输出时 abort 按钮、`/help` 等 slash 命令触发。
- **不改变后端契约**：`/api/**` 接口、SSE 协议、Java 后端均不动（**BREAKING**: 无）。

## Capabilities

### New Capabilities
- `web-ui-layout`: DeepSeek Harness 风格的三栏布局（顶栏 + 会话列表 + 对话区 + 输入区）与交互

### Modified Capabilities
- `web-ui`: 前端用户可见行为变化（会话列表可点击选中、主题切换、增强的输入交互），但后端 /api 与 SSE 契约不变

## Impact

- 前端代码：`apps/... (agent-web/frontend)` 的 `src/App.tsx` / `ChatPanel.tsx` / `MessageBubble.tsx` / `ToolCallCard.tsx` / `PermissionCard.tsx` + 新增 `lib/theme.css` / `components/{Sidebar,TopBar,Composer,ThemeToggle}`。
- 依赖：新增 `lucide-react`（图标）；`react-markdown` 保留。
- 测试：受影响的前端测试（`MessageBubble.test.tsx` / `ToolCallCard.test.tsx`）需按新 DOM/类名更新；后端测试不受影响。
- 后端：无改动。
