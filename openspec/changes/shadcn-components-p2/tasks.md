# shadcn-components-p2 任务清单

> 状态更新（本次会话）：**12/18 个 `.module.css` 已迁**，技术债 C1–C7 全部偿还。
> 剩余 6 个是体积最大、最复杂的（ModelSelect / SettingsRows / Composer /
> WorkspacePickerModal / MarkdownContent / Sidebar）。

## 准备

- [x] **P1** 从 shadcn registry 重拉全部 11 个组件（修 Radix Slot 根因）
- [x] **P2** 偿还 C1（form/sonner stub）/ C2（@ts-nocheck）/ C3（noImplicitAny）/ C4（axe）
- [x] **P3** 建 worktree `.worktrees/shadcn-components-p2` + OpenSpec 提案

## batch C — 渲染/杂项

- [ ] **C-1** `MarkdownContent.module.css`（3883 B）——**未迁**
- [x] **C-2** `MessageBubble`（→ Tailwind，含测试断言从 CSS Module 类名改 flex-row-reverse）
- [x] **C-3** `ThinkingCollapse`
- [x] **C-4** `ToolCallCard`（STATUS_CONFIG 的 class 改为 Tailwind 类字符串）
- [x] **C-5** `PermissionCard`
- [x] **C-6** `PwaUpdatePrompt`
- [x] **C-7** `ThemePopover`（含共用的 `ThemeToggle`）
- [ ] **C-8** `WorkspacePickerModal.module.css`（3728 B）——**未迁**

## batch B — 设置/表单

- [ ] **B-1** `ModelSelect.module.css`（2847 B）——**未迁**
- [x] **B-2** `SettingsEmpty`
- [ ] **B-3** `SettingsRows.module.css`（2888 B，被 **6 个**组件共用：
      `LanguageSelect` / `AppearanceCards` / `EnterBehaviorSelect` /
      `OpenConfigButton` / `PermissionModeSelect` / `ModelsSection`）——**未迁**
- [ ] **B-4** settings 家族全量回归——**未做**

## batch A — 布局/顶部

- [x] **A-1** `TopBar`
- [ ] **A-2** `Sidebar.module.css`（11171 B，最大）——**未迁**
- [ ] **A-3** `Composer.module.css`（3047 B）——**未迁**
- [x] **A-4** `ChatPanel`
- [x] **A-5** `OfflineBanner` / `OfflineFallback`
- [x] **A-6** `StatsBar`
- [x] **A-7** 附带：`MessageActionRow`（别人 commit 引入，一并迁 + 修其 tsc 错误）

## 技术债（C5–C7）

- [x] **C5** 填全 `[data-theme="hc"]` 高对比度变量（全部 `--dsw-static-*` + 别名层，WCAG AAA）
- [x] **C6** 统一主题机制（`tokens-dark.css` 改 `:root[data-theme="dark"]`，并修别名求值 bug）
- [x] **C7** 废弃 `lib/theme.ts`（删除；`main.tsx` 不再调 `initTheme`）

## 收尾

- [ ] **F1** 跑全套门禁（mvn + vitest + tsc + build）——**待全部迁完后做**
- [ ] **F2** archive + merge main + push + 清理——**未做**
- [ ] **F3** 删除全部 `.module.css` 并确认 0 引用——**依赖 A/B/C 全部完成**

## 当前门禁状态（分支上）

| 项 | 值 |
|----|----|
| vitest | 311 passed（41 文件） |
| tsc | **2** 个错误（回到基线；含修掉的 MessageActionRow） |
| vite build | 通过 |
| `.module.css` 数量 | 18 → **6** |
| `@ts-nocheck` | 0 |
| `strict` | 完整（无 `noImplicitAny` 覆盖） |

## 未完成项汇总（下次接手）

1. **6 个 `.module.css` 未迁**：`ModelSelect` / `SettingsRows`（6 个使用方）/
   `Composer` / `WorkspacePickerModal` / `MarkdownContent` / `Sidebar`
2. **§4 清理未做**：删全部 `.module.css`、清理 `package.json` 依赖、
   写 `docs/frontend-design.md`、四件套、最终门禁、archive、merge main
3. **`docs/frontend-design.md` 从未创建**（设计 §7 要求）