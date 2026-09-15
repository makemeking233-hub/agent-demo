## Why

M1 已产出设置面板的 modal shell 与数据底座（settings 持久化、REST/SSE API、`useSettingsStore`）。M2 需要把 4 个具体设置项接入"通用设置"菜单，并提供「在文件管理器中显示」按钮，让用户能真正读写外观/权限/语言/Enter 行为。

## What Changes

- 后端 `SettingsValidator` 增加三字段枚举校验（appearance.preference、permission.mode、enterBehavior.mode）
- 后端 `SettingsController` 增加 3 个 PATCH 端点（appearance/permission/enterBehavior）+ revision 乐观锁
- 后端新增 `GET /api/settings/file-path` + `POST /api/settings/reveal`（跨平台 reveal，命令白名单）
- 前端 `AppearanceCards` 三卡片组件 + `useThemeApplication` hook + 重构 `ThemeToggle` 为壳子
- 前端 `PermissionModeSelect` / `LanguageSelect` / `EnterBehaviorSelect` 三个下拉组件
- 前端 `Composer` 接入 Enter 行为三种分支（send/queue/newSession）+ 内存 queue 简化实现
- 前端「在文件管理器中显示」按钮 + 「复制路径」dropdown + clipboard + Toast
- 把 4 个组件接入 SettingsModal 内容区
- 测试：四件套 + e2e

## Capabilities

### New Capabilities
（无新增 capability；属于 `settings` capability 的扩展）

### Modified Capabilities
- `settings`: 增加 4 个设置项的具体 REQUIREMENTS（外观三态/权限四态/语言二态/Enter 行为三态）+ reveal/file-path 端点 REQUIREMENTS

## Impact

- 后端：`agent-core` 新增 `SettingsRevealController`；`SettingsValidator` / `SettingsController` 扩展
- 前端：`agent-web/frontend/src` 新增 `AppearanceCards`、`PermissionModeSelect`、`LanguageSelect`、`EnterBehaviorSelect`、`OpenConfigButton`；`Composer.tsx` 改造（约 +150 行）；`ThemeToggle.tsx` 改造为壳子
- 测试：`docs/test-agent-demo/<日期>-settings-modal-v0-2/` 四件套
- 行为变化：`Composer` 的 Enter 处理逻辑变更（用户快捷键）
