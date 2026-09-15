# Tasks：polish-theme-toggle

## 1. ThemeToggle 改造

- [ ] 1.1 改造 ThemeToggle.tsx 为单图标按钮（Sun/Moon/Monitor 动态）+ Popover 容器
  - 测试：`ThemeToggle.test.tsx` 改造（按钮渲染、aria-expanded、点击切换）
  - commit：`feat(web): ThemeToggle 单按钮 + Popover 容器`

## 2. ThemePopover 新组件

- [ ] 2.1 ThemePopover.tsx：绝对定位 Popover + 渲染 AppearanceCards
  - 测试：`ThemePopover.test.tsx`（点击外部关闭、Esc 关闭、选中后关闭）
  - commit：`feat(web): ThemePopover 组件`

## 3. AppearanceCards 视觉升级

- [ ] 3.1 SettingsRows.module.css：cube 样式优化（圆角 12px、阴影、间距、hover/selected）
  - 测试：`AppearanceCards.test.tsx` 验证 selected + hover 不破现有断言
  - commit：`style(web): AppearanceCards 视觉优化`

## 4. 验收

- [ ] 4.1 跑全套门禁：`npx vitest run` + `npx tsc --noEmit`
  - 验证：tsc 错误数 ≤ 7（基线）

## 5. 合并与归档

- [ ] 5.1 `openspec archive polish-theme-toggle --yes`
- [ ] 5.2 §2.7.5.2 合并到 main + main 复验 + push
- [ ] 5.3 §2.7.5.3 清理 worktree + branch

---

**总任务数**：3 实现 + 1 验收 + 3 归档

**累计估算**：~3h
