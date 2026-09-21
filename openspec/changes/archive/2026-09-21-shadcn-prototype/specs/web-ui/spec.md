# Web UI 增量（shadcn 原型）

> 主 spec：`openspec/specs/web-ui/spec.md`

## ADDED Requirements

### Requirement: ReasoningEffortSelect 使用 shadcn Popover + RadioGroup

MUST 使用 shadcn `Popover` + `RadioGroup` 替换原 `<details>` 元素手搓实现。
该 requirement 仅在 prototype 阶段有效；后续 §2/§3 change 将进一步把组件迁完。

prototype 阶段针对的 `agent-web/frontend/src/components/ReasoningEffortSelect.tsx`
使用 shadcn `Popover` + `RadioGroup` 替换原 `<details>` 元素手搓实现。
对外接口保持不变：

```ts
interface ReasoningEffortSelectProps {
  options: ReasoningEffort[];
  value?: string;
  onChange: (effort: string) => void;
}
```

#### Scenario: 用户打开 effort 选择下拉

- **WHEN** 用户点击 trigger
- **THEN** 弹出 shadcn `Popover` 内容区
- **AND** 内容区列出 `options` 每一项为 `RadioGroupItem`

#### Scenario: 用户切换三主题视觉一致

- **WHEN** 用户在 light / dark / high-contrast 之间切换 `data-theme`
- **THEN** trigger 与内容区的颜色随主题变量变化，视觉与现状一致