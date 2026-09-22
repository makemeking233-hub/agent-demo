# shadcn-components-p2 任务清单

> **全部完成**：18/18 组件迁移 + §4 清理 + C1–C7 技术债偿还。

## 准备

- [x] **P1** 从 shadcn registry 重拉全部 11 个组件（修 Radix Slot 根因）
- [x] **P2** 偿还 C1（form/sonner stub）/ C2（@ts-nocheck）/ C3（noImplicitAny）/ C4（axe）
- [x] **P3** 建 worktree `.worktrees/shadcn-components-p2` + OpenSpec 提案

## batch C — 渲染/杂项

- [x] **C-1** `MarkdownContent`（149 行 CSS → `@layer components` 的 `.prose-sm` 块；
      连带迁 `MarkdownImage` / `MermaidBlock` / `MathNode` / `MarkdownLink` 4 个引用方）
- [x] **C-2** `MessageBubble`
- [x] **C-3** `ThinkingCollapse`
- [x] **C-4** `ToolCallCard`
- [x] **C-5** `PermissionCard`
- [x] **C-6** `PwaUpdatePrompt`
- [x] **C-7** `ThemePopover`（含共用的 `ThemeToggle`）
- [x] **C-8** `WorkspacePickerModal`

## batch B — 设置/表单

- [x] **B-1** `ModelSelect`（两层菜单，11 个测试保持全绿）
- [x] **B-2** `SettingsEmpty`
- [x] **B-3** `SettingsRows`（被 6 个组件共用：`LanguageSelect` / `AppearanceCards` /
      `EnterBehaviorSelect` / `OpenConfigButton` / `PermissionModeSelect` / `ModelsSection`
      ——一次性迁完 6 处后删表）
- [x] **B-4** settings 家族全量回归通过

## batch A — 布局/顶部

- [x] **A-1** `TopBar`
- [x] **A-2** `Sidebar`（最大的 `.module.css`，11 KB / 490 行 / 48 处引用）
- [x] **A-3** `Composer`
- [x] **A-4** `ChatPanel`
- [x] **A-5** `OfflineBanner` / `OfflineFallback`
- [x] **A-6** `StatsBar`
- [x] **A-7** 附带：`MessageActionRow`（他人 commit 引入，一并迁 + 修其 tsc 错误）

## 技术债（C5–C7）

- [x] **C5** 填全 `[data-theme="hc"]` 高对比度变量（全部 `--dsw-static-*` + 别名层，WCAG AAA）
- [x] **C6** 统一主题机制（`tokens-dark.css` 改 `:root[data-theme="dark"]`，并修别名求值 bug）
- [x] **C7** 废弃 `lib/theme.ts`（删除；`main.tsx` 不再调 `initTheme`）

## §4 收尾

- [x] **F1** 跑全套门禁：mvn **912 全绿**（0 jacoco 违规）、vitest **311 passed**、tsc **2**、build ✓
- [x] **F2** 删全部 `.module.css`（**21 → 0**），grep 确认 0 引用
- [x] **F3** 清理依赖（卸载未使用的 `happy-dom`）
- [x] **F4** 写 `docs/frontend-design.md`（项目级前端规范）
- [x] **F5** 测试四件套 + `test-guide.md` 登记（§1 表 + §2.20）
- [x] **F6** archive + merge main + push + 清理

## DoD（阶段出口）— 全部达成

- [x] `components/*.module.css` 数量 = **0**；无 `.tsx` 引用
- [x] mvn 533 + 379 = **912 全绿**，0 jacoco 违规
- [x] vitest **311 passed**（41 文件）
- [x] tsc **2**（低于基线 7）
- [x] `strict: true` 完整（无 `noImplicitAny` 覆盖）
- [x] `// @ts-nocheck` = **0**
- [x] axe 用例通过（非 skip）
- [x] `docs/frontend-design.md` 落地
- [x] 四件套 + test-guide 登记
- [x] OpenSpec archive 完成

## Deviation（与 plan 的偏差，全部留痕）

1. **batch C-1 扩大范围**：`MarkdownContent.module.css` 被 4 个文件引用
   （`MarkdownImage` / `MermaidBlock` / `MathNode` / `MarkdownLink`），一并迁移
2. **batch B-3 扩大范围**：`SettingsRows.module.css` 被 6 个组件共用，必须一次迁完
3. **`MarkdownContent` 用 `@layer components` + `@apply`** 而非逐元素堆 utility：
   30+ 个后代选择器若全量内联会让 JSX 不可读，且不引入 `@tailwindcss/typography` 依赖
4. **新增 `@theme` 语义色**：`--color-warning` / `--color-success` / `--color-accent-subtle` /
   `--color-neutral-50..900`（项目自有语义色与色阶，shadcn 默认没有）
5. **修 1 个既有 tsc 错误**：`MessageActionRow.tsx` 的 `ReturnType<typeof setTimeout>`
   与 `window.setTimeout` 类型冲突（他人 commit 引入）
6. **卸载 `happy-dom`**：原本为 axe 装的，纠正 matcher 用法后发现 jsdom 足够

## 明确未覆盖（deferred）

- 全量组件级 axe 扫描（本次只给 `ReasoningEffortSelect` 建了 a11y 测试文件）
- Playwright E2E（沙箱无浏览器）
- 像素级视觉回归（无截图基线）
- 三主题实机视觉走查（需人工在浏览器切换）