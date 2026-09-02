## Context

web 侧边栏现状（add-session-switch / add-session-management 之后）：
- 会话是 `~/.agent-demo/sessions/<id>.jsonl` 文件；`title`/`preview` 由 `SessionController.derive()` 从**首条消息即时派生**，**不落盘**（`SessionController:146-157`）。
- `workspace` 在 `SessionController.buildActiveSummaries/buildArchivedSummaries` 中**硬编码为 `"agent-demo"`**（第 127/140 行）。
- `Sidebar.tsx` 已按 `workspace` 分组、有展开/收起、有「归档视图 + 恢复」；但会话行**只有一个**「删除/恢复」按钮（无 `...` 菜单），工作区头部**无**「＋」新建入口。

用户希望仿 DeepSeek Harness：会话行「`...`」菜单（重命名/归档/恢复/删除）+ 工作区头部「＋」新建（工作区为真实运行目录）。

约束（沿用 AGENTS.md §3）：JDK17 / Fail-Closed / 文件 0600 目录 0700 / 中文 commit · 文档 / commit 即 push。已有 **worktree 模式**（`resolveWorkingDir`），本设计的工作区**运行目录路由**与之互补（worktree 是"每会话独立 checkout"，工作区是"每个用户目录一个固定 cwd+存储"）。

## Goals / Non-Goals

**Goals:**
- 会话重命名：持久化自定义标题（侧车元数据），列表/侧栏优先显示自定义标题。
- 工作区：新建工作区 = 指定名称 + 真实目录；该工作区会话 `cwd=目录`、存档落 `workspaces/<name>/sessions/`。
- 仿 DSH UI：会话行 `...` 下拉菜单、工作区头部「＋」新建弹窗、按工作区分组。

**Non-Goals:**
- 不做工作区**删除/重命名**（v1 仅创建 + 列表）。
- 不做会话**跨工作区迁移**。
- 不做每工作区独立的 provider/模型/tool 配置（全局共享，仅 cwd+存储按工作区路由）。
- 不做**默认工作区迁移**（旧顶层 `sessions/` 保留为 "agent-demo"）。

## Decisions

### D1: 工作区 = 真实目录，按 `workspaces/<name>/` 分层；默认工作区不迁移
```
~/.agent-demo/
├── sessions/                      # 默认工作区 "agent-demo"（现状不改，avoid 迁移）
│   └── <id>.jsonl  <id>.meta.json
└── workspaces/
    └── <name>/
        ├── meta.json              # { name, dir, created_at }
        └── sessions/<id>.jsonl    # 该工作区会话 + <id>.meta.json
```
- 默认工作区 "agent-demo" 的 `dir = System.getProperty("user.dir")`，存储=顶层 `sessions/`。**不迁移旧会话**。
- 新建工作区 = `workspaces/<name>/meta.json` + `sessions/`；`WorkspaceRegistry` 扫描元数据枚举/lookup。
- 备选（迁移旧会话到 workspaces/agent-demo）否决：破坏既有磁盘布局与 /resume 定位，收益低、风险高。

### D2: 会话重命名走侧车 `<id>.meta.json{title}`，列表优先自定义标题
- `SessionStore` 加静态 `readTitle(dir,id)` / `writeTitle(dir,id,title)`（写 `<id>.meta.json`，JSON `{title}`）。
- `SessionController.derive(...)` 改成：先读侧车 `title`（非空则用），否则沿用首条消息派生标题。
- 理由：不改 JSONL 正文格式（append-only 语义保留），元数据旁路，干净可回退（删侧车即恢复自动标题）。

### D3: 运行目录路由 = `AgentLoopFactory.buildLoop` 加 `Path workingDirOverride`
- `buildLoop(...)` 增加 `workingDirOverride` 参数（`null`=沿用 `resolveWorkingDir(cfg)` 现状）。用 override 设 `AgentLoop` 的 workingDir。
- `WebAgentRuntime.createLoop` 增加 `workspace`（或直接 `workingDir`）参数：当 `workspace != null && != "agent-demo"` 时传 `workingDirOverride = 该工作区 dir`。
- 备选（每工作区独立 `AgentConfig`/`resolveWorkingDir`）否决：over-engineering，provider/tool 共享，只需覆盖 cwd。

### D4: `WebAgentRuntime` 按工作区路由"会话基目录"
- 新增 `Path sessionsDirFor(String workspace)`：默认工作区 → 顶层 `sessions/`；否则 `workspaces/<ws>/sessions/`。
- 现有 `historyFor/messagesFor/hasSession/archiveSession/restoreSession/recorderFor` 均改为按 `sessionsDirFor(workspace)` 路由。
- **注意**：`sessionHistories/sessionRecorders/sessionStores` 三个 map 现按 `sessionId` 为 key。工作区引入后改用 `workspace + ":" + sessionId` 复合 key（uuid 撞车概率极低，但复合 key 消除歧义）。
- 新增 `WorkspaceRegistry`：枚举 `workspaces/*/meta.json`、按 name lookup、创建（校验 dir 存在 / name 唯一）、默认工作区兜底。

### D5: `send` 的 `workspace` 透传链
`POST /api/chat/send` 请求体加可选 `workspace` → `ChatController` 解析（非法工作区 → 400）→ `ChatStreamService.create(sessionId, model, mode, workspace)` → `runtime.createLoop(..., workspace)` → 路由 cwd + 存储。

### D6: 前端仿 DSH
- **会话行 `...` 菜单**：把现有单按钮换成 `Ellipsis` 按钮，点击弹 dropdown：重命名 / 归档 / 恢复 / 删除。重命名 → 内联 `<input>` 或小弹窗 → `POST /api/sessions/{id}/rename` → 更新 `sessions` 状态。
- **工作区头部「＋」**：新增按钮，点击弹「名称 + 目录路径」弹窗 → `POST /api/workspaces` → 成功后刷新工作区列表。
- `api/chat.ts` 加 `listWorkspaces`/`createWorkspace`/`renameSession`；`App.tsx` 持有 `workspaces` 状态，`Sidebar` 加 `onRename`/`onCreateWorkspace` 回调与 `workspaces`/`activeWorkspace` props。
- 侧栏各工作区会话独立加载（`GET /api/sessions?workspace=<name>`）；默认工作区也走此路径，保持一致。

## Risks / Trade-offs

- **[会话 map 复合 key 改动]** → `historyFor` 等用 `ws:sessionId` key；`SessionResumeLoader.loadById`/`messagesFor` 需按工作区目录读。关联改动面大，需一并迁移现有测试。
- **[默认工作区不迁移导致 `GET /api/sessions` 语义变化]** → 缺省（无 workspace 参数）继续返回默认工作区会话，保持向后兼容；`?workspace=` 返回对应工作区会话。
- **[工作区 dir 校验]** → `POST /api/workspaces` 校验 `dir` 为**存在的绝对目录**（`Files.isDirectory(dir.toAbsolutePath())`）；不在本机存在的目录拒绝（400）。避免"目录不存在但会话跑在错误 cwd"。
- **[并发 agent]** → 本 change 触碰 `Sidebar.tsx`/`App.tsx`/`WebAgentRuntime` 等并行改动面，实施时拉取最新、只 stage 相关文件。
- **[重命名与自动标题竞争]** → 自定义标题一旦写入即覆盖派生标题；`derive` 逻辑优先侧车，天然消除竞争（侧车不存在才派生）。

## Migration Plan

- 纯增量：新增 `workspaces/`、会话侧车 `<id>.meta.json`、新增端点/字段；**不迁移**已有 `sessions/*.jsonl`。
- 回滚：删除 workarea 相关端点与侧车逻辑即回退；默认工作区行为不变。
- 部署顺序：core（SessionStore + WorkspaceStore）→ web（Runtime/Controller）→ 前端。

## Open Questions

- 工作区 `name` 的合法性/长度上限（避免非法文件名字符）→ 建议 `[A-Za-z0-9._-]`、≤64 字符，非法 400。
- `dir` 是否允许**相对路径**？→ 建议仅绝对路径（`Files.isAbsolute`），相对 400。
- 会话重命名是否也应在 CLI `/resume` 标题体现？→ 本次仅 web。
