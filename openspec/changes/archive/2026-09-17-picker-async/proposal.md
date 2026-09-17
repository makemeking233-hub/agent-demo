## Why

当前 WorkspacePickerController 同步阻塞 `process.waitFor(5 分钟)`，导致：
1. 前端按钮一直"选择中..."直到 PowerShell 退出
2. 用户在这期间无法操作其他 UI（虽然 modal 是 fixed，但心理上的"卡死"感强）
3. 即使加了 timeout，5 分钟的等待仍然不可接受

参考 dsh web 的 `directory-picker-native` 实现，关键思想：**进程异步化**（PowerShell 在 child process 跑，主进程立即返回 task_id）+ **reveal 快速备选**（explorer /select 几乎瞬时返回）。

## What Changes

- 后端 `WorkspacePickerController.pickFolder()` 改为立即返回 202 + `task_id`
- 后台 thread 启动 PowerShell 进程，结果写入 task store
- 前端 `GET /api/workspaces/pick-folder/{task_id}` 轮询状态
- 前端 modal 加 "在资源管理器中显示" 备选（调 `/api/settings/reveal` 复用）
- 超时 5 分钟 → 自动 destroy process + 标记任务 cancelled
- AbortSignal 支持（前端 Esc / 关闭 modal → 后端 destroy process）

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `settings`: workspace 创建流程改为异步轮询模式

## Impact

- 后端：`WorkspacePickerController` 改造 + `PickerTaskStore` 新增
- 前端：`WorkspacePickerModal` 改用 task_id + 轮询 + 加 reveal 按钮
- 测试：相应更新（前端 mock fetch 适配异步 API）
- 性能：用户可在 PowerShell 慢启动期间操作其他 UI（前端不再"卡"）

## Out of Scope

- 去掉 PowerShell 改用 JNA 直调 Win32 `IFileOpenDialog`（单独 change：`picker-jna`）
- 跨平台 picker 优化（macOS osascript、Linux zenity 保留现状）
