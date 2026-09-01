## 1. Core：PermissionMode 枚举

- [ ] 1.1 先写 `agent-core/.../permission/PermissionModeTest`（红）：覆盖 `READ_ONLY` / `WORKSPACE_WRITE` / `FULL_ACCESS` 三值、`from("read_only")` 等合法映射、`from("bogus")` 抛 `IllegalArgumentException`。
- [ ] 1.2 实现 `PermissionMode` 枚举（三值 + `from(String)` + 每类别的 allow/ask 判定方法），测试转绿（绿）。
- [ ] 1.3 `PermissionMode` 增加每类别默认结果的辅助方法（供 `PermissionManager` 复用），并补对应断言。

## 2. Core：PermissionManager 模式感知 + 工作区边界

- [ ] 2.1 先扩展 `PermissionManagerTest`（红）：`setMode(READ_ONLY)` 下 READ→allow / WRITE→ask；`setMode(FULL_ACCESS)` 全类别→allow；敏感路径在非 full_access→ask、full_access→allow。
- [ ] 2.2 实现 `PermissionManager.setMode(...)`、`setWorkingDirectory(Path)`；把 `policy` 的 `final` 去掉，`decide(toolName, input, ctx)` 按新顺序重排（full_access→allow；敏感→ask；否则按 mode+category）。测试转绿。
- [ ] 2.3 先写工作区边界用例（红）：`WORKSPACE_WRITE` 下 WRITE 路径在工作目录内→allow、目录外→ask。实现 `path.normalize().startsWith(workingDir normalize)` 判定，转绿。
- [ ] 2.4 `PermissionManagerObservabilityTest` 补充：不同 mode 下 permission/decision 事件广播仍只对 ask/deny 产生（allow 不 broadcast）。

## 3. Core：AgentLoop 运行时切换

- [ ] 3.1 先扩展 `AgentLoopToolContextTest` / `AgentLoopFactoryTest`（红）：`createLoop`/`buildLoop` 传入 mode 后，`AgentLoop` 用该 mode 装配 `PermissionManager`。
- [ ] 3.2 实现 `AgentLoop`：加 `private volatile PermissionMode mode`（默认 `READ_ONLY`），构造时 `perms.setMode(mode)` + `perms.setWorkingDirectory(workingDir)`；新增 `setPermissionMode(PermissionMode)`（volatile，内部同步 `perms.setMode`，对齐 `setModel` 范式），转绿。
- [ ] 3.3 `AgentLoopFactory.buildLoop` 与 `WebAgentRuntime.createLoop` 增加 `PermissionMode` 参数（缺省 `READ_ONLY`），并透传到 `AgentLoop`，转绿。

## 4. Web API：初始模式 + 实时切换

- [ ] 4.1 `ChatSendRequest` 加可选 `permission_mode`；`ChatController` 解析并透传 `ChatStreamService.create(sessionId, model, mode)`；补 `ChatControllerTest`（含非法 `permission_mode`→400）。
- [ ] 4.2 `ChatStreamService.create` 增加 mode 参数，透传 `runtime.createLoop(...)`；新建 `PermissionModeRequest` DTO；新增 `setPermission(streamId, mode)`（`actives.get(id).loop().setPermissionMode(mode)`）。补 `ChatStreamServiceTest`（成功 / 未知流 / 非法 mode）。
- [ ] 4.3 `ChatController` 新增 `POST /api/chat/{stream_id}/permission`，body `{"mode":"..."}`；返回 `200 {"ok":true,"mode":...}`；未知流→404 `stream_not_found`；非法 mode→400 `invalid_mode`。补 `ChatControllerTest`。
- [ ] 4.4 `WebAgentRuntimeTest` / `ChatStreamServiceTest` 覆盖「send 带初始 mode → 该流按 mode 裁决」。

## 5. 前端：权限模式下拉

- [ ] 5.1 `ChatApi` 加 `setPermission(streamId, mode)`（`POST /api/chat/{stream_id}/permission`）；`send` 透传可选 `permission_mode`。补前端测试。
- [ ] 5.2 `Composer.tsx` statusBar 区加权限下拉（Read Only / Workspace Write / Full access），缺省 Read Only；onChange 调 `ChatApi.setPermission(streamId, mode)`。补 `Composer`/`ChatPanel` 测试（下拉渲染、切换调用）。
- [ ] 5.3 `App`/`ChatPanel` 持有当前 `streamId`，初始化新会话时随 `send` 传 `permission_mode`。

## 6. 文档与验证

- [ ] 6.1 更新 `docs/design/design.md` §6.5，补权限模式的说明（三档 + 工作区边界 + 敏感路径 + 工具级 DENY 兜底）。
- [ ] 6.2 更新 `README.md` Web UI 权限部分，说明权限模式下拉。
- [ ] 6.3 后端验证：`mvn -o verify -DskipNpm=true` 全绿（含 jacoco LINE≥80% / BRANCH≥70%）。
- [ ] 6.4 前端构建：`npm run build`（`agent-web/frontend`）成功后 `mvn -o package`，确认 SPA bundle 更新到 `resources/static`；`check-md.sh` 校验本 change 下 md 无 Mermaid 违规。
- [ ] 6.5 提交并推送：按变更点拆分中文 commit（`feat(permission-mode): ...`），逐一 commit 即 push。
