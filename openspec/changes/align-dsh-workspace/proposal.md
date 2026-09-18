# align-dsh-workspace

## Why

当前 `agent-demo` 的 workspace 模型是一个轻量级「目录 + meta.json」映射，与 DSH Web（DeepSeek Harness）的 workspace 完整领域模型差距明显，导致 5 个明显 gap（按优先级）：

1. **路径未规范化**：用 `Paths.get(dir)` 直接读，同一目录不同大小写/符号链接可建多个 workspace（DSH：`fs.realpath` 规范化 + 同 canonical path 最多 1 record）
2. **Workspace 不可删**：没有 DELETE 端点，workspace 永久存在（DSH：`workspace.delete` 仅删 record，session/dir/session log 不动，被删 workspace 的 sessions 自动变 Ungrouped）
3. **Workspace 不可重命名**：没有 `setTitle` 端点，display title 跟 name 绑定（DSH：title 与 path 完全解耦，多 workspace 可共享 basename-derived title，可独立 setTitle）
4. **没有 durable order 持久化**：用字母序，显式创建的 workspace 排在默认 workspace 后（DSH：durable order 持久化到 table，启动时初始化，支持 `insertBefore` 拖拽重排）
5. **目录不存在时硬拒绝**：`Files.isDirectory()` 失败 → 400（DSH：`status() = 'missing-dir'`，record 保留，UI 标红）

DSH spec 来源：
- `E:\claude-projects\deepseek-harness\packages\workspace\workspace\README.md`
- `E:\claude-projects\deepseek-harness\.agents\notes\implemented\feature\2026-07-25-workspace-ui-product-flow.md`

## What Changes

- `WorkspaceStore` 加 `id` 字段 + `title`/`createdAt`/`updatedAt` + `sessionIds[]` 候选索引（按 cwd 自动 attach）；meta.json 升级到 v2 schema（含以上全部字段）
- `WorkspaceStore.create()` 接收 path → `realpath` 规范化 → 查同 canonical path 已有 record → 复用 or 新建；basename 自动派生 title（同名 title 允许）
- `WorkspaceStore.delete(name)` 仅删 `<agentDataDir>/workspaces/<name>/` 目录，session log + dir 完整保留
- `WorkspaceStore.rename(name, newTitle)` 改 title（不重命名 dir）
- `WorkspaceStore.insertBefore(name, beforeName)` 持久化 order 到 `<agentDataDir>/workspaces/order.json`；list() 按 durable order 排序
- `WorkspaceStore.status(name)` 返回 `ok` / `missing-dir`；create 校验改成「missing-dir 允许，status 在 list 返回」
- `WorkspaceController` 加 `DELETE /api/workspaces/{name}` + `PATCH /api/workspaces/{name}` (`{title?}`) + `PUT /api/workspaces/order` (`{order: [names...]}`) 三个端点
- `Sidebar` UI 加右键菜单：删除（confirm）+ 重命名（弹 input）+ drag-to-reorder
- `WorkspacePickerModal` 把「输 name + 选 dir」改为「只选 dir」（title 自动派生 basename），减少一步交互
- `WebAgentRuntime` + `ChatStreamService.create()` 启动时调 `WorkspaceStore.list()` 持久化 durable order

## Impact

- 受影响模块：`agent-core/session/WorkspaceStore` + `agent-web/api/WorkspaceController` + `agent-web/frontend/src/components/Sidebar.tsx` + `WorkspacePickerModal.tsx`
- API/接口：扩展（WorkspaceStore 加新方法；WorkspaceDto 加 `id/title/updatedAt/status` 字段；WorkspaceController 加 3 端点；schema v2 向后兼容 v1）
- 数据/Schema：meta.json v2（v1 自动迁移，v2 加 `id/title/updatedAt/sessionIds`）；新增 `workspaces/order.json`
- 行为变化：picker 少一步输入；workspace 可删 / 可重命名 / 可拖拽重排 / 缺失目录容忍

## Out of Scope

- 不做 Session 按 cwd 自动 attach（DSH #6，留作后续 `align-dsh-session-candidate` change）
- 不做 Workspace Intent lifecycle（DSH #10，v1.0 无此 UI 入口）
- 不做 Ungrouped 分组（DSH #8，需要 session attach 后才有意义）
- 不做 picker 单 action 改造（DSH #7 之一，本次只去掉 name 输入步骤）
- 不改 `workspace` 命令的 CLI 行为（CLI 走 `init`/`chat`，web 端独享 Workspace 概念）

## Compliance

- `AGENTS.md §2.5.4`：必须先 `openspec-explore`（✅ 已在 explore 模式盘点）+ 必须建 worktree（✅）
- `AGENTS.md §2.7.4`：只用显式 `git add <path>`
- `AGENTS.md §2.7.5`：合并门禁（测试全绿 + jacoco）
- `AGENTS.md §2.5.4` + `§2.7.1`：doD/退出码走 D3 验证（双 baseline verify）