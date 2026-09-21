# shadcn-components-p2 任务清单

## 准备

- [x] **P1** 从 shadcn registry 重拉全部 11 个组件（修 Radix Slot 根因）
- [x] **P2** 偿还 C1（form/sonner stub）/ C2（@ts-nocheck）/ C3（noImplicitAny）/ C4（axe）
- [ ] **P3** 建 worktree `.worktrees/shadcn-components-p2` + OpenSpec 提案

## batch C — 渲染/杂项（8 个，风险最低）

- [ ] **C-1** `MarkdownContent`（+ 共享 `MarkdownContent.module.css`）
- [ ] **C-2** `MessageBubble`
- [ ] **C-3** `ThinkingCollapse`
- [ ] **C-4** `ToolCallCard`
- [ ] **C-5** `PermissionCard`
- [ ] **C-6** `PwaUpdatePrompt`
- [ ] **C-7** `ThemePopover`
- [ ] **C-8** `WorkspacePickerModal`

## batch B — 设置/表单（4 个）

- [ ] **B-1** `ModelSelect`
- [ ] **B-2** `SettingsEmpty`
- [ ] **B-3** `SettingsRows`（被多个 settings 子组件共享，需一并核对）
- [ ] **B-4** 全量回归（settings 家族）

## batch A — 布局/顶部（7 个）

- [ ] **A-1** `TopBar`
- [ ] **A-2** `Sidebar`
- [ ] **A-3** `Composer`
- [ ] **A-4** `ChatPanel`
- [ ] **A-5** `OfflineBanner` / `OfflineFallback`
- [ ] **A-6** `StatsBar`
- [ ] **A-7** 全量回归

## 技术债（C5–C7）

- [ ] **C5** 填全 `[data-theme="hc"]` 高对比度变量
- [ ] **C6** 统一主题机制（`tokens-dark.css` 选择器改 `:root[data-theme="dark"]`）
- [ ] **C7** 废弃 `lib/theme.ts` 旧 API（`main.tsx` 不再调 `initTheme`）

## 收尾

- [ ] **F1** 跑全套门禁（mvn + vitest + tsc + build）
- [ ] **F2** archive + merge main + push + 清理

## DoD（阶段出口）

- [ ] 18 个 `.module.css` 全部删除，grep 确认 0 引用
- [ ] mvn 912 全绿、vitest ≥ 310 全绿、tsc ≤ 7 基线
- [ ] `strict: true` 无 `noImplicitAny` 覆盖
- [ ] 无 `// @ts-nocheck`
- [ ] axe a11y 用例覆盖主要组件
- [ ] OpenSpec archive 完成 + main 复验通过 + push