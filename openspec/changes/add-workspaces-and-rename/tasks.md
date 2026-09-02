## 1. Core：会话元数据侧车（重命名）

- [x] 1.1 先写 `SessionStoreTest`（红）：`writeTitle(dir,id,title)` 生成 `<id>.meta.json`；`readTitle` 读到自定义标题；无侧车返回空。
- [x] 1.2 实现 `SessionStore.writeTitle/readTitle/deleteTitle`（写/读 `<id>.meta.json{title}`；`id` 白名单防路径穿越），测试转绿。
- [x] 1.3 `SessionController.derive` 改为"侧车标题优先，否则首条消息派生"，补 `SessionControllerTest`（带自定义标题/无标题两例）。

## 2. Core：工作区存储

- [x] 2.1 先写 `WorkspaceStoreTest`（红）：创建 `workspaces/<name>/{meta.json,sessions/}`；列出（含默认工作区）；lookup 按 name；dir 不存在/name 重复 → 拒绝。
- [x] 2.2 实现 `WorkspaceStore`（`create/list/get/sessionsDirFor`，默认工作区 `agent-demo` 映射顶层 `sessions/`），测试转绿。
- [x] 2.3 实现 `WorkspaceRegistry`（枚举 `workspaces/*/meta.json`、按 name lookup、`dir` 绝对路径存在性校验、`name` 唯一与合法性校验），补 `WorkspaceStoreTest` 覆盖校验分支。

## 3. Core：AgentLoop workingDir 覆盖

- [x] 3.1 先扩展 `AgentLoopFactoryTest`（红）：`buildLoop(..., workingDirOverride)` 非空时，`loop.toolContext().workingDirectory()` 等于该覆盖值；`null` 时沿用 `resolveWorkingDir`。
- [x] 3.2 实现 `AgentLoopFactory.buildLoop` 增加 `Path workingDirOverride` 参数（`null`=现状），并把 override 作为 `AgentLoop` 的 workingDir，测试转绿。

## 4. Web：WebAgentRuntime 工作区路由

- [x] 4.1 先扩展 `WebAgentRuntimeTest`（红）：`sessionsDirFor("md-main")` 返回 `workspaces/md-main/sessions`；`sinkFor/historyFor/messagesFor/hasSession` 按工作区路由。
- [x] 4.2 实现 `WebAgentRuntime.sessionsDirFor(workspace)` 与 map 复合 key（`workspace:sessionId`），把 `historyFor/messagesFor/hasSession/archiveSession/restoreSession/recorderFor` 改为按工作区目录路由，测试转绿。
- [x] 4.3 `createLoop` 增加 `workspace` 参数，非默认工作区传 `workingDirOverride=工作区 dir`，补 `WebAgentRuntimeTest`/`AgentLoopFactoryTest` 断言工作区 cwd。

## 5. Web API：rename + workspaces 端点

- [x] 5.1 新增 `WorkspaceController`：`GET /api/workspaces`（列工作区）+ `POST /api/workspaces {name,dir}`（创建，400/409 校验）。补 `WorkspaceControllerTest`。
- [x] 5.2 `SessionController` 加 `POST /api/sessions/{id}/rename {title}`（200/404/400），补 `SessionControllerTest`。
- [x] 5.3 `SessionController.list` 支持 `?workspace=<name>` 过滤；新增 DTO（`WorkspaceDto`/`RenameRequest`），补测试。

## 6. Web API：send 归属工作区

- [x] 6.1 `ChatSendRequest`/`SendRequest` 加可选 `workspace`；`ChatController.send` 校验工作区（不存在→400 `workspace_not_found`），透传 `ChatStreamService.create(..., workspace)`。补 `WebIntegrationTest`/`ChatStreamServiceTest`。
- [x] 6.2 `ChatStreamService.create` 增加 `workspace` 参数，透传 `runtime.createLoop(..., workspace)`；`sinkFor` 按工作区路由存储。补测试（创建会话落对应工作区 sessions、unknown workspace 拒绝）。

## 7. 前端：...菜单 + ＋新建工作区

- [x] 7.1 `api/chat.ts` 加 `listWorkspaces`/`createWorkspace`/`renameSession`（含类型），补前端类型测试。
- [x] 7.2 `Sidebar.tsx` 会话行换成 `...` 下拉菜单（重命名/归档/恢复/删除）；重命名内联输入调 rename。补 `Sidebar` 测试。
- [x] 7.3 工作区头部加「＋」新建按钮 + 弹窗（名称/目录路径）→ `createWorkspace` → 刷新；`App.tsx` 持有 `workspaces`/`activeWorkspace` 状态，侧栏按工作区分组加载会话。补 `App`/`Sidebar` 测试。
- [x] 7.4 `npm run build` 成功；前端 `vitest run` 全绿。

## 8. 文档与验证

- [x] 8.1 更新 `docs/design/design.md` §10（会话存储）补工作区分层与 `<id>.meta.json` 说明；`README.md` §10 Web UI 补工作区/重命名。
- [x] 8.2 后端验证：`mvn -o verify -DskipNpm=true`（非 e2e 全绿 + jacoco LINE≥80%/BRANCH≥70%）。
- [x] 8.3 `check-md.sh` 校验本 change 下 md 无 Mermaid 违规。
- [x] 8.4 提交并推送：中文 `feat(session-workspace): ...` 拆分，逐一 commit 即 push。
