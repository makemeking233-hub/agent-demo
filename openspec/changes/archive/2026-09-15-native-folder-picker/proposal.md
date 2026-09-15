## Why

当前 WorkspacePickerModal 是浏览器内嵌的目录树浏览（依赖后端 listDir/mkdir API），功能完备但 UI 复杂、与 OS 原生文件管理器割裂。用户期望：点"+"直接调起 OS 资源管理器选文件夹，体验更原生。

## What Changes

- 后端新增 `POST /api/workspaces/pick-folder`：调 OS 文件选择对话框（Win/Mac/Linux），阻塞等待用户选择（最多 5 分钟），返回绝对路径或 null（取消）
- 前端 `WorkspacePickerModal` 大幅简化：删除目录树/面包屑/mkdir/历史/排序等；保留"选文件夹"按钮（调后端）+ 已选路径展示 + 工作区名称输入 + 确认
- `FsController` 的 listDir / mkdir / getDrives / getQuickAccess 标记为 deprecated（暂不删，先简化前端）
- `api/fs.ts` 移除对应的 client 调用（保留类型）
- 测试：后端 + 前端对应更新

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `workspace-management`: workspace 创建流程改为 OS 文件选择对话框驱动

## Impact

- 后端：`agent-web` 新增 `WorkspacePickerController`；`FsController` 暂保留
- 前端：`WorkspacePickerModal` 大幅简化（约 766 行 → ~150 行）；`api/fs.ts` 简化
- 测试：相应更新
- 依赖：依赖 OS 工具（Windows PowerShell 5+/7+、macOS osascript、Linux zenity 或 kdialog）
