## 1. Setup — 依赖与 token

- [ ] 1.1 `npm install lucide-react`（图标库；Tailwind 依赖已装，但本次用 CSS Modules + token，不引入 Tailwind 构建）
- [ ] 1.2 新建 `frontend/src/styles/tokens.css`（亮色 `--dsw-*` token，从 deepseek-harness `design-platform.css` 抽取）+ `tokens-dark.css`（暗色 `body[data-ds-dark-theme]` 映射）
- [ ] 1.3 `main.tsx` 引入 `tokens.css`

## 2. 布局壳 — App / TopBar / Sidebar

- [ ] 2.1 新建 `TopBar.tsx`（FishLogo 占位 + "agent-demo" 标题 + [新建会话][主题切换][设置]按钮）
- [ ] 2.2 新建 `Sidebar.tsx`（分组树状静态会话列表 + 可点击选中高亮 + 折叠按钮）
- [ ] 2.3 新建 `ThemeToggle.tsx`（切 `body.dataset.dsDarkTheme` + `localStorage` 持久化）
- [ ] 2.4 `App.tsx` 改为三栏 grid 布局（顶栏 + 侧栏 + 主区），主区渲染 ChatPanel

## 3. 对话区 — ChatPanel / MessageBubble / ToolCallCard / PermissionCard / Composer

- [ ] 3.1 `ChatPanel.tsx` 重构为用新布局 + CSS Modules；保留 SSE 消费/abort/权限逻辑
- [ ] 3.2 `MessageBubble.tsx` 重构（Markdown 渲染，用户右/助手左，Harness 风格气泡）
- [ ] 3.3 `ToolCallCard.tsx` 重构（TerminalBlock 风格三态：running/ok/fail + 耗时 + Result）
- [ ] 3.4 `PermissionCard.tsx` 重构（权限请求卡片，yes/no/always 三按钮）
- [ ] 3.5 新建 `Composer.tsx`（底部多行输入，Ctrl+Enter 发送 / Shift+Enter 换行 + abort 按钮 + 状态条）

## 4. 测试更新

- [ ] 4.1 更新 `MessageBubble.test.tsx` 断言（新 DOM/类名仍可测 markdown/占位/对齐）
- [ ] 4.2 更新 `ToolCallCard.test.tsx` 断言（running/ok/fail 三态 + 无 text 隐藏结果块）

## 5. 验证与收尾

- [ ] 5.1 `cd frontend && npm test` 全绿
- [ ] 5.2 `cd frontend && npm run build` 通过，产出 `static/` bundle
- [ ] 5.3 `mvn -pl agent-web -am verify`（含 jacoco 门禁）通过
- [ ] 5.4 提交前端源码 + `/static` build 产物 + `git push`
