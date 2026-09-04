## Why

当前 Web UI 在 Sidebar 创建工作区时，需要用户手动键入「工作区名」和「目录路径（绝对路径）」两个文本框。该交互对照 DSH 的 `WorkspacePicker` 体验差距明显：

- 用户需要记住并拼写完整绝对路径（如 `D:\claude-projects\md-main`），极易出错；
- 错误路径要等服务端校验失败后才被察觉，无浏览/新建/搜索辅助；
- 对照 DSH 桌面端用原生文件选择对话框的 UX，体验明显落后。

本次变更把内联的"两个输入框"替换为仿 DSH 的目录选择 Modal：路径框可编辑 + 面包屑 + 条目列表 + 新建文件夹，name 默认 basename 且可改。后端补齐对应的文件系统浏览 API，并把浏览根严格锁到家目录做安全边界。

## What Changes

- **新增后端 `FsController`**：暴露 `GET /api/fs/home`、`GET /api/fs/list`、`POST /api/fs/mkdir`、`GET /api/fs/drives`（仅 Windows）四个端点；所有端点沿用 trusted-host 鉴权，路径解析后必须落在 `$HOME` 子树内。
- **新增前端 `WorkspacePickerModal`**：仿 DSH 文件选择器布局（路径框 + 面包屑 + "此电脑"盘符层 + 工具栏 + 条目列表 + 名称输入框 + 提交按钮）；默认定位 `$HOME` 或 `localStorage` 记忆的上次位置；完成后自动切换到新工作区。
- **改造 `Sidebar.tsx`**：移除当前内联的 `workspaceForm` 表单与相关 state，工作区切换条右侧的 `+` 按钮改为打开 Modal。
- **增强 `WorkspaceStore` 校验**：保留现有 `validateName` / `validateDir` 语义不变，Modal 通过现有 `POST /api/workspaces` 提交，name/dir 字段契约不变。
- **新增对应 OpenSpec delta spec**：在 `web-ui` capability 下追加"工作区目录选择"相关 Requirement；spec 仅描述用户可见行为与 API 契约，不约束实现。

无破坏性变更（BREAKING）：现有 `POST /api/workspaces { name, dir }` 接口形态不变，旧表单移除后只剩 Modal 入口，调用方仍可正常请求。

## Capabilities

### New Capabilities

无（fs 浏览 API 与 Modal 视作 web-ui 能力的一部分）。

### Modified Capabilities

- `web-ui`：在现有 spec 中追加 8 个新 Requirement，覆盖「家目录获取」「目录列出」「新建空目录」「盘符列表」「路径安全边界」「Modal 选择器」「自动切换」「localStorage 记忆」。

## Impact

- **后端**：新增 `agent-web/.../api/FsController.java`（含 4 个 DTO：`FsListResponse`、`FsMkdirRequest`、`FsHomeResponse`、`FsDrivesResponse`）；新增 `FsControllerTest.java` 覆盖正常路径 + 路径越界 + `..` 攻击 + Windows 盘符。
- **后端（minor）**：复用 `WebAgentRuntime` 拿 `agentDataDir` 不变；复用 `TrustedHostFilter` 不变；不引入新依赖。
- **前端**：新增 `agent-web/frontend/src/components/WorkspacePickerModal.tsx` + `.module.css` + `WorkspacePickerModal.test.tsx`（vitest + testing-library）。
- **前端（改）**：`Sidebar.tsx` 删除 `workspaceForm` / `workspaceInput` / `workspaceError` 内部 state 与 JSX（约 30 行），改为触发 Modal 的回调。
- **API 契约**：不破坏 `POST /api/workspaces`，只是替换前端入口；后端 `FsController` 是纯新增。
- **安全**：所有 `/api/fs/**` 沿用 trusted-host + 路径前缀校验；非 $HOME 子树一律 403；symlink 解析用 `Path.toRealPath()`，无法解析的不返回。
- **测试**：新增 1 个 Java 测试类（~10 个用例）+ 1 个前端组件测试（~6 个用例）；`mvn verify` 门禁 LINE≥80%/BRANCH≥70% 仍需通过。
- **文档**：无 README 变更（属于 v0.x 内部 UX 改进）。
