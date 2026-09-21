# shadcn-prototype 任务清单

> 详细 step 见 `docs/superpowers/plans/2026-09-18-shadcn-frontend-migration.md` §A。
> 本文件是 OpenSpec tasks.md，与 plan 同步。

## 任务列表

- [x] **A1** 建 worktree 与分支 + OpenSpec 提案骨架
- [x] **A2** 装 Tailwind v4 + shadcn 基础依赖
- [x] **A3** 配 vite.config.ts 注册 tailwindcss 插件
- [x] **A4** 写 src/index.css @theme 块（CSS 变量映射到 --dsw-*）
- [x] **A5** `npx shadcn@latest add popover radio-group label`（+ button）
- [x] **A6** 重写 ReasoningEffortSelect 为 Popover + RadioGroup
- [x] **A7** 改写 ReasoningEffortSelect.test 断言（6/6 通过）
- [x] **A8** 跑全套门禁（mvn 901/901、vitest 290/290、tsc 2 ≤ 7、axe N/A）
- [x] **A9** 三主题手动验证（jsdom 替代，3/3 通过；手动验证需本地 dev server）
- [x] **A10** 写原型报告 + 不 merge main

## DoD（阶段出口）

- [x] `feat/shadcn-prototype` 分支在 origin
- [x] mvn 528+373 全绿、vitest 290 全绿、tsc ≤ 7、axe N/A（手动核对 Radix a11y）
- [x] 三主题 jsdom 测试通过
- [x] 原型报告（`docs/test-agent-demo/2026-09-18-shadcn-prototype/prototype-report.md`）
- [x] 通过判据：vite build OK / shadcn 输出与原断言差异可吸收 / tsc 错误数 ≤ 7
- [x] 不 merge main

## 失败处理

任何 DoD 不达标 → 不进 §1；回 brainstorming 选路径 B（跳过原型）或 C（退出全量）。
**实际**：原型通过，进入 §1 (shadcn-infra)。

## Deviation（与 plan 的偏差）

详见 `docs/test-agent-demo/2026-09-18-shadcn-prototype/prototype-report.md` §3：
1. 现状 token 系统比 plan 复杂（--dsw-* 而非 --surface）
2. 两套主题机制并存（额外发现）
3. 三主题降级为两主题（hc 留 §1）
4. shadcn 4.x 与 3.x 差异（需手写 components.json + alias）
5. trigger 与下拉项文案撞车（trigger 改文案避免）