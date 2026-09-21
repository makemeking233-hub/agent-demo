# shadcn-infra — 基础设施 + tokens 统一

## Why
shadcn-prototype 完成（§A 10/10 task，merge 进 main）。原型的 @theme 映射已验证可行，
但有 5 处遗留问题需 §1 解决：

1. 现状 token 系统（`--dsw-static-*` + `--dsw-alias-*`）与 shadcn 标准命名 (`--background` /
   `--foreground`) 平行两套——@theme 映射是中间层，不是真源
2. 两套主题机制并存（`body[data-ds-dark-theme]` 与 `<html data-theme>`）—— ThemeToggle 切换
   后 dark 不一定生效
3. 现状只有 light/dark 两主题；design §8 计划 light/dark/high-contrast 三主题
4. 没有 vitest-axe 自动 a11y 扫描——手动核对 Radix a11y 不持续

§1 把 shadcn 集成从「能用原型」推进到「可批量迁组件」状态。

## What Changes
- 装首批 7 个 shadcn 组件依赖（dialog / dropdown-menu / form / select / tabs / tooltip /
  sonner）— 仅装包与 @/components/ui/，不写实际组件代码
- 引入 vitest-axe 作为自动化 a11y 扫描
- 统一现状 token 系统命名（保留双层结构，添加 shadcn 语义层）
- 统一主题机制：保留 `<html data-theme>`（新机制），废弃 `body[data-ds-dark-theme]` 与
  `lib/theme.ts` 旧路径
- 引入 high-contrast 第三主题（如果 design §8 仍然要求）
- 写 web-ui delta spec（MUST 条件 + Scenario）
- 不动 21 个现有 .module.css（保留到 §3 / §4 逐组件迁）
- 不动后端 DTO

## Impact
- 改文件：
  - `agent-web/frontend/package.json`、`package-lock.json`（加 shadcn 依赖 + vitest-axe）
  - `agent-web/frontend/vitest.setup.ts`（加 vitest-axe 集成）
  - `agent-web/frontend/src/index.css`（扩展 @theme 块，含 hc 第三主题）
  - `agent-web/frontend/src/styles/tokens.css` / `tokens-dark.css`（废弃或迁移）
  - `agent-web/frontend/src/lib/theme.ts`（废弃或迁移）
  - `agent-web/frontend/src/hooks/useThemeApplication.ts`（可能微调以支持 hc）
- 不动 21 个 .module.css
- 不动后端 DTO
- 不动 ReasoningEffortSelect.tsx（已迁完）

## Out of Scope
- Dialog / DropdownMenu / Form 等**实际使用**在 SettingsModal / Dropdown 等组件中（§2）
- 删 .module.css（§4 cleanup）
- 三主题之外的扩展（如主题色自定义 UI）

## 关联文件

- 设计：`docs/superpowers/specs/2026-09-18-shadcn-frontend-migration-design.md` §4
- 计划：`docs/superpowers/plans/2026-09-18-shadcn-frontend-migration.md` §B
- 原型报告：`docs/test-agent-demo/2026-09-18-shadcn-prototype/prototype-report.md` §3
- skill：`~/.dsh/skills/shadcn-tailwind/`