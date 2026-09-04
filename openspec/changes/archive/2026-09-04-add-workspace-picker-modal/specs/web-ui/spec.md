## ADDED Requirements

### Requirement: 获取用户家目录

`GET /api/fs/home` SHALL 返回当前进程对应的用户家目录绝对路径。

#### Scenario: 返回家目录路径

- **WHEN** 客户端发送 `GET /api/fs/home`
- **THEN** 服务端返回 `200 OK`，响应体 `{"path": "<abs-path>", "platform": "windows"|"linux"|"mac"}`

#### Scenario: 服务未启动

- **WHEN** Web 服务未运行或响应超时
- **THEN** 客户端按"无法连接到服务端"展示行内错误，不弹额外 dialog

### Requirement: 列出目录条目

`GET /api/fs/list?path=<abs>&includeHidden=false` SHALL 返回某绝对路径下的目录条目（不含隐藏文件），含父目录指针。

#### Scenario: 正常列出

- **WHEN** 客户端发送 `GET /api/fs/list?path=<家目录内已存在目录>&includeHidden=false`
- **THEN** 服务端返回 `200 OK`，响应体形如：
  ```json
  {
    "path": "<abs-path>",
    "parent": "<abs-parent-or-null>",
    "entries": [
      {"name": "agent-demo", "path": "<abs>", "isDir": true, "size": 0, "mtime": 1700000000000},
      {"name": "README.md", "path": "<abs>", "isDir": false, "size": 2048, "mtime": 1700000001000}
    ]
  }
  ```
- **AND** `entries` 按目录优先 + 名称升序排列
- **AND** 默认不含以 `.` 开头或 Windows hidden 属性的条目

#### Scenario: 包含隐藏文件

- **WHEN** 客户端发送 `GET /api/fs/list?path=...&includeHidden=true`
- **THEN** 返回的 `entries` 包含隐藏文件（`.git` / `.vscode` 等）

#### Scenario: 路径不是绝对路径

- **WHEN** 客户端发送 `GET /api/fs/list?path=relative/path`
- **THEN** 服务端返回 `400 Bad Request`，响应体 `{"error": "path_not_absolute"}`

#### Scenario: 路径不存在

- **WHEN** 客户端发送 `GET /api/fs/list?path=<不存在的绝对路径>`
- **THEN** 服务端返回 `404 Not Found`，响应体 `{"error": "path_not_found"}`

#### Scenario: 路径在家目录外

- **WHEN** 客户端发送 `GET /api/fs/list?path=<家目录外的绝对路径>`
- **THEN** 服务端返回 `403 Forbidden`，响应体 `{"error": "path_outside_home"}`

### Requirement: 新建空目录

`POST /api/fs/mkdir` SHALL 在 `$HOME` 子树内创建空目录，并返回创建后的绝对路径。

#### Scenario: 创建成功

- **WHEN** 客户端发送 `POST /api/fs/mkdir` 请求体 `{"path": "<家目录内不存在的绝对路径>"}`
- **THEN** 服务端创建该目录（含必要的父目录），返回 `200 OK`，响应体 `{"path": "<abs>"}`

#### Scenario: 目录已存在

- **WHEN** 客户端发送 `POST /api/fs/mkdir`，且 `path` 已存在
- **THEN** 服务端返回 `409 Conflict`，响应体 `{"error": "dir_exists"}`

#### Scenario: 名称非法

- **WHEN** 客户端发送 `POST /api/fs/mkdir`，且 `path` 含路径分隔符、非法字符或为空
- **THEN** 服务端返回 `400 Bad Request`，响应体 `{"error": "name_invalid"}`

#### Scenario: 路径越界

- **WHEN** 客户端发送 `POST /api/fs/mkdir`，且 `path` 解析后不在 `$HOME` 子树内
- **THEN** 服务端返回 `403 Forbidden`，响应体 `{"error": "path_outside_home"}`

### Requirement: 获取盘符列表（Windows）

`GET /api/fs/drives` SHALL 在 Windows 平台返回盘符列表（如 `C:`、`D:`），供 Modal "此电脑"层级展示。

#### Scenario: Windows 返回盘符

- **WHEN** 客户端发送 `GET /api/fs/drives`，且运行平台为 Windows
- **THEN** 服务端返回 `200 OK`，响应体 `{"drives": [{"name": "C:", "path": "C:\\"}, {"name": "D:", "path": "D:\\"}]}`

#### Scenario: 非 Windows 平台

- **WHEN** 客户端发送 `GET /api/fs/drives`，且运行平台为 Linux/macOS
- **THEN** 服务端返回 `200 OK`，响应体 `{"drives": []}`（空数组，前端按家目录展示）

### Requirement: 路径安全边界

所有 `/api/fs/**` 端点 SHALL 在执行任何文件系统操作前把传入路径解析为 `toRealPath()`，并强制其落在 `$HOME`（`toRealPath()` 之后）的子树内；否则返回 `403 Forbidden` 且不执行任何 IO。

#### Scenario: `..` 逃逸被挡

- **WHEN** 客户端发送 `GET /api/fs/list?path=<家目录内路径>/../../../etc`
- **THEN** 服务端在解析后判断该路径不在家目录子树，返回 `403 Forbidden`，响应体 `{"error": "path_outside_home"}`
- **AND** 不返回任何 `/etc` 下的条目

#### Scenario: 符号链接逃逸被挡

- **WHEN** 客户端发送 `GET /api/fs/list?path=<家目录内符号链接>`，且该链接指向家目录外（如 `/etc`）
- **THEN** 服务端经 `toRealPath()` 解析后判断真实路径不在家目录子树，返回 `403 Forbidden`

#### Scenario: trusted-host 鉴权

- **WHEN** 客户端源 IP 不在 `agent.web.trusted-hosts` 白名单内
- **THEN** 服务端在路径校验前先返回 `403 Forbidden`，响应体 `{"error": "host_not_trusted"}`（沿用现有 trusted-host 行为）

### Requirement: 工作区目录选择 Modal

Web UI SHALL 提供 `WorkspacePickerModal` 组件，触发后弹出模态对话框，用于以"目录浏览 + 路径输入 + 新建文件夹 + 自动 basename"的方式选择工作区目录。

#### Scenario: 触发打开 Modal

- **WHEN** 用户点击 Sidebar 工作区切换条右侧的 `+` 按钮
- **THEN** 弹出 `WorkspacePickerModal`，默认定位到 `$HOME` 或 `localStorage` 记忆的上次位置

#### Scenario: 路径输入框跳转

- **WHEN** 用户在 Modal 顶部路径输入框键入绝对路径并按 Enter
- **AND** 该路径在家目录内且存在
- **THEN** Modal 主区域刷新为该路径下的条目列表

#### Scenario: 路径输入框非法跳转

- **WHEN** 用户键入非家目录内路径 / 非法路径 / 不存在路径并按 Enter
- **THEN** Modal 顶部展示行内错误（如"路径不存在"），不切换主区域

#### Scenario: 双击进入子目录

- **WHEN** 用户双击某目录条目
- **THEN** 主区域刷新为该子目录的条目列表，面包屑更新

#### Scenario: 单击选中条目

- **WHEN** 用户单击某条目
- **THEN** 该条目获得高亮，底部"选择此目录"按钮变为可用
- **AND** 工作区名称输入框预填为选中路径的 basename

#### Scenario: 文件条目不可选

- **WHEN** 用户单击某文件条目（非目录）
- **THEN** 该条目不进入选中态，底部按钮仍为禁用

#### Scenario: 新建文件夹

- **WHEN** 用户点击工具栏"新建文件夹"按钮
- **THEN** 工具栏下方展开一行输入框 + 确认/取消按钮
- **WHEN** 用户输入合法名称并点击确认
- **THEN** 调用 `POST /api/fs/mkdir { path: <当前路径>/<新名> }`
- **AND** 创建成功后刷新当前目录列表，高亮新文件夹

#### Scenario: 显示/隐藏隐藏文件

- **WHEN** 用户点击工具栏"显示隐藏文件"开关
- **THEN** 重新拉取当前目录（参数 `includeHidden=true`），条目列表更新
- **AND** 开关状态在本 Modal 生命周期内保持（不持久化到 localStorage）

#### Scenario: 面包屑跳转

- **WHEN** 用户点击面包屑中某个中间层级
- **THEN** 主区域刷新为该层级的目录条目

#### Scenario: 此电脑层级

- **WHEN** 用户点击面包屑最左侧"此电脑"
- **THEN** Modal 显示盘符列表（Windows）或直接定位到 `$HOME`（Linux/macOS）
- **AND** 双击某个盘符进入该盘符根目录（Windows）

#### Scenario: 关闭 Modal

- **WHEN** 用户点击右上角 `×` / 点击 Modal 外区域 / 按 Esc
- **THEN** Modal 关闭，localStorage 写入当前路径（`agent-demo.workspace-picker.last-path`），不创建工作区

### Requirement: 选完后自动创建并切换工作区

`WorkspacePickerModal` 提交按钮 SHALL 调用现有 `POST /api/workspaces { name, dir }`，成功后关闭 Modal、刷新工作区列表、自动调用 `onWorkspaceChange(newWs.name)`，与 DSH 行为一致。

#### Scenario: 提交成功

- **WHEN** 用户点击"选择此目录"且当前选中路径为家目录内的有效目录
- **AND** 工作区名称通过 `WorkspaceStore.validateName` 校验
- **THEN** 客户端调用 `POST /api/workspaces { name, dir }`
- **AND** 返回 `200` 后关闭 Modal、刷新 Sidebar 工作区列表、调用 `onWorkspaceChange(newWs.name)`
- **AND** 切换后会自动发起新会话（或显示空态）

#### Scenario: 名称冲突

- **WHEN** 用户提交的工作区名称已存在
- **THEN** Modal 顶部展示行内错误"工作区已存在"，不关闭 Modal

#### Scenario: 路径不存在（提交时）

- **WHEN** 用户提交时选中路径在服务端校验时已不存在
- **THEN** Modal 顶部展示行内错误"路径不存在"，刷新当前目录列表

#### Scenario: 名称不合法

- **WHEN** 用户键入的名称不通过 `WorkspaceStore.validateName`（含非法字符 / 超过 64 字符 / 等于 `agent-demo`）
- **THEN** Modal "选择此目录"按钮保持禁用，输入框显示行内提示

### Requirement: localStorage 记忆上次浏览位置

`WorkspacePickerModal` SHALL 在关闭前将当前浏览路径写入 `localStorage["agent-demo.workspace-picker.last-path"]`；下次打开 Modal 时优先使用该路径。

#### Scenario: 初次打开

- **WHEN** localStorage 中无 `agent-demo.workspace-picker.last-path` 记录
- **THEN** Modal 默认定位到 `$HOME`

#### Scenario: 二次打开恢复位置

- **WHEN** localStorage 中存在上次记录的路径，且该路径仍存在且在家目录内
- **THEN** Modal 打开后直接定位到该路径

#### Scenario: 路径已失效

- **WHEN** localStorage 中记录的上次路径已不存在或不在家目录内
- **THEN** Modal 回退到 `$HOME`，并清除 localStorage 中的失效记录
