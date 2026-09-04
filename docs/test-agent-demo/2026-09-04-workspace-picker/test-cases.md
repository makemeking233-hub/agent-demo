# `2026-09-04-workspace-picker/` — 测试用例清单

## 1. 后端：HomePathGuardTest（13 用例）

| 编号 | 场景 | 期望 |
|---|---|---|
| HP-01 | 解析 home 内路径 | realPath.startsWith(home) + existed=true |
| HP-02 | 非绝对路径（`relative/path` / `./foo` / `foo/bar` / `C:foo`） | `path_not_absolute` |
| HP-03 | null / 空字符串 / 全空白 | `path_not_absolute` |
| HP-04 | 通过 `..` 逃逸到 home 外 | `path_outside_home` |
| HP-05 | 路径在 home 外（直接构造 outside 目录） | `path_outside_home` |
| HP-06 | requireExists=true 但路径不存在 | `path_not_found` |
| HP-07 | requireExists=false + 父目录在 home 内 | `existed=false` + parentReal 在 home 内 |
| HP-08 | requireExists=false + 路径已存在 | `dir_exists` |
| HP-09 | requireExists=false + 父目录在 home 外 | `path_outside_home` |
| HP-10 | 符号链接逃逸（Windows 开发者模式跳过） | `path_outside_home` |
| HP-11 | mkdir 模式嵌套上溯 `a/b/c` | `existed=false` + parent=`a/b` 在 home 内 |
| HP-12 | mkdir 模式 home 之外所有祖先 | `path_outside_home` |
| HP-13 | path_too_long（`<=64` 校验） | name_invalid 由 controller 层做 |

## 2. 后端：FsControllerTest（15 用例）

| 编号 | 端点 | 场景 | 期望 |
|---|---|---|---|
| FC-01 | GET /home | 返回家目录 + platform | 200 + 合法 path/platform |
| FC-02 | GET /list | 正常列出 | 200 + entries 目录优先 + 文件按名称 |
| FC-03 | GET /list | 默认隐藏文件过滤 | 200 + 只含 `public` |
| FC-04 | GET /list?includeHidden=true | 含隐藏文件 | 200 + 含 `.secret` 和 `public` |
| FC-05 | GET /list | 相对路径 | 400 + `path_not_absolute` |
| FC-06 | GET /list | 路径在 home 外 | 403 + `path_outside_home` |
| FC-07 | GET /list | 路径不存在 | 404 + `path_not_found` |
| FC-08 | GET /list | 路径是文件 | 400 + `not_a_directory` |
| FC-09 | POST /mkdir | 成功 | 200 + 目录真创建 |
| FC-10 | POST /mkdir | 已存在 | 409 + `dir_exists` |
| FC-11 | POST /mkdir | leaf 含空格 | 400 + `name_invalid` |
| FC-12 | POST /mkdir | 空路径 | 400 + `name_invalid` |
| FC-13 | POST /mkdir | 父目录在 home 外 | 403 + `path_outside_home` |
| FC-14 | POST /mkdir | 嵌套目录 `a/b/c` | 200 + 全部创建 |
| FC-15 | GET /drives | Windows 返回盘符 / Linux 返回空 | 200 + 合法数组 |

## 3. 前端：api/fs.test.ts（12 用例）

| 编号 | 函数 | 场景 | 期望 |
|---|---|---|---|
| FS-01 | getHome | 200 | 返回 `{path, platform}` |
| FS-02 | getHome | 5xx | 抛 `FsError(code=unknown)` |
| FS-03 | listDir | 200 解析 entries | entries.length === 2 |
| FS-04 | listDir | includeHidden=true 查询串 | URL 含 `includeHidden=true` |
| FS-05 | listDir | 403 path_outside_home | 抛 `FsError` |
| FS-06 | listDir | 400 path_not_absolute | 抛 `FsError` |
| FS-07 | listDir | 404 path_not_found | 抛 `FsError` |
| FS-08 | mkdir | 200 返回 path | 返回 `{path}` |
| FS-09 | mkdir | 409 dir_exists | 抛 `FsError` |
| FS-10 | mkdir | 403 path_outside_home | 抛 `FsError` |
| FS-11 | getDrives | Windows 返回数组 | drives.length === 1 |
| FS-12 | getDrives | Linux 返回空 | drives === [] |

## 4. 前端：WorkspacePickerModal.test.tsx（14 用例）

| 编号 | 类别 | 场景 | 期望 |
|---|---|---|---|
| WP-01 | 初始化 | 默认定位 home | listDir 调 HOME |
| WP-02 | 初始化 | 读 localStorage 记住位置 | listDir 调 PROJECTS |
| WP-03 | 初始化 | localStorage 失效回退 home | listDir 调 HOME |
| WP-04 | 导航 | 双击进入子目录 | listDir 调 PROJECTS |
| WP-05 | 导航 | 路径输入框 Enter 跳转 | listDir 调 PROJECTS |
| WP-06 | 选中 | 单击目录选中 + footer basename | nameInput.value === "projects" |
| WP-07 | 选中 | 文件不可选（按钮 disabled） | btn.disabled === true |
| WP-08 | 提交 | 改 name + 选择此目录 → onSubmit(name, dir) | onSubmit called + onClose called |
| WP-09 | 提交 | name 含非法字符 → disabled | btn.disabled === true |
| WP-10 | 提交 | 提交失败显示错误条 | 错误文案显示 + onClose 未调 |
| WP-11 | 关闭 | Esc 关闭 + 写 localStorage | onClose called + localStorage === HOME |
| WP-12 | 关闭 | 点击 overlay 关闭 | onClose called |
| WP-13 | 新建 | 工具栏 → 输入名 → 创建 → mkdir + refresh | mkdir called + listDir 调用 3 次 |
| WP-14 | 新建 | 名称非法 → 显示错误 + 不调 mkdir | mkdir not called + 错误文案 |

## 5. 前端：Sidebar.test.tsx（9 用例，含 1 新增集成）

| 编号 | 场景 | 期望 |
|---|---|---|
| SB-01~05 | 既有（默认 5 个 / 展开 / 新会话 / 归档 / 重命名） | 既有断言 |
| SB-06（新） | 点击新建工作区 + 弹出 WorkspacePickerModal | dialog 出现 + Esc 关闭 |
| SB-07（新，端到端集成）| 点 + → 弹 Modal → 双击 → 选中 → 改 name → 提交 → 调 onCreateWorkspace | onCreateWorkspace called with (name, dir) |
| SB-08~09 | 既有（归档视图 / 相对时间） | 既有断言 |
