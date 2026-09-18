# Tasks: align-dsh-workspace

> 对齐 DSH Web workspace 模型（Top-5 gap）。预估 ~4h。

## 1. WorkspaceStore 数据模型 v2

- [ ] 1.1 `Workspace` record 加字段：`id` (UUID) / `title` / `updatedAt` / `sessionIds` / `Status`
- [ ] 1.2 meta.json schema v2：`{version:2, name, id, path, title, created_at, updated_at, session_ids}`
- [ ] 1.3 v1 → v2 读时迁移（lazy migration）：缺字段补默认值，list() 时确保 snapshot 内存里齐
- [ ] 1.4 新增 `workspaces/order.json` 持久化 durable order（`{version:1, order:[names...]}`）
- [ ] 1.5 加 `Status` 枚举：`OK` / `MISSING_DIR`；`list(name)` 返回 status

## 2. WorkspaceStore 接口重写

- [ ] 2.1 `create(agentDataDir, dir)` 改签名：接收 dir 不再接收 name → `realpath` 规范化 + 查同 canonical 复用 + basename 派生 name/title
- [ ] 2.2 `delete(agentDataDir, name)`：删 `<ws>/` 目录 + from order.json，不动 dir / session log
- [ ] 2.3 `rename(agentDataDir, name, newTitle)`：更新 meta.json title + updated_at
- [ ] 2.4 `insertBefore(agentDataDir, name, beforeName)`：持久化 order.json；beforeName=null 移到末尾
- [ ] 2.5 `ensureOrderInitialized(agentDataDir)`：启动时调，按 session header cwd 自动 attach + 按 createdAt desc 初始化 order
- [ ] 2.6 `list(agentDataDir)` 按 durable order 返回
- [ ] 2.7 `list(agentDataDir, name)` 返回单 workspace 含 status
- [ ] 2.8 文件锁：`<name>.lock` 防并发 create/delete/rename 冲突

## 3. WorkspaceController 新增端点

- [ ] 3.1 `POST /api/workspaces` 改 req: `{path}`（去掉 name）
- [ ] 3.2 `DELETE /api/workspaces/{name}` → 204 / 404
- [ ] 3.3 `PATCH /api/workspaces/{name}` (`{title}`) → 200 / 400 / 404
- [ ] 3.4 `PUT /api/workspaces/order` (`{order:[names...]}`) → 200 / 400
- [ ] 3.5 `WorkspaceDto` 加 `id` / `title` / `updatedAt` / `status` 字段（向后兼容旧字段）
- [ ] 3.6 启动时调 `WorkspaceStore.ensureOrderInitialized`

## 4. WebAgentRuntime + ChatStreamService 接入

- [ ] 4.1 `WebAgentRuntime` 启动时调 `WorkspaceStore.ensureOrderInitialized(agentDataDir)`
- [ ] 4.2 现有 `WorkspaceStore.list()` 调用全部通过 `WebAgentRuntime` 走 ensureOrderInitialized

## 5. Sidebar UI 改造

- [ ] 5.1 workspace 行加右键菜单（重命名 / 删除 / confirm）
- [ ] 5.2 workspace 行加 drag handle（HTML5 drag-and-drop 跨行重排）
- [ ] 5.3 拖拽松手调 PUT `/api/workspaces/order`
- [ ] 5.4 缺失目录（`status='missing_dir'`）workspace UI 标红 + 「重新连接」按钮
- [ ] 5.5 workspace 名旁显示 `title`（与 `name` 不同时）

## 6. WorkspacePickerModal 改造

- [ ] 6.1 去掉「工作区名称」input 字段
- [ ] 6.2 path 输入框自动 focus；submit 后立即创建（title 自动派生 basename）
- [ ] 6.3 创建成功后自动切换到新 workspace + 关闭 modal

## 7. 测试

- [ ] 7.1 `WorkspaceStoreTest`：v1 → v2 迁移 + realpath 复用 + 同 path 同 canonical + 不同 path 同 basename + delete 保留 dir/session + rename + insertBefore + order 持久化 + missing-dir status + ensureOrderInitialized 派生顺序（10+ 用例）
- [ ] 7.2 `WorkspaceControllerTest`：5 个新端点 + 错误码 + 旧端点兼容（5+ 用例）
- [ ] 7.3 `WorkspacePickerModal.test.tsx`：去掉 name 输入 + 提交即创建 + 自动切换（3+ 用例）
- [ ] 7.4 `Sidebar.test.tsx`：右键菜单（重命名/删除）+ drag 重排 + missing-dir 渲染（5+ 用例）
- [ ] 7.5 既有 workspace 相关测试通过（SessionControllerTest / WorkspaceControllerTest 等不破）

## 8. 验证与归档

- [ ] 8.1 跑 `mvn -o -pl agent-core,agent-web test` 全绿
- [ ] 8.2 跑 `mvn -o -pl agent-web verify -DskipNpm=true` jacoco 通过（新增包 workspace-entity BRANCH ≥70%；既有 fail 放行）
- [ ] 8.3 跑 `npx vitest run` 全绿（vitest 244+ → 270+）
- [ ] 8.4 中文 Conventional Commits 分 commit（feat/fix/docs/test）+ 立即 push
- [ ] 8.5 `openspec validate align-dsh-workspace --type change --strict` 通过
- [ ] 8.6 `openspec archive align-dsh-workspace --yes` 合并 delta spec
- [ ] 8.7 合并回 main + 在 main 上复验 + push main + cleanup