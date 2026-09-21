# Web UI 增量（shadcn-components-p1）

> 主 spec：`openspec/specs/web-ui/spec.md`

## MODIFIED Requirements

### Requirement: SettingsModal 使用 shadcn Dialog

MUST 使用 shadcn `Dialog` + `DialogContent` 替换原 `<div role="dialog">` 手搓实现。
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

#### Scenario: 用户打开设置 modal

- **WHEN** 用户点击 trigger
- **THEN** 弹出 shadcn Dialog，自动 focus trap + Esc 关闭 + 关闭时焦点回到 trigger
- **AND** 内容区渲染左侧 nav + 右侧 content（来自 SettingsContent）

#### Scenario: 关闭 modal 后焦点回到 trigger

- **WHEN** 用户按 Esc 或点 mask 关闭
- **THEN** Radix 自动还原焦点到 trigger 元素

### Requirement: Dropdown 使用 shadcn DropdownMenu

MUST 使用 shadcn `DropdownMenu` + `DropdownMenuTrigger` + `DropdownMenuContent`
替换原 `<details>` 元素手搓实现。保留 `Dropdown` 组件作为**兼容 re-export 层**
（4 处现有使用方暂不强制迁移，§3 统一推进）。

#### Scenario: Dropdown 打开下拉

- **WHEN** 用户点击 trigger
- **THEN** 弹出 shadcn DropdownMenu
- **AND** 自动外点击关闭 + Esc 关闭 + 焦点还原

### Requirement: ThemeToggle 三选项含 high-contrast

MUST 在 `ThemeToggle.tsx` 提供 light / dark / hc 三选项，与现有 useThemeApplication
机制打通。hc 主题选择器 `[data-theme="hc"]` 已在 §1 index.css 提供。

#### Scenario: 用户选择 high-contrast 主题

- **WHEN** 用户在 ThemeToggle 选择 "高对比度"
- **THEN** `<html data-theme="hc">` 被设置
- **AND** 现状 `--dsw-*` 颜色变量被高对比度配色覆盖（来自 index.css [data-theme="hc"]）

#### Scenario: 主题选择持久化

- **WHEN** 用户选择主题后刷新页面
- **THEN** 通过 settings store 的 appearance.preference 持久化（已有机制）