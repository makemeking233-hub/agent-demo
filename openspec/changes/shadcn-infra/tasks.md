# shadcn-infra 任务清单

> 详细 step 见 `docs/superpowers/plans/2026-09-18-shadcn-frontend-migration.md` §B。

## 任务列表

- [ ] **B1** 建 worktree `.worktrees/shadcn-infra` + OpenSpec proposal 骨架（已 commit 在 main）
- [ ] **B2** 装 shadcn 首批依赖（dialog / dropdown-menu / form / select / tabs / tooltip / sonner）
- [ ] **B3** 引入 vitest-axe 自动化 a11y 扫描
- [ ] **B4** 扩展 src/index.css @theme 块（完整 shadcn 语义层 + hc 第三主题 if 选择三主题）
- [ ] **B5** 保留现状 21 个 .module.css 不动（验证 grep）
- [ ] **B6** 统一主题机制（废弃 body[data-ds-dark-theme]，仅用 <html data-theme>）
- [ ] **B7** 跑全套门禁（mvn + vitest + tsc + axe）
- [ ] **B8** web-ui delta spec 写 MODIFIED Requirements + openspec validate + archive + push

## DoD（阶段出口）

- [ ] `feat/shadcn-infra` 分支在 origin
- [ ] mvn 528+379 全绿（main 实际数字）、vitest 293+（含新 axe 用例）、tsc ≤ 7
- [ ] axe 自动化扫描跑通（零违规为佳；有则为既有）
- [ ] 21 个 .module.css 仍工作（grep 验证）
- [ ] web-ui delta spec 通过 openspec validate
- [ ] OpenSpec archive 完成，delta spec 并入 openspec/specs/web-ui/
- [ ] main merge 完成 + 复验 + push main

## 失败处理

任何 DoD 不达标 → 不进 §2；保留 shadcn-infra 分支不 archive；回 brainstorming 重选。

## 复用 §A 经验

§A 原型报告 5 处 deviation 已在 §B 中规避：
1. token 系统统一：本 task B4 + B6 合并处理
2. 双主题机制统一：B6 处理
3. shadcn 4.x API：components.json 已存在；alias 已配置；沿用
4. trigger 撞车：与本任务无关
5. tsc 错误预算：B7 验证 ≤ 7