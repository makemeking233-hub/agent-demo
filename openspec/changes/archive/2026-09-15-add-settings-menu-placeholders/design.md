# 设计：add-settings-menu-placeholders

> 与 [`docs/superpowers/specs/2026-09-15-add-settings-menu-design.md`](../../../docs/superpowers/specs/2026-09-15-add-settings-menu-design.md) §8 同源。

## Context

M1 的 SettingsNav 已有 4 项菜单（通用/模型/插件/Agent 预设）；M1 的 SettingsContent 路由表只把"通用"指向占位。M2 把"通用"接入具体设置项。本 M3 把"模型/插件/Agent 预设"三个菜单的实质内容接入，让用户在设置面板里能完成"看到全部 4 个菜单 + 在每个菜单间切换"的完整体验。

## Goals / Non-Goals

**Goals**:
- "模型"菜单：复刻 TopBar 的 ModelSelect + ReasoningEffortSelect，共享同一 store
- "插件" / "Agent 预设"菜单：显示 SettingsEmpty 占位
- SettingsContent 路由表完整 4 项
- e2e 验证 4 菜单切换正常

**Non-Goals**（M3 不做）：
- 把 TopBar 的模型下拉删除（保持现状）
- 默认模型写入 settings.yaml（仍走 localStorage，与 M2 一致）
- 插件市场的实质功能（独立 change）
- Agent 预设的实质功能（独立 change）

## Decisions

### D1. 模型菜单：复刻而非挪移

```typescript
function ModelsSection() {
  const model = useAppStore(s => s.model);
  const effort = useAppStore(s => s.reasoningEffort);
  return (
    <>
      <Row label="默认模型"><ModelSelect api={api} value={model} onChange={onModelChange} /></Row>
      <Row label="默认推理强度"><ReasoningEffortSelect value={effort} options={...} onChange={onEffortChange} /></Row>
    </>
  );
}
```

**为什么不挪到设置面板**：保持 TopBar 现状，减少风险；用户习惯两个地方都能改。

### D2. 占位组件：通用 SettingsEmpty

```typescript
function SettingsEmpty({ icon: Icon, title, description }: Props) {
  return (
    <div className="empty">
      <Icon size={32} />
      <h3>{title}</h3>
      <p>{description}</p>
      <p className="hint">将在后续版本接入</p>
    </div>
  );
}
```

**为什么不每页单独写**：3 个占位 + 未来可能的"语音"等菜单都需要同样形态；提取一次。

### D3. 菜单对应表

| 菜单 id | 组件 | 图标 |
|--------|------|------|
| `general` | `GeneralSection`（M2） | `Settings` |
| `models` | `ModelsSection` | `Box` |
| `plugins` | `PluginsSection` → `SettingsEmpty` | `Plug` |
| `agent-presets` | `AgentPresetsSection` → `SettingsEmpty` | `User` |

## Risks / Trade-offs

| # | 风险 | 缓解 |
|---|------|------|
| R1 | 模型菜单与 TopBar 不同步（不同 store 实例） | 复用 App.tsx 同一 `useAppStore` |
| R2 | 占位被误以为是完整功能 | 行内"将在后续版本接入"提示 |
| R3 | npx tsc 因新增 4 个文件而新增错误 | T6 跑 tsc --noEmit 验证 ≤ 7 |

## Migration Plan

无。

## Open Questions

1. **模型菜单未来是否要持久化到 settings.yaml**（替换 localStorage）？目前 YAGNI，独立 change。
2. **Agent 预设的占位是否要给个时间预期**（如"v0.4 接入"）？目前 YAGNI。
