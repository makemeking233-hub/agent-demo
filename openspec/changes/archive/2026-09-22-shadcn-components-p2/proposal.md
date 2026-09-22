# shadcn-components-p2 — 剩余 18 个组件迁 shadcn / Tailwind

## Why
§2（shadcn-components-p1）完成后，前端仍有 **18 个 `.module.css`** 未迁（原 21 个，已删 3 个）。
本 change 把它们全部迁到 Tailwind utility + shadcn 组件，为 §4 全量删除 CSS Modules 铺路。

同时偿还 §1/§2 遗留的技术债：hc 主题只填了占位变量、双主题机制并存、
`lib/theme.ts` 旧 API 未废弃。

## What Changes
按耦合度分 3 批（同一 worktree 顺序推进，避免并行冲突）：

- **batch C（渲染/杂项，8 个，风险最低）**：`MarkdownContent` `MessageBubble`
  `ThinkingCollapse` `ToolCallCard` `PermissionCard` `PwaUpdatePrompt`
  `ThemePopover` `WorkspacePickerModal`
- **batch B（设置/表单，4 个）**：`ModelSelect` `SettingsEmpty` `SettingsRows`
  （后者被多个 settings 子组件共享）
- **batch A（布局/顶部，7 个）**：`TopBar` `Sidebar` `Composer` `ChatPanel`
  `OfflineBanner` `OfflineFallback` `StatsBar`

另含：
- C5 填全 `[data-theme="hc"]` 的高对比度变量（当前仅 13 个 `--dsw-*` 占位）
- C6 统一主题机制：`tokens-dark.css` 选择器从 `body[data-ds-dark-theme]`
  改为 `:root[data-theme="dark"]`，与 `useThemeApplication` 对齐
- C7 废弃 `lib/theme.ts` 的 `applyTheme` / `toggleTheme` / `initTheme` 旧 API
  （`main.tsx` 不再调用 `initTheme`）

## Impact
- 改文件：18 个 `*.module.css`（逐个删除）+ 对应 `.tsx`（改用 Tailwind）
- 改 `src/index.css`（hc 变量填全）、`src/styles/tokens-dark.css`（选择器统一）
- 删 `src/lib/theme.ts`（或仅保留类型）、改 `src/main.tsx`
- 不动后端 DTO / 不动 `src/components/ui/*`（shadcn 组件本身）

## Out of Scope
- 删全部 `.module.css` 的最终清理与 `docs/frontend-design.md`（§4 `shadcn-cleanup`）
- 真实多 provider HTTP 路由（v0.2 未实现）

## 关联文件

- 设计：`docs/superpowers/specs/2026-09-18-shadcn-frontend-migration-design.md` §6
- 计划：`docs/superpowers/plans/2026-09-18-shadcn-frontend-migration.md` §D
- §1 归档：`openspec/changes/archive/2026-09-21-shadcn-infra/`
- §2 归档：`openspec/changes/archive/2026-09-21-shadcn-components-p1/`
- skill：`~/.dsh/skills/shadcn-tailwind/`