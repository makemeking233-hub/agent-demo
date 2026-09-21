# shadcn-components-p1 任务清单

## 任务列表

- [ ] **C1** 装回 form / dropdown-menu / sonner 完整代码（prettier 格式化）
- [ ] **C2** 建 worktree `.worktrees/shadcn-components-p1`
- [ ] **C3** SettingsModal 用 shadcn Dialog 重写
- [ ] **C4** Dropdown 用 shadcn DropdownMenu 重写（保留兼容导出）
- [ ] **C5** ThemeToggle 三选项（接 hc 主题入口）
- [ ] **C6** 删这 3 个 .module.css + 跑门禁
- [ ] **C7** archive + merge main + push

## DoD（阶段出口）

- [ ] `feat/shadcn-components-p1` 分支在 origin
- [ ] mvn 533+379 全绿、vitest ≥ 293 全绿、tsc ≤ 7
- [ ] axe 自动化（jsdom 限制，§2 仍 skip）
- [ ] 这 3 个 .module.css 全部删除且 grep 0 引用
- [ ] web-ui delta spec 通过 openspec validate
- [ ] OpenSpec archive 完成
- [ ] main merge 完成 + 复验 + push main

## 失败处理

任何 DoD 不达标 → 不进 §3；保留分支不 archive；回 brainstorming 重选。

## 复用 §0 / §A / §1 经验

- §A 原型：trigger 与下拉项文案撞车 → trigger 用 aria-label + textContent 简洁
- §1 infra：shadcn 4.x 单行输出 → 用 prettier 格式化重装；保留 `// @ts-nocheck` 与 stub 作为兜底
- §0 design：trigger 撞车 a11y：Shadcn Button 默认已含 aria-expanded / aria-controls，无需手工补