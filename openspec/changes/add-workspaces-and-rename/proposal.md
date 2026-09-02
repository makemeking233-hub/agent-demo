## Why

web 侧边栏当前**无法重命名会话**（会话 `title` 由首条消息**即时派生、不落盘**，`SessionController.derive()`），且 `workspace` 在 `SessionController:127` **硬编码为 `"agent-demo"`**——用户无法建立/切换多个工作区。用户希望像 DeepSeek Harness 那样：给会话**重命名**（`...` 菜单），并**添加新的工作区**（工作区头部 `+`，工作区为真实运行目录）。

## What Changes

- **会话重命名（持久化）**：新增会话元数据侧车 `<id>.meta.json{title}`，`POST /api/sessions/{id}/rename` 写入；列表摘要**优先用自定义标题**，否则回落首条消息派生的自动标题。
- **工作区（真实运行目录）**：新增 `workspaces/<name>/` 存储（`meta.json{name,dir,created_at}` + `sessions/<id>.jsonl`），每个工作区是一个**真实目录**；会话在该工作区下运行时 `cwd=工作区 dir`、会话落该工作区 `sessions/`。
  - `GET /api/workspaces` 列工作区；`POST /api/workspaces {name,dir}` 新建（校验 dir 存在、name 唯一）。
  - `GET /api/sessions?workspace=<name>` 列指定工作区会话；`POST /api/chat/send` 支持可选 `workspace` 归属新会话。
  - **默认工作区 `agent-demo` 不迁移**：继续用顶层 `sessions/`（旧会话无缝保留），新建工作区才用 `workspaces/<name>/`。
- **后端**：`WorkspaceStore`/`WorkspaceManager`（元数据 + 每工作区 sessions 目录）；`AgentLoopFactory.buildLoop` 加 `workingDir` 覆盖；`WebAgentRuntime` 的 `sessionsDir/sinkFor/createLoop` 按工作区路由。
- **前端（仿 DSH）**：会话行用 `...` **下拉菜单**（重命名/归档/恢复/删除）替换单个删除按钮；工作区头部加 `+` **新建工作区**按钮（名称 + 目录路径）；侧栏按工作区分组展示（`GET /api/workspaces` + 各工作区会话）。

## Capabilities

### New Capabilities
- `session-workspace`: 工作区（真实运行目录）的创建/列表、按工作区路由会话存储与运行目录、会话重命名（持久化标题覆盖）、以及对应 Web UI（`...` 菜单 + 头部 `+` + 工作区分组）。

### Modified Capabilities
- （无）——`web-ui` 现有会话列表/归档/恢复/重进恢复 requirement 不变；重命名与工作区为**新增能力**，归入 `session-workspace`。

## Impact

- **core**：`agent-core/.../session/{SessionStore.java（加 meta 侧车读写）/WorkspaceStore.java（新增）}`；`agent-core/.../core/AgentLoopFactory.java`（workingDir 覆盖）。
- **web API**：`agent-web/.../api/{SessionController.java, WorkspaceController.java（新增）, ChatController.java}`；`agent-web/.../api/dto/{SessionSummaryDto.java, WorkspaceDto.java（新增）, RenameRequest.java（新增）}`；`agent-web/.../stream/{WebAgentRuntime.java, ChatStreamService.java}`。
- **前端**：`agent-web/frontend/src/components/Sidebar.tsx`（`...` 菜单 + 头部 `+` + 工作区分组）、`api/chat.ts`（rename/workspace API）、`App.tsx`（工作区状态）。
- **测试**：core `SessionStoreTest`/`WorkspaceStoreTest`；web `SessionControllerTest`/`WebAgentRuntimeTest`/`WebIntegrationTest`；前端 `Sidebar`/`App`。
- **无破坏性 API**：新增端点 + 可选字段（缺省 `workspace`=默认工作区、缺省顶栏会话沿用现状）。

## Out of Scope

- 不做工作区**删除/重命名**（v1 仅创建 + 列表；DSH 的删除作为后续）。
- 不做会话**移动/跨工作区迁移**（新会话归入所选工作区）。
- 不做每工作区独立的 provider/模型配置（provider/tool 仍全局共享，仅 cwd 与存储按工作区路由）。
