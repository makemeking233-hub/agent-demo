# Web UI 增量（shadcn-infra）

> 主 spec：`openspec/specs/web-ui/spec.md`

## ADDED Requirements

### Requirement: 统一 token 系统与主题机制

MUST 把现状 token 系统（`--dsw-static-*` + `--dsw-alias-*`）统一为单层 shadcn 语义命名
（`--background` / `--foreground` / `--primary` / `--border` / `--destructive` 等）。
现状 `src/styles/tokens.css` 与 `src/styles/tokens-dark.css` 仅作为颜色值出处保留；
不再有别名层。

MUST 废弃 `body[data-ds-dark-theme]` 主题机制；统一使用 `<html data-theme>`（已存在的
`useThemeApplication` 机制）。`lib/theme.ts` 的 `toggleTheme` / `applyTheme` 等旧 API
可保留作为内部实现，但不再用于切换 dark。

#### Scenario: dark 主题切换后页面背景与文字正确变化

- **WHEN** 用户在 settings 选择 `appearance.preference=dark`
- **THEN** `<html data-theme="dark">` 生效
- **AND** 所有 `--dsw-static-*` 颜色变量被 `.dark` 选择器覆盖（或迁移到 `:root[data-theme="dark"]`）
- **AND** 三主题（light / dark / hc）切换视觉一致

#### Scenario: high-contrast 主题可启用

- **WHEN** 用户选择 `appearance.preference=hc`（如果 §1 决定保留三主题）
- **THEN** `<html data-theme="hc">` 生效
- **AND** 对比度 ≥ 7:1（WCAG AAA）

### Requirement: 引入 vitest-axe 自动化 a11y 扫描

MUST 在 `vitest.setup.ts` 中集成 `vitest-axe` 提供 `expect(...).toHaveNoViolations()` 断言。

#### Scenario: 渲染 ReasoningEffortSelect 触发 axe 检查

- **WHEN** 测试代码 `await expect(screen.getByRole(...)).toHaveNoViolations()`
- **THEN** axe 扫描该 DOM 节点
- **AND** 报告 0 个违规（或显式列出现有违规）