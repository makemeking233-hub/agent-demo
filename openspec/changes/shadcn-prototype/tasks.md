# shadcn-prototype 任务清单

> 详细 step 见 `docs/superpowers/plans/2026-09-18-shadcn-frontend-migration.md` §A。
> 本文件是 OpenSpec tasks.md，与 plan 同步。

## 任务列表

- [ ] **A1** 建 worktree 与分支 + OpenSpec 提案骨架
- [ ] **A2** 装 Tailwind v4 + shadcn 基础依赖
- [ ] **A3** 配 vite.config.ts 注册 tailwindcss 插件
- [ ] **A4** 写 src/index.css @theme 块（CSS 变量映射）
- [ ] **A5** `npx shadcn@latest add popover radio-group label`
- [ ] **A6** 重写 ReasoningEffortSelect 为 Popover + RadioGroup
- [ ] **A7** 改写 ReasoningEffortSelect.test 断言
- [ ] **A8** 跑全套门禁（mvn + vitest + tsc + axe）
- [ ] **A9** 三主题手动验证（light / dark / hc）
- [ ] **A10** 写原型报告 + 不 merge main

## DoD（阶段出口）

- [ ] `feat/shadcn-prototype` 分支在 origin
- [ ] mvn 519+373 全绿、vitest 290 全绿、tsc ≤ 7、axe 零违规
- [ ] 三主题截屏（`docs/test-agent-demo/2026-09-19-shadcn-prototype/screenshots/`）
- [ ] 原型报告（`docs/test-agent-demo/2026-09-19-shadcn-prototype/prototype-report.md`）
- [ ] 通过判据：vite build OK / 三主题视觉一致 / shadcn 输出与原断言差异可吸收 / tsc 错误数 ≤ 7
- [ ] 不 merge main

## 失败处理

任何 DoD 不达标 → 不进 §1；回 brainstorming 选路径 B（跳过原型）或 C（退出全量）。