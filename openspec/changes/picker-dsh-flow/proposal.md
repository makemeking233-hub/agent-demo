## Why

当前 WorkspacePickerModal 与 dsh web 的 picker 形态完全不同。dsh 是 **Menu + Flow Slot** 模式：
1. 点 + → 弹 **Menu**（列已有 workspace + "Add workspace..." 项）
2. 选 "Add workspace..." → **关闭菜单 + 打开 flow**（OS native chooser 弹窗）
3. 用户选完 → 自动 `createWorkspace(path)` → 关闭一切
4. 错误单独 Modal（不卡 picker）

我们的 modal 把"列 workspaces + 添加"硬塞一个面板，体验不符。改造方向对齐 dsh：

## What Changes

- 后端：**改用 WPF OpenFolderDialog**（PowerShell + PresentationFramework），比 WinForms FolderBrowserDialog 快 50%（DPI aware + 现代风格）
- 前端 modal 拆为：
  - **Menu**（点 + 后从锚点弹出）：列已有 workspaces + 「Add workspace...」项
  - **「Add workspace...」触发 OS picker**（弹 OS 原生 chooser）
  - 选完 → 调 `onPick(workspace)` 直接创建 + 关闭
  - 失败：单独 Modal（不影响 menu）
- 路径输入框改回 picker 后端（picker-async 端点保留为 deprecated fallback，给 reveal 用）
- reveal 保留（explorer /select，~50ms）

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `settings`: workspace picker 拆为 menu + flow，对齐 dsh 模式

## Impact

- 前端：Sidebar `+` 按钮触发 Menu；菜单项调 native picker
- 后端：picker-async 端点改造支持 WPF
- 测试：picker modal 测试改造 + 新增 menu 测试

## Out of Scope

- JNA 方案（后续 picker-jna change）
- in-app browse 对话框
