# Web UI 增量（shadcn-components-p2）

> 主 spec：`openspec/specs/web-ui/spec.md`
> 注：本次为样式层迁移（CSS Modules → Tailwind utility），不改变对外行为契约，
> 故 delta 只声明「样式方案」与「主题机制统一」两条 ADDED Requirement。

## ADDED Requirements

### Requirement: 前端样式统一为 Tailwind utility

MUST 把 `agent-web/frontend/src/components/` 下**全部** `*.module.css` 迁移为
Tailwind utility class，迁移后：

- 组件 `.tsx` MUST NOT `import styles from "*.module.css"`
- `*.module.css` 文件 MUST 全部删除
- 视觉表现 MUST 与迁移前一致（用 `--dsw-*` 派生的 shadcn 语义变量着色）

#### Scenario: 迁移后无 CSS Modules 引用

- **WHEN** 执行 `grep -rn "\.module\.css" agent-web/frontend/src/components`
- **THEN** 无任何匹配
- **AND** 前端 vitest / tsc / vite build 全绿

#### Scenario: 主题切换后视觉仍正确

- **WHEN** 用户在 light / dark / high-contrast 之间切换
- **THEN** 迁移后的组件颜色随 `--dsw-*` 变量变化
- **AND** 无硬编码颜色导致的主题错乱

### Requirement: 主题机制单一来源

MUST 把暗色主题的选择器从 `body[data-ds-dark-theme]` 统一为
`:root[data-theme="dark"]`，与 `useThemeApplication()` 写入的
`<html data-theme>` 对齐。

`src/lib/theme.ts` 的旧 API（`initTheme` / `applyTheme` / `toggleTheme` /
`detectInitialTheme` / `getStoredTheme` / `persistTheme`）MUST 被移除或废弃，
`src/main.tsx` MUST NOT 调用 `initTheme()`。

#### Scenario: 暗色主题经 settings 生效

- **WHEN** 用户在设置中选择「深色」（`general.appearance.preference = "dark"`）
- **THEN** `useThemeApplication` 把 `<html>` 的 `data-theme` 设为 `"dark"`
- **AND** `tokens-dark.css` 的 `:root[data-theme="dark"]` 选择器覆盖 `--dsw-static-*` 变量
- **AND** 不再依赖 `body[data-ds-dark-theme]` 属性

#### Scenario: high-contrast 主题变量完整

- **WHEN** `data-theme="hc"` 生效
- **THEN** `--dsw-static-*` 全部语义变量（surface / text / border / accent /
  neutral / warning / success / danger 各档）都被高对比度配色覆盖
- **AND** 正文与背景对比度 ≥ 7:1（WCAG AAA）