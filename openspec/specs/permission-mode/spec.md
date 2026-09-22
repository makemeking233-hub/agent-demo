# permission-mode Specification

## Purpose
TBD - created by archiving change add-permission-mode-dropdown. Update Purpose after archive.
## Requirements
### Requirement: 权限模式选择

系统 SHALL 在 web 输入区提供权限模式下拉，其取值定义已迁移到 `sandbox-policy` capability（详见 `openspec/specs/sandbox-policy/spec.md`）：4 档 dsh 命名 `plan` / `ask` / `danger-full` / `dontAsk`，缺省 `plan`；后端 `PermissionMode.from()` 接受新旧两套 wire value 并自动 normalize 到 4 档。

#### Scenario: 下拉默认 Plan

- **WHEN** web UI 加载且 `settings.yaml` 无 `general.permission.mode`
- **THEN** 权限下拉 SHALL 显示 `Plan`
- **AND** 当前会话 SHALL 按 `plan` 裁决

#### Scenario: 旧 wire value 自动迁移

- **WHEN** `settings.yaml` 含旧值 `full_access`
- **THEN** `SettingsService.read()` SHALL 改写为 `danger-full`
- **AND** SHALL 记录 INFO 日志 `migrated permission mode from 'full_access' to 'danger-full'`

#### Scenario: 前端从 settings 读取

- **WHEN** 用户在设置面板选择 `danger-full`
- **THEN** `ChatPanel` SHALL 从 `useSettingsStore` 读取该值
- **AND** `POST /api/chat/send` 的 `permission_mode` 字段 SHALL 透传 `danger-full`

