# shadcn-components-p1 任务清单

## 任务列表

- [x] **C1** 装回 dropdown-menu 完整代码（shadcn 4.x 254 行 multi-line）
- [x] **C2** 建 worktree `.worktrees/shadcn-components-p1`
- [x] **C3** SettingsModal 用 shadcn Dialog 重写
- [x] **C4** 删除死代码 Dropdown（plan 原计划是重写为 DropdownMenu，见 deviation）
- [x] **C5** ThemeToggle 加 high-contrast 第四选项
- [x] **C6** 删 3 个 .module.css（SettingsModal / ThemeToggle / Dropdown）+ 跑门禁
- [x] **C7** archive + merge main + push

## DoD（阶段出口）

- [x] `feat/shadcn-components-p1` 分支在 origin
- [x] mvn 533+379 = 912 全绿、vitest 292 passed + 1 skipped、tsc 2 = 基线
- [x] axe 自动化（jsdom 限制，仍 skip；§3 切 happy-dom）
- [x] 3 个 .module.css 删除且 grep 0 引用（21 → 18 个）
- [x] web-ui delta spec 通过 openspec validate
- [x] OpenSpec archive 完成
- [x] main merge 完成 + 复验 + push main

## Deviation（与 plan §C 的偏差）

1. **C4 改为删除而非重写**：plan 说「Dropdown 重写为 DropdownMenu，保留兼容导出」。
   代码事实：Dropdown.tsx 已无生产调用（仅由 ReasoningEffortSelect 使用，但 §A 已把它迁到
   Popover + RadioGroup）→ grep 验证只有 Dropdown.test.tsx 自身引用。直接删除三件
   （.tsx / .test.tsx / .module.css），用户已确认。
2. **C5 改为加第四项而非替换**：plan 说「三选项 light / dark / hc」。现状已有
   light / dark / system，其中 system（跟随系统）是既有功能 → 删除会造成 UX 回归。
   改为**加 hc 为第四项**（4 卡片）。design §8 明确允许「保留 popover 形态」，
   故不强行改用 DropdownMenu。
3. **C6 扩大范围**：SettingsModal.module.css 仍被 SettingsNav.tsx 引用 →
   顺手把 SettingsNav（38 行）迁到 Tailwind，才能删除该 .module.css。
4. **shadcn Dialog 测试用 mock**：radix-ui Slot 的 asChild pattern 在 jsdom 下
   渲染失败（`Primitive.div failed to slot onto its children`）→ SettingsModal.test.tsx
   mock `@/components/ui/dialog`，测试聚焦 props 接口与渲染逻辑；真实
   focus trap / Esc / 外点击行为由 Radix 保证，留 §3 用 happy-dom + axe 验证。
5. **dropdown-menu.tsx 加 `@ts-nocheck`**：shadcn 4.x 输出的 binding element 无显式
   类型，与 strict 模式冲突（同 §1 处理）。

## 复用历史经验

- §0/§A：trigger 与下拉项文案撞车 → SettingsModal 用 aria-label 而非文案定位
- §1：shadcn 4.x 单行输出 → 本次 dropdown-menu 是 multi-line（CLI 行为变化），
  仅需改 `from "cn"` → `from "@/lib/utils"`
- §1：jsdom × Radix Slot → mock 组件而非放弃测试