## Why

agent-demo 当前在 `TopBar` 已预留「设置」齿轮按钮（`App.tsx:197` 用 `alert("设置 v0.2 接入")` 占位），用户实际无法访问任何设置。本次需要把占位替换为真正可用的设置面板基础设施——为后续 M2（接入具体设置项）、M3（菜单占位）提供 REST + SSE 热重载底座，让设置项的读写、跨标签同步、刷新页面持久化都能正常工作。

## What Changes

- 新增后端 `SettingsService` 读写 `~/.agent-demo/settings.yaml`（含原子替换 + revision 乐观锁）
- 新增后端 `SettingsController` REST 端点（GET/PATCH `/api/settings`）+ SSE 端点（GET `/api/settings/events`）
- 新增后端 `SettingsFileWatcher`（JDK WatchService）+ `SettingsChangeBroadcaster`（SseEmitter 注册表）
- 新增后端 `SettingsValidator`（字段枚举校验）
- 新增前端 `useSettingsStore`（模块级单例 + `useSyncExternalStore`）
- 新增前端 `SettingsModal` shell + `SettingsNav` 4 项路由 + `SettingsContent` 路由表
- 新增前端 `api/settings.ts` REST 客户端 + `settings-sse.ts` SSE 订阅封装
- 替换 `App.tsx:197` 的 `alert` 占位 → 真正打开 modal（含焦点回归）

## Capabilities

### New Capabilities
- `settings`: 用户可在 Web UI 设置面板读写持久化设置；变更通过 SSE 热重载跨标签同步；M1 仅产出 modal shell + 数据底座，具体设置项在 M2 接入。

### Modified Capabilities
（无；M1 不改既有 spec 的 REQUIREMENTS）

## Impact

- 后端：`agent-core` 新增 `com.example.agent.settings` 包；`agent-web` 新增 `SettingsController` + `SettingsSseController`
- 前端：`agent-web/frontend/src` 新增 `components/SettingsModal|SettingsNav|SettingsContent.tsx`、`hooks/useSettingsStore.ts`、`api/settings.ts`、`lib/settings-sse.ts`
- 现有 `App.tsx:197` 一行替换
- 现有 `TopBar.tsx:26` 不变（齿轮按钮已存在）
- 文件系统：`~/.agent-demo/settings.yaml`（首次启动自动创建）
- 测试：新增 ~13 个单测 + 1 个 e2e；不影响现有测试
