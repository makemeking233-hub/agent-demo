## Why

M1 已建好 modal shell + 4 项菜单路由（其中"通用"由 M2 接入具体设置项）；M3 把"模型/插件/Agent 预设"三个菜单的实质内容接入，让用户在设置面板里能完成"看到全部 4 个菜单 + 在每个菜单间切换"的完整体验，避免视觉残缺。

## What Changes

- 新增 `ModelsSection`：在"模型"菜单复刻 TopBar 的 `ModelSelect` + `ReasoningEffortSelect`（共享 App store，不动 TopBar 位置）
- 新增 `PluginsSection` 占位页（Plug 图标 + "将在后续版本接入"）
- 新增 `AgentPresetsSection` 占位页（User 图标 + "将在后续版本接入"）
- 抽出 `SettingsEmpty` 通用占位组件（被 PluginsSection + AgentPresetsSection 复用）
- `SettingsContent` 路由表更新为完整 4 项
- 测试：e2e 跑 4 菜单切换

## Capabilities

### New Capabilities
（无；属于 `settings` capability 的扩展）

### Modified Capabilities
- `settings`: 增加"模型/插件/Agent 预设"三个菜单的具体 REQUIREMENTS（模型菜单含 ModelSelect + ReasoningEffortSelect；插件/Agent 预设显示占位）

## Impact

- 前端：`agent-web/frontend/src/components/` 新增 `ModelsSection.tsx` / `PluginsSection.tsx` / `AgentPresetsSection.tsx` / `SettingsEmpty.tsx`
- `SettingsContent.tsx` 路由表从 1 项扩展为 4 项
- 现有 `App.tsx` / `TopBar.tsx` / `ModelSelect.tsx` / `ReasoningEffortSelect.tsx` 不动（TopBar 仍显示模型下拉，"模型"菜单里复刻一份）
