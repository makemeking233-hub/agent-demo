# session-workspace Specification

## Purpose
TBD - created by archiving change add-workspaces-and-rename. Update Purpose after archive.
## Requirements
### Requirement: 会话重命名

系统 SHALL 允许用户把一个会话重命名为自定义标题，并持久化覆盖自动派生的标题。

#### Scenario: 重命名成功

- **WHEN** 客户端发送 `POST /api/sessions/{session_id}/rename`，请求体 `{"title":"我的项目"}`，且会话存在
- **THEN** 服务端写会话元数据侧车 `<session_id>.meta.json{title}`，返回 `200 {"ok":true,"title":"我的项目"}`
- **AND** 之后 `GET /api/sessions` 中该会话的 `title` 为 `我的项目`

#### Scenario: 重命名未知会话

- **WHEN** 客户端对不存在的会话发送 `POST /api/sessions/{id}/rename`
- **THEN** 服务端返回 `404 Not Found`，响应体 `{"error":"session_not_found"}`

#### Scenario: 空标题被拒

- **WHEN** 请求体 `title` 为空或全空白
- **THEN** 服务端返回 `400 Bad Request`，响应体 `{"error":"title_empty"}`
- **AND** 不改写既有标题

### Requirement: 工作区列表

系统 SHALL 暴露工作区列表，供前端侧边栏分组件渲染。

#### Scenario: 列出工作区

- **WHEN** 客户端发送 `GET /api/workspaces`
- **THEN** 服务端返回 `200 OK`，响应体为数组，每项含 `name`/`dir`/`sessionCount`/`lastActiveAt`
- **AND** 至少包含默认工作区 `agent-demo`

### Requirement: 创建工作区

系统 SHALL 允许用户创建一个新的工作区（真实运行目录），并把该工作区的会话存储与运行目录路由到该目录。

#### Scenario: 创建工作区成功

- **WHEN** 客户端发送 `POST /api/workspaces`，请求体 `{"name":"md-main","dir":"E:\\md-main"}`，且 `dir` 是存在的绝对目录、`name` 唯一
- **THEN** 服务端创建 `workspaces/md-main/meta.json{name,dir,created_at}` 与 `workspaces/md-main/sessions/`，返回 `200 {"ok":true,"name":"md-main","dir":"E:\\md-main"}`

#### Scenario: 目录不存在被拒

- **WHEN** 请求体 `dir` 不是存在的目录
- **THEN** 服务端返回 `400 Bad Request`，响应体 `{"error":"dir_not_found"}`

#### Scenario: 名称重复被拒

- **WHEN** 请求体 `name` 已存在（大小写不敏感）
- **THEN** 服务端返回 `409 Conflict`，响应体 `{"error":"workspace_exists"}`

### Requirement: 工作区会话存储与运行目录

系统 SHALL 让归属某工作区的会话，其存档落该工作区 `workspaces/<name>/sessions/`，且该工作区会话的 `file` 工具相对路径以该工作区 `dir` 为基准（`cwd = dir`）。

#### Scenario: 指定工作区新建会话

- **WHEN** 客户端发送 `POST /api/chat/send`，请求体含 `"workspace":"md-main"` 且该工作区存在
- **THEN** 该会话的 JSONL 落入 `workspaces/md-main/sessions/<sessionId>.jsonl`
- **AND** 该会话 agent 运行的工作目录为 `E:\md-main`

#### Scenario: 缺省工作区

- **WHEN** 客户端发送 `POST /api/chat/send`，请求体不含 `workspace`
- **THEN** 会话落入默认工作区 `agent-demo`（顶层 `sessions/`），运行目录为项目根

#### Scenario: 未知工作区被拒

- **WHEN** 请求体 `workspace` 不是已登记的工作区名
- **THEN** 服务端返回 `400 Bad Request`，响应体 `{"error":"workspace_not_found"}`

### Requirement: 按工作区列会话

系统 SHALL 支持按工作区列出会话。

#### Scenario: 指定工作区列会话

- **WHEN** 客户端发送 `GET /api/sessions?workspace=md-main`
- **THEN** 服务端只返回该工作区 `sessions/` 下的会话摘要（title 优先自定义）
- **AND** 不含默认工作区的会话

#### Scenario: 缺省列出默认会话

- **WHEN** 客户端发送 `GET /api/sessions`（无 `workspace` 参数）
- **THEN** 服务端返回默认工作区 `agent-demo` 的会话摘要

### Requirement: 工作区侧栏分组与新建入口（web）

系统 SHALL 在 web 侧栏把会话按工作区分组展示，并提供「新建工作区」入口与会话重命名入口。

#### Scenario: 侧栏按工作区分组与头部新建

- **WHEN** web UI 加载且 `GET /api/workspaces` 返回多个工作区
- **THEN** 侧栏按工作区分组展示各工作区及其会话（会话标题优先自定义）
- **AND** 工作区头部显示 `+` 新建入口，点击打开「名称 + 目录路径」弹窗

#### Scenario: 会话行 ... 菜单提供重命名

- **WHEN** 用户点击某会话行的 `...` 按钮
- **THEN** 弹出下拉菜单，含「重命名」选项
- **AND** 点击「重命名」进入内联输入/弹窗，提交后调 `POST /api/sessions/{id}/rename` 并更新侧栏标题

#### Scenario: 新建工作区后侧栏刷新

- **WHEN** 用户确认新建工作区（名称 + 目录）
- **THEN** 服务端创建工作区后，侧栏新增该工作区分组并刷新
- **AND** 目录/名称非法的场景在弹窗内给出错误提示

