## Why

TopBar 当前直接把"浅色/深色/跟随系统"三张并排卡片铺开，占用大量横向空间、视觉杂乱。需要：① TopBar 改为单图标按钮（Sun/Moon 动态），② 3 张卡片移到 Popover 内，③ 卡片视觉更精致。

## What Changes

- `ThemeToggle` 改造为单图标按钮（动态 Sun/Moon 图标 + 当前 preference 提示）
- 点击按钮 → 弹出 Popover（右上方对齐按钮）
- Popover 内渲染 `AppearanceCards`（更精致的卡片样式：圆角、阴影、hover）
- Popover 关闭路径：点击外部 / 选中后自动关闭 / Esc
- `AppearanceCards` 视觉优化：圆角 12px、阴影、间距优化

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `settings`: settings modal 内外观项 UI 行为不变，但 TopBar 上的入口改为 popover 模式

## Impact

- 前端：`agent-web/frontend/src/components/ThemeToggle.tsx` 改造；`AppearanceCards.tsx` 视觉样式优化；新增 `ThemePopover.tsx`
- 不动后端、不动 settings.yaml schema、不动 store
- TopBar.tsx 调用点不变（仍调 `<ThemeToggle />`）
- 测试：ThemeToggle.test.tsx 改造 + 新增 ThemePopover.test.tsx
