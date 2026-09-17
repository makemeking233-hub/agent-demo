## Why

当前 picker 流程「点 + → 弹 modal → 调 PowerShell FolderBrowserDialog（3-5s 启动）→ 选文件夹」卡 UI。dsh 是"in-app browse"（Miller 列），agent-demo 没那资源。简化方案：**砍 dialog，纯路径输入**（DSH 的 Edit path 模式）+ reveal 按钮（让用户到资源管理器复制路径粘回来）。

## What Changes

- 后端：复用现有 `/api/settings/reveal`（explorer /select, ~50ms）
- 前端 modal 简化：**无 dialog 按钮**，只剩「工作区名称 + 路径」两输入
- 新增「在资源管理器中显示」按钮 → 用户可右键复制路径或自己导航
- 路径输入支持 Tab 自动补全 basename 为 name
- 保留 picker-async 的 task_id 端点（deprecated 但可用作 fallback）

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `settings`: workspace picker 简化为「路径输入 + reveal 快速备选」

## Impact

- 前端：`WorkspacePickerModal.tsx` 删 pick folder 按钮 + 流程
- 后端：无改动（复用 reveal 端点）
- 测试：picker modal test 改造

## Out of Scope

- JNA 直调 Win32（后续 picker-jna change）
- in-app 文件浏览器（与 dsh 完整 browse 不一致）
