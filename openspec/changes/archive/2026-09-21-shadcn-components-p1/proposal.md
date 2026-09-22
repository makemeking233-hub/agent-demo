# shadcn-components-p1 — 首批 3 组件迁 shadcn

## Why
shadcn-infra 完成（§B 8/8 task，merge 进 main）。装了 dialog / dropdown-menu / select /
tabs / tooltip / sonner + vitest-axe + @theme + hc 占位，但实际组件代码 4 个还 stub
(form / dropdown-menu / sonner)，4 个 @ts-nocheck。

§2 推进首批**真实使用**：3 个高频 / 缺 a11y 的组件迁到 shadcn 对应物：
- SettingsModal → shadcn Dialog（focus trap + Esc + 焦点还原）
- Dropdown → shadcn DropdownMenu（外点击关闭 + Esc + 焦点还原）
- ThemeToggle → shadcn 三选项（light / dark / hc 接通）

## What Changes
- §2 阶段一：装回 form / dropdown-menu / sonner 完整代码（用 prettier 格式化）
- §2 阶段二：建 worktree + proposal
- §2 阶段三：SettingsModal 重写为 Dialog，props 接口保持
- §2 阶段四：Dropdown 重写为 DropdownMenu（保留 Dropdown 兼容 re-export）
- §2 阶段五：ThemeToggle 三选项（hc 占位接通）
- §2 阶段六：删这 3 个 .module.css + 跑门禁 + archive + merge main + push

## Impact
- 改文件（计划）：
  - SettingsModal.tsx / .test.tsx
  - Dropdown.tsx / .test.tsx（保留兼容 re-export）
  - ThemeToggle.tsx / .test.tsx
  - ThemePopover.tsx 保留（不删）
  - 删 SettingsModal.module.css / Dropdown.module.css / ThemeToggle.module.css
  - 用到 Dropdown 的 4 处调用方：Composer / Sidebar / SettingsNav / TopBar（适配 DropdownMenu API）
- 不动后端 DTO / 不动其他 .module.css

## Out of Scope
- ModelSelect / Composer / ChatPanel 等其他组件迁移（§3）
- 删除 .module.css 全量清理（§4）
- 真实多 provider HTTP 路由（v0.2 未实现）

## 关联文件

- 设计：`docs/superpowers/specs/2026-09-18-shadcn-frontend-migration-design.md` §5
- 计划：`docs/superpowers/plans/2026-09-18-shadcn-frontend-migration.md` §C
- 原型报告：`docs/test-agent-demo/2026-09-18-shadcn-prototype/prototype-report.md`
- §1 归档：`openspec/changes/archive/2026-09-21-shadcn-infra/`
- skill：`~/.dsh/skills/shadcn-tailwind/`