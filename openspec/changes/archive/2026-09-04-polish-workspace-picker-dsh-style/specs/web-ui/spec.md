## ADDED Requirements

### Requirement: 获取快速访问目录

`GET /api/fs/quick-access` SHALL 返回当前用户家目录下的快速访问目录列表（Home + Desktop + Documents + Downloads），按平台探测并跳过不存在的目录。

#### Scenario: Windows 全部存在

- **WHEN** 客户端发送 `GET /api/fs/quick-access`，且运行平台为 Windows 且家目录下 Desktop / Documents / Downloads 都存在
- **THEN** 服务端返回 `200 OK`，响应体 `{"items":[{"name":"Home","path":"<home>"},{"name":"Desktop","path":"<home>\\Desktop"},{"name":"Documents","path":"<home>\\Documents"},{"name":"Downloads","path":"<home>\\Downloads"}]}`

#### Scenario: Linux 部分不存在

- **WHEN** 客户端发送 `GET /api/fs/quick-access`，且家目录下不存在 Desktop 但存在 Documents
- **THEN** 服务端只返回 `Home + Documents`，跳过 Desktop（响应体 items 长度 2）

#### Scenario: 路径越界

- **WHEN** 客户端发送 `GET /api/fs/quick-access`，且探测到的某路径在 `$HOME` 外
- **THEN** 服务端跳过该目录，不返回

#### Scenario: trusted-host 鉴权

- **WHEN** 客户端源 IP 不在 `agent.web.trusted-hosts` 白名单内
- **THEN** 服务端在响应前先返回 `403 Forbidden`，响应体 `{"error":"host_not_trusted"}`

### Requirement: 工作区目录选择 Modal（DSH 风格）

`WorkspacePickerModal` SHALL 提供仿 DSH 资源管理器视觉的目录选择对话框：顶部 ←/→/↑ 导航 + 面包屑 + 显示隐藏，主区域左侧导航树 + 右侧文件列表，底部"文件夹"路径框 + name 输入框 + 取消/选择按钮。

#### Scenario: 打开默认定位 home

- **WHEN** 用户点击 Sidebar 工作区切换条右侧 `+` 按钮
- **THEN** 弹出 Modal，默认定位到 `$HOME` 或 `localStorage["agent-demo.workspace-picker.last-path"]` 记忆的上次位置（路径在 home 内）

#### Scenario: 标题文案

- **WHEN** Modal 渲染
- **THEN** 顶部显示英文 "Select Workspace Directory"，`aria-label="选择工作区目录"`

### Requirement: 顶部前进/后退/上一级按钮

`WorkspacePickerModal` SHALL 在顶部提供 ← / → / ↑ 三个按钮，分别实现"后退"、"前进"、"上一级"。

#### Scenario: 后退

- **WHEN** 用户点击 ← 按钮且 history 栈存在前驱节点
- **THEN** `currentPath` 切换到 `history[historyIndex-1]`，列表重新加载；按钮在栈底时禁用

#### Scenario: 前进

- **WHEN** 用户点击 → 按钮且 history 栈存在后继节点
- **THEN** `currentPath` 切换到 `history[historyIndex+1]`，列表重新加载；按钮在栈顶时禁用

#### Scenario: 上一级

- **WHEN** 用户点击 ↑ 按钮
- **AND** 当前目录存在父目录
- **THEN** `currentPath` 切换到父目录（若父目录在 home 内）；当前已在 home 根时按钮禁用

#### Scenario: 历史栈深度上限

- **WHEN** 用户连续浏览超过 50 个不同目录
- **THEN** history 栈超 50 时弹栈底（保留最新 50 条）

### Requirement: 左侧导航树

`WorkspacePickerModal` SHALL 在主区域左侧渲染导航树，包含"快速访问"组（Home + Desktop + Documents + Downloads，从 `/api/fs/quick-access` 拉取）与"此电脑"组（盘符列表，从 `/api/fs/drives` 拉取，仅 Windows 显示）。

#### Scenario: Linux/macOS 无盘符

- **WHEN** Modal 打开且运行平台为 Linux/macOS
- **THEN** 左导航树只显示"快速访问"组，"此电脑"组不渲染（或显示空）

#### Scenario: 点击快速访问跳转

- **WHEN** 用户点击左侧 `Desktop` 条目
- **THEN** `currentPath` 切换到 Desktop 的绝对路径，右侧列表重新加载

#### Scenario: 点击盘符跳转

- **WHEN** 用户点击左侧 `C:` 条目（Windows）
- **THEN** `currentPath` 切换到 `C:\` 的真实路径，右侧列表重新加载（若该路径在 home 内；否则显示空 + 错误条）

### Requirement: 右侧文件列表列头点击排序

`WorkspacePickerModal` SHALL 在右侧文件列表上方渲染列头"名称 / 修改时间 / 类型"，点击列头切换排序字段与升降序，默认 name asc。

#### Scenario: 默认排序

- **WHEN** Modal 渲染或首次拉取目录条目
- **THEN** 条目按 name 升序排列（目录优先 + 名称 `localeCompare`，大小写不敏感）

#### Scenario: 切换排序字段

- **WHEN** 用户点击"修改时间"列头
- **THEN** 条目按 mtime 升序排列；再次点击切换为降序

#### Scenario: 排序状态保持

- **WHEN** 用户切换到 mtime desc 后浏览到子目录
- **THEN** 子目录的列表仍按 mtime desc 排序（sort 状态在 Modal 生命周期内保持）

### Requirement: 底部"文件夹"路径框可编辑

`WorkspacePickerModal` SHALL 在底部提供"文件夹：<input>"路径框，可直接编辑绝对路径 + Enter 跳转到该路径（路径必须在 home 内）。

#### Scenario: 路径合法跳转

- **WHEN** 用户在底部路径框键入绝对路径并按 Enter
- **AND** 该路径在家目录内且存在
- **THEN** `currentPath` 切换到该路径，右侧列表重新加载，history 栈更新

#### Scenario: 路径非法

- **WHEN** 用户键入家目录外路径或不存在的路径并按 Enter
- **THEN** Modal 顶部展示行内错误"路径不在家目录范围内"或"路径不存在"，不切换

### Requirement: 工作区名称输入框保留

`WorkspacePickerModal` SHALL 在底部"文件夹"路径框下方提供"工作区名称：<input>"，与"add-workspace-picker-modal" 既有行为一致（默认 basename(selectedPath) 可编辑）。

#### Scenario: 选中后默认填充 basename

- **WHEN** 用户单击右侧目录条目
- **THEN** 工作区名称输入框预填为选中路径的 basename

#### Scenario: 名称非法禁用提交

- **WHEN** 工作区名称含非法字符（不含 `[A-Za-z0-9._-]`）或超过 64 字符
- **THEN** "选择此目录"按钮禁用，输入框下方显示行内提示

### Requirement: 显示隐藏文件开关

`WorkspacePickerModal` SHALL 在右侧列头旁提供 `Eye/EyeOff` 图标按钮，切换是否显示隐藏文件。

#### Scenario: 默认隐藏

- **WHEN** Modal 打开
- **THEN** 显示 `Eye` 图标（"显示隐藏文件"），目录条目默认不含 `.` 开头或 Windows hidden 属性

#### Scenario: 切换显示

- **WHEN** 用户点击 Eye 图标
- **THEN** 图标变为 `EyeOff`，重新拉取当前目录（参数 `includeHidden=true`），列表更新
- **AND** 状态在 Modal 生命周期内保持（不持久化到 localStorage）
