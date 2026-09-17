# 设计：picker-dsh-flow

## Context

DSH WorkspacePicker 真实形态 = Menu + Flow Slot：点 + 弹 Menu，列已有 workspaces + Add workspace...，选 Add workspace... → 关闭 menu + 打开 native chooser，选完直接 createWorkspace。

我们当前是单 modal：硬塞 workspaces + path + name，OS picker 用 PowerShell WinForms（3-5s 慢）。

## Goals / Non-Goals

**Goals**:
- 完全对齐 dsh picker 形态：Menu + Flow Slot + 错误 Modal 分离
- 用 WPF OpenFolderDialog 替代 WinForms FolderBrowserDialog（启动 ~1s vs ~3-5s）
- 保留路径输入 + reveal 作为 fallback（picker-jna 前的过渡方案）

**Non-Goals**:
- 不引入 JNA / koffi（picker-jna change 处理）
- 不实现 in-app Miller 列 browse（DSH 完整方案）
- 不改 settings.yaml schema

## Decisions

### D1. 前端：Menu + picker 触发

Sidebar `+` 点击 → 弹 Popover/Menu（用现有 Dropdown 组件）：
```
┌─────────────────────────────┐
│ AGENT-DEMO       ✓         │
│ MD-MAIN                     │
│ ─────────────────────────  │
│ +  Add workspace...        │
└─────────────────────────────┘
```

选已有 workspace → 直接 `onPick(id)`（无需 picker）
选 "Add workspace..." → 关闭 menu + 调 picker

### D2. picker 失败 → 单独 Modal

```
Modal "folder error":
  错误信息
  [Cancel] [Choose again]   ← Choose again 重开 picker
```

不阻塞 menu。

### D3. 后端 picker：WPF OpenFolderDialog

```powershell
Add-Type -AssemblyName PresentationFramework
$dlg = New-Object Microsoft.Win32.OpenFolderDialog
$dlg.Title = 'Select Workspace Directory'
if ($dlg.ShowDialog() -eq $true) { Write-Output $dlg.FolderName }
```

vs 现在 WinForms FolderBrowserDialog：
- Add-Type WPF 首次 JIT ~0.5s（vs WinForms 2s）
- OpenFolderDialog 是 Vista+ 风格（DPI aware）
- 启动总时间 ~1-1.5s（vs 3-5s）

### D4. picker-async 端点保留 + 加 `kind` 参数

`POST /api/workspaces/pick-folder?kind=modern` 用 WPF；默认仍 WinForms（向后兼容）
reveal 端点不动（explorer /select，~50ms）

### D5. 路径输入框仍保留在 picker dialog（备选）

DSH 的 Edit path 功能：用户可以用 reveal 调起资源管理器 → 复制路径 → 粘到输入框 → 确认

## Risks / Trade-offs

| # | 风险 | 缓解 |
|---|------|------|
| R1 | WPF OpenFolderDialog 仍是 PowerShell + .NET JIT | 启动 ~1s（vs 3-5s），picker-jna 后续彻底解决 |
| R2 | Menu popover 定位与 dsh 不完全一致 | 沿用项目 Dropdown 组件；后续可优化 |
| R3 | 用户选已有 workspace 时跳过 picker | 这是 dsh 的标准行为，简化路径 |

## Migration Plan

UI 形态变更：原 modal 用户需要适应 menu + picker 流程。
`STORAGE_KEY` 路径记忆保留。

## Open Questions

1. Menu 锚点位置：TopBar `+` 下方 vs Sidebar header `+`？→ **Sidebar header**（与 dsh sidebar.workspaces 一致）
2. 是否同时支持路径输入？→ **保留作为 fallback**（picker 失败时仍可手输）
