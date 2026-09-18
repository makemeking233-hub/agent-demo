# workspace-entity Spec Deltas (align-dsh-workspace)

## ADDED Requirements

### Requirement: Workspace 用 fs.realpath 规范化 + 同 canonical path 1 record

`WorkspaceStore.create(dir)` SHALL 用 `fs.realpath` 规范化 `dir`，同 canonical path 最多 1 record（已存在则复用，不创建新 record）。

#### Scenario: 同一目录不同大小写 / 符号链接 / `..` 折叠

- **WHEN** 用户先 `create(/home/user/projects/md-main)`，再 `create(/home/user/Projects/md-main)` 或 `create(/home/user/../user/projects/md-main)`
- **THEN** 第二次调用返回第一次创建的 workspace（title 不同），不创建新 record

#### Scenario: 不同路径同 basename

- **WHEN** 用户 `create(/repos/md-main)` 和 `create(/work/md-main)`
- **THEN** 创建两个 workspace，title 都派生为 `md-main`（同名 title 允许，路径区分）

### Requirement: Workspace 包含稳定 id + title 与 path 解耦

`Workspace` record SHALL 含 `id` (UUID) / `path` (realpath) / `title` (可独立重命名) / `createdAt` / `updatedAt` / `sessionIds[]`；`title` 与 `path` 完全解耦（同名 title 允许）。

#### Scenario: 重命名 workspace 不改 dir

- **WHEN** 用户在 Sidebar 右键 → 重命名，把 `md-main` 改为 `My Main Project`
- **THEN** dir 不变，meta.json title 更新为 `My Main Project`，UI 显示 `My Main Project` 但 chat 仍在 `/home/user/projects/md-main` 工作

### Requirement: 持久化 durable order + 拖拽重排

`WorkspaceStore` SHALL 持久化 workspace 顺序到 `workspaces/order.json`；支持 `insertBefore(name, beforeName)` 重新排序；`list()` 按此顺序返回。

#### Scenario: 显式创建的 workspace 排最前

- **WHEN** 用户 `create(/repos/new)`（已有 `md-main` 和 `agent-demo`）
- **THEN** `order.json` 写入 `["new", "md-main", "agent-demo"]`；`list()` 按此顺序返回

#### Scenario: insertBefore 持久化

- **WHEN** 用户拖拽 `agent-demo` 到 `md-main` 前面
- **THEN** `order.json` 更新为 `["agent-demo", "md-main", "new"]`；重启后 list() 仍按此顺序

### Requirement: 缺失目录容忍 (status='missing-dir')

`WorkspaceStore.list(name)` SHALL 返回 `Workspace.status` 为 `ok` 或 `missing-dir`；目录不存在时 record 保留，UI 标红 + 「重新连接」按钮。

#### Scenario: workspace 目录被删 / 移走

- **WHEN** workspace `md-main` 目录被用户 mv 到 `/tmp/old`
- **THEN** `list()` 仍返回该 workspace，`status='missing-dir'`；UI 标红 + 「目录已移动，请重新指定」按钮（重新 `create` 后复用 record）

### Requirement: Workspace 可删除（仅删 record）

`WorkspaceStore.delete(name)` SHALL 删 `<agentDataDir>/workspaces/<name>/` 目录（含 meta.json + order.json 中的 name），不删 dir、session、session log；该 workspace 的 sessions 变 Ungrouped（本期不渲染 Ungrouped 分组，留作 follow-up）。

#### Scenario: 删除 workspace 后 sessions 仍可读

- **WHEN** 用户在 Sidebar 右键 → 删除 `md-main`，confirm
- **THEN** `<agentDataDir>/workspaces/md-main/` 消失；`/repos/md-main/` 目录完整保留；`/repos/md-main/.dsh/sessions/*.jsonl`（如有）保留；后续 session 列表仍能加载这些 sessions（成为 Ungrouped）

### Requirement: meta.json schema v2 + 自动迁移

`WorkspaceStore.list()` 遇到 v1 schema（无 id/title/updatedAt/sessionIds）时 SHALL 自动迁移到 v2：
- `id` = `UUID.randomUUID()`
- `path` = `Paths.get(dir).toRealPath()`（失败则用原 dir）
- `title` = `basename(path)`
- `updated_at` = `created_at`
- `session_ids` = `[]`

迁移在 read-time 一次性完成，不写回（避免每次 read 都 IO）；迁移只在后台 ensureOrderInitialized 时写回。

### Requirement: Picker 单 action（去掉 name 输入）

`WorkspacePickerModal` SHALL 只让用户选 path（不再要求输 name）；title 由 path basename 自动派生。

#### Scenario: 用户选 `C:\Users\byp\projects\md-main`

- **WHEN** 用户 picker 选该目录
- **THEN** 创建 workspace，name = `md-main`，title = `md-main`，自动跳到该 workspace 并开 New Session