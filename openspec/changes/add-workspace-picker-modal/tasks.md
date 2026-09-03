## 1. 后端基础设施

- [x] 1.1 新增 `FsEntry` / `FsListResponse` / `FsMkdirRequest` / `FsHomeResponse` / `FsDrivesResponse` 五个 record DTO（在 `agent-web/.../api/dto/` 下），含最小字段与文档注释；`mvn -pl agent-web compile` 通过
- [x] 1.2 新增 `HomePathGuard` 工具类（`agent-web/.../web/security/HomePathGuard.java`）：暴露 `resolveWithinHome(String input)` 返回 `Path` 或抛 `HomePathException`，内部用 `Path.toRealPath()` + `startsWith(homeRealPath)` 判前缀；对应单元测试覆盖正常路径 / `..` 越界 / 符号链接越界 / 路径不存在 / 路径非绝对
- [x] 1.3 新增 `FsController`（`agent-web/.../api/FsController.java`）四个端点骨架：`GET /api/fs/home`、`GET /api/fs/list`、`POST /api/fs/mkdir`、`GET /api/fs/drives`；注入 `WebAgentRuntime` 拿环境、`HomePathGuard` 做安全；DTO 与 controller 通过 jackson 自动序列化；`mvn -pl agent-web compile` 通过
- [x] 1.4 写 `FsControllerTest`（`agent-web/.../api/FsControllerTest.java`，用 `@WebFluxTest` 或 `WebTestClient`）：覆盖正常列表 / 越界 403 / 路径不存在 404 / 非绝对路径 400 / Windows 盘符 / mkdir 成功 + 重名 409 + 越界 403 + 非法名 400；`mvn -pl agent-web test` 全绿

## 2. 前端 Modal 基础设施

- [x] 2.1 新增 `frontend/src/api/fs.ts`：暴露 `getHome() / listDir(path, includeHidden) / mkdir(path) / getDrives()` 四个函数，统一走 `fetch`，错误统一抛 `Error` 带服务端错误码；新增 `frontend/src/api/fs.test.ts` 用 vitest mock fetch 覆盖 200/4xx/5xx 三种分支
- [x] 2.2 新增 `WorkspacePickerModal` 组件骨架（`frontend/src/components/WorkspacePickerModal.tsx` + `.module.css`）：Modal 外壳 + Esc/外部点击关闭 + 关闭时写 localStorage；用 `useReducer` 管理 `currentPath / entries / loading / error / selectedPath / workspaceName / includeHidden / isCreatingWs` 八字段；默认定位到 `localStorage["agent-demo.workspace-picker.last-path"]` 或 `getHome()` 结果
- [x] 2.3 实现 Modal 主体内容：路径输入框 + Enter 跳转 + 面包屑（点击跳转 / 最左侧"此电脑"层） + 工具栏（新建文件夹 inline 输入框、刷新、显示隐藏切换） + 条目列表（目录优先 + 名称升序、双击进、单击选中、文件灰掉、面包屑式加载/错误/空态）；`vitest run WorkspacePickerModal.test.tsx` 增 4 个用例（路径输入跳转 / 双击进入 / 面包屑跳转 / 文件不可选）
- [x] 2.4 实现提交区：底部"工作区名称"输入框（默认 `basename(selectedPath)`）+ 当前路径展示 + "取消 / 选择此目录"按钮；提交按钮在 `selectedPath` 非空且为目录、`workspaceName` 通过客户端校验（与 `WorkspaceStore.validateName` 同规则）时启用；调用 `createWorkspace(name, dir)` props 回调，成功后调用 props.onSuccess(workspaceName) 触发外部刷新 + 切换；增 2 个 vitest 用例（名称冲突 / 路径不存在）
- [x] 2.5 新增 `frontend/src/components/WorkspacePickerModal.test.tsx` 总装：覆盖"打开 → 浏览 → 双击进 → 选中 → 改 name → 提交 → 回调被调"主路径 + "按 Esc 关闭" + "localStorage 写入" + "localStorage 失效回退 $HOME" + "新建文件夹"五个集成用例；`vitest run --coverage WorkspacePickerModal` 覆盖率 ≥ 80%

## 3. 集成

- [ ] 3.1 改造 `Sidebar.tsx`：删除 `showCreateWs / wsName / wsDir / wsError` 四个 state 与 `workspaceForm` JSX 块（约 30 行）；保留 `onCreateWorkspace` props，新增 `onCreateWorkspaceFromPicker(name, dir)` 透传 + `WorkspacePickerModal` 嵌入；`+` 按钮 onClick 改为 `setShowPicker(true)`；样式同步删除 `.workspaceForm / .workspaceInput / .workspaceError` 三类（`Sidebar.module.css`）；`Sidebar.test.tsx` 增 1 个用例（点击 `+` 弹 Modal）
- [ ] 3.2 改造 `App.tsx`：新增 `showPicker` state + `handleCreateWorkspaceFromPicker(name, dir)` 回调（成功后刷新 workspaces 列表 + `setActiveWorkspace(newWs.name)`）；把 Modal 渲染放在 `Sidebar` 旁边；`mvn -pl agent-web verify` 通过（包含 jacoco 门禁）
- [ ] 3.3 手动跑 `mvn -pl agent-web spring-boot:run -Dspring-boot.run.profiles=web` + 浏览器打开 `http://127.0.0.1:8080`，端到端验证：点击 `+` 弹 Modal → 浏览/跳转/新建/选目录 → 输入 name → 点选择 → Sidebar 出现新工作区并自动切换；截图归档到 `docs/test-agent-demo/<时间戳>-workspace-picker-e2e/` 四件套

## 4. 收尾

- [ ] 4.1 跑 `mvn verify`（含 jacoco），确认 LINE ≥ 80% / BRANCH ≥ 70% 全绿；如有未覆盖分支补针对性单测
- [ ] 4.2 更新 `docs/test-agent-demo/test-guide.md` §1 登记表追加一行（批次目录、测试主题、用例数、结果、四件套✅、状态=已归档），§2 追加批次详情小节；按 `openspec-archive-change` 流程收尾：所有 task 勾选 → `openspec validate` → `mvn verify` 全绿 → `openspec archive-change add-workspace-picker-modal` → 自动归档到 `openspec/changes/archive/`；commit + push
