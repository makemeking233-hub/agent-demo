# shadcn-prototype — 原型：ReasoningEffortSelect 迁 shadcn

## Why
验证 shadcn/ui + Tailwind v4 + Radix UI 在 agent-demo 前端的可行性：
Tailwind v4 装包、@theme 映射、三主题切换、shadcn 组件替换手搓、
vitest 断言兼容、tsc 错误预算。

## What Changes
- 新增 Tailwind v4 + shadcn 依赖
- 新增 src/index.css 含 @theme 块（CSS 变量映射）
- ReasoningEffortSelect.tsx 用 shadcn Popover + RadioGroup 重写
- ReasoningEffortSelect.test.tsx 断言适配

## Impact
- 不入 main（仅原型）
- 改文件：`agent-web/frontend/package.json`、`agent-web/frontend/vite.config.ts`、
  `agent-web/frontend/src/index.css`、`agent-web/frontend/src/components/ReasoningEffortSelect.tsx`、
  `agent-web/frontend/src/components/ReasoningEffortSelect.test.tsx`
- 不动后端、不动其他组件

## Out of Scope
- 其他组件迁移
- 主题切换 UI 改进
- OpenSpec archive（原型不入 change 库）

## 关联文件

- 设计：`docs/superpowers/specs/2026-09-18-shadcn-frontend-migration-design.md` §3
- 计划：`docs/superpowers/plans/2026-09-18-shadcn-frontend-migration.md` §A
- skill：`~/.dsh/skills/shadcn-tailwind/`