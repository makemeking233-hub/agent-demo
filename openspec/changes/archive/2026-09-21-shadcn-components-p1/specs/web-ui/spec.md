# Web UI 增量（shadcn-components-p1）

> 主 spec：`openspec/specs/web-ui/spec.md`
> 注：主 spec 中无 SettingsModal / ThemeToggle 相关 Requirement，
> 故本次全部为 ADDED（MODIFIED 会因找不到匹配 header 而 archive 失败）。

## ADDED Requirements

### Requirement: SettingsModal 使用 shadcn Dialog

MUST 使用 shadcn `Dialog` + `DialogContent` 替换原手搓 `<div role="dialog">`，
以获得 Radix 提供的 focus trap / Esc 关闭 / 焦点还原 / 外点击关闭 / portal 渲染。

对调用方 props 接口 MUST 保持不变：

```ts
interface SettingsModalProps {
  open: boolean;
  onClose: () => void;
  triggerElement?: HTMLElement | null;
  api: ChatApi;
  selection: ModelSelection;
  reasoningEfforts: ReasoningEffort[];
  onSelectionChange: (next: ModelSelection) => void;
  onReasoningEffortChange: (effort: string) => void;
}
```

`data-testid="settings-modal"` MUST 保留以兼容现有测试与自动化。

#### Scenario: 打开设置 modal

- **WHEN** 用户点击 trigger（`open` 变为 true）
- **THEN** 渲染 shadcn Dialog，`data-testid="settings-modal"` 存在
- **AND** 左侧渲染 4 个 nav 项（general / models / plugins / agent-presets）+ "设置" 标题
- **AND** 右侧渲染 SettingsContent（默认 `settings-content-general`）

#### Scenario: 关闭 modal

- **WHEN** 用户点击关闭按钮
- **THEN** `onClose` 被调用一次
- **AND** Radix 自动把焦点还原到 trigger 元素

### Requirement: ThemeToggle 支持 high-contrast 主题

`AppearancePreference` MUST 支持 `"light" | "dark" | "system" | "hc"` 四个值。
`useThemeApplication` MUST 把 `preference="hc"` 映射为 `<html data-theme="hc">`。
`ThemeToggle` MUST 在 preference 为 hc 时显示高对比度图标与「高对比度」标签。

#### Scenario: 用户选择高对比度

- **WHEN** 用户点击 `appearance-card-hc`
- **THEN** settings store 的 `general.appearance.preference` 被 patch 为 `"hc"`
- **AND** `useThemeApplication` 把 `<html data-theme>` 设为 `"hc"`
- **AND** `src/index.css` 中 `[data-theme="hc"]` 选择器覆盖 `--dsw-*` 颜色变量

#### Scenario: 保留跟随系统

- **WHEN** 用户选择「跟随系统」（`preference="system"`）
- **THEN** `data-theme` 跟随 `prefers-color-scheme`（dark → `"dark"`，否则 `"light"`）
- **AND** 新增 hc 不破坏既有 system 行为

### Requirement: 淘汰无引用的 Dropdown 组件

`Dropdown.tsx` / `Dropdown.test.tsx` / `Dropdown.module.css` MUST 被删除
（仅由 ReasoningEffortSelect 使用，而后者已迁至 shadcn Popover + RadioGroup）。

#### Scenario: 代码库中不再有 Dropdown 引用

- **WHEN** 执行 `grep -r "components/Dropdown" agent-web/frontend/src`
- **THEN** 无任何匹配
- **AND** 前端测试与构建仍全绿