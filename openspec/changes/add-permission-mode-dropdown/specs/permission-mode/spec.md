## ADDED Requirements

### Requirement: 权限模式选择

系统 SHALL 在 web 输入区提供权限模式下拉（`read_only` / `workspace_write` / `full_access`），供用户预先设定当前会话的权限基准；缺省为 `read_only`。

#### Scenario: 下拉默认 Read Only

- **WHEN** web UI 加载且尚未选择过模式
- **THEN** 权限下拉显示 `Read Only`
- **AND** 当前会话按 `read_only` 裁决

#### Scenario: 下拉切换模式

- **WHEN** 用户在下拉选择 `Full access`
- **THEN** 前端把该模式发送到后端（`POST /api/chat/{streamId}/permission`）
- **AND** 后续工具调用按 `full_access` 裁决

### Requirement: 权限模式裁决策略

系统 SHALL 按权限模式将工具类别映射到 `allow` / `ask` 默认裁决，决定 `PermissionManager.decide` 的非敏感路径结果。

#### Scenario: Read Only 裁决

- **WHEN** 模式为 `read_only` 且工具类别为 READ（如 ReadFile / Ls）
- **THEN** 裁决为 `allow`（直接执行，不弹窗）
- **AND** 类别为 WRITE（WriteFile / EditFile）、SHELL 或 OTHER 时裁决为 `ask`（走 `permission_request`）

#### Scenario: Workspace Write 裁决

- **WHEN** 模式为 `workspace_write` 且 WRITE 工具的目标路径在工作目录内
- **THEN** 裁决为 `allow`
- **AND** 目标路径在工作目录外时裁决为 `ask`
- **AND** SHELL 或 OTHER 类别仍裁决为 `ask`

#### Scenario: Full Access 裁决

- **WHEN** 模式为 `full_access`
- **THEN** 所有类别（READ / WRITE / SHELL / OTHER）裁决为 `allow`

### Requirement: 工作区写入边界

在 `workspace_write` 模式下，系统 SHALL 依据会话工作目录（`ToolContext.workingDirectory()`）判定 WRITE 工具的目标路径是否在工作区边界内。

#### Scenario: 工作区内写入放行

- **WHEN** WRITE 工具目标路径位于会话工作目录内（或其子目录）
- **THEN** 裁决为 `allow`

#### Scenario: 工作区外写入询问

- **WHEN** WRITE 工具目标路径位于会话工作目录之外
- **THEN** 裁决为 `ask`（即使模式为 `workspace_write`）

### Requirement: 敏感路径处理

系统 SHALL 对敏感路径（`**/.ssh/**`、`**/.env*`、`**/*.pem`、`**/*credentials*`）在 `read_only` / `workspace_write` 模式下强制 `ask`；`full_access` 模式下放行。

#### Scenario: 非 Full Access 敏感路径询问

- **WHEN** 模式为 `read_only` 或 `workspace_write`，且工具目标路径命中敏感路径模式
- **THEN** 裁决为 `ask`

#### Scenario: 全权限敏感路径放行

- **WHEN** 模式为 `full_access`，且工具目标路径命中敏感路径模式
- **THEN** 裁决为 `allow`（仍受工具级 DENY 兜底）

### Requirement: 权限模式实时切换

系统 SHALL 提供 `POST /api/chat/{stream_id}/permission`，在会话进行中切换该流的权限模式。

#### Scenario: 合法模式切换成功

- **WHEN** 客户端发送 `POST /api/chat/{stream_id}/permission`，请求体 `{"mode": "full_access"}`，且 `stream_id` 有效
- **THEN** 服务端返回 `200 OK`，响应体 `{"ok": true, "mode": "full_access"}`
- **AND** 该流后续工具调用按新模式裁决

#### Scenario: 非法模式被拒

- **WHEN** 请求体 `mode` 不是 `read_only` / `workspace_write` / `full_access` 之一
- **THEN** 服务端返回 `400 Bad Request`，响应体 `{"error": "invalid_mode"}`

#### Scenario: 未知流被拒

- **WHEN** `stream_id` 不存在
- **THEN** 服务端返回 `404 Not Found`，响应体 `{"error": "stream_not_found"}`

### Requirement: 初始权限模式

系统 SHALL 在 `POST /api/chat/send` 请求体接受可选的 `permission_mode` 字段，作为该会话的初始模式。

#### Scenario: 提供初始模式

- **WHEN** 客户端发送 `POST /api/chat/send`，请求体含 `"permission_mode": "workspace_write"`
- **THEN** 服务端按 `workspace_write` 为该流配置初始策略，并正常启动会话

#### Scenario: 缺省为 Read Only

- **WHEN** 客户端发送 `POST /api/chat/send`，请求体不含 `permission_mode`
- **THEN** 服务端按缺省 `read_only` 配置该流

#### Scenario: 非法初始模式被拒

- **WHEN** 请求体 `permission_mode` 不是三个合法值之一
- **THEN** 服务端返回 `400 Bad Request`，不创建流

### Requirement: 工具级拒绝兜底

系统 SHALL 保证权限模式的放行 SHALL NOT 覆盖工具级 `DENY`（`isDestructive`、shell 黑名单等终态拒绝）。

#### Scenario: 全权限下破坏性工具仍被拒

- **WHEN** 模式为 `full_access`，但工具的 `checkPermissions`（或 shell 黑名单）返回 `DENY`
- **THEN** 该工具调用被拒绝，不进入确认流程

#### Scenario: 拒绝后不弹窗

- **WHEN** 工具级裁决为 `DENY`
- **THEN** 服务端不推送 `permission_request`，直接向模型返回失败
