## Why

agent-web 当前的权限是**每个工具调用逐次确认**（`permission_request` SSE → 前端 yes/no/always），`PermissionManager` 基于 `PermissionPolicy.defaults()`（read=allow / write=ask / shell=ask）在会话创建时固定，运行期无法全局切换。用户希望在输入区提供像 DeepSeek Harness 那样的**权限模式下拉**（Read Only / Workspace Write / Full access），提前设定后由后端按模式自动裁决——非放行类别仍弹窗确认，从而减少只读/工作区写盘等高频场景的重复交互，同时保留高风险操作的确认兜底。

## What Changes

- **核心**：新增 `PermissionMode` 枚举（`READ_ONLY` / `WORKSPACE_WRITE` / `FULL_ACCESS`），每个模式映射到一份 `PermissionPolicy` 预设。
- **核心**：`PermissionManager` 支持**会话内重设模式**；`WORKSPACE_WRITE` 下 WRITE 工具按 `ToolContext.workingDirectory()` 判定工作区边界（目录内 → allow，目录外 → ask）。
- **核心**：敏感路径（`**/.ssh/**`、`**/.env*`、`**/*.pem`、`**/*credentials*`）默认强制 ask；`FULL_ACCESS` 时放行（仅工具级 `isDestructive`/黑名单 DENY 兜底）。
- **Web API**：新增 `POST /api/chat/{streamId}/permission {mode}` 实时切换模式；`POST /api/chat/send` 请求体支持可选初始 `permission_mode`。
- **核心**：`AgentLoop` 加 `setPermissionMode(...)`（同现有 `setModel` 模式，volatile 保证并发可见）。
- **前端**：composer 状态栏加权限模式下拉（默认 `Read Only`），切换即调后端接口；`ChatApi` 加对应方法。

## Capabilities

### New Capabilities
- `permission-mode`: 权限模式（Read Only / Workspace Write / Full access）的枚举与默认值、模式→策略映射、工作区边界裁决、敏感路径处理、运行时切换 API 与前端下拉控件。

### Modified Capabilities
- （无）——`web-ui` 的 `permission_request` SSE 契约不变，仅当模式裁决结果为 `ASK` 时仍走它；新端点与 send 请求新增的可选字段均纳入 `permission-mode`。

## Impact

- **core**：`agent-core/.../permission/{PermissionMode.java, PermissionManager.java, PermissionPolicy.java}`；`agent-core/.../core/AgentLoop.java`。
- **web API**：`agent-web/.../api/{ChatController.java, ChatSendRequest.java, PermissionRequestDto/java}`；`agent-web/.../stream/{WebAgentRuntime.java, ChatStreamService.java}`。
- **前端**：`agent-web/frontend/src/components/Composer.tsx`（下拉控件）、`api/chat.ts`（`send` 透传 `permission_mode` + 新增 `setPermission`）。
- **测试**：core `PermissionManagerTest`/`AgentLoop` 相关扩充；web `ChatControllerTest`/`ChatStreamServiceTest`；前端 `Composer`/`ChatPanel` 相关。
- **无破坏性 API**：新增端点 + 可选请求字段（向后兼容；缺省 `permission_mode` 时沿用现状 read=allow/write=ask/shell=ask）。

## Out of Scope

- 不做权限模式的**持久化**（模式仅会话内生效，刷新/新会话重置为默认 `Read Only`）。
- 不改变 CLI（`ChatCommand`）已有权限确认行为，也不把模式接入 CLI。
- 不新增工具类别；`OTHER` 类别（MCP/Skill/Plugin）在非 `FULL_ACCESS` 下继续按工具自身 `isReadOnly` 判定（默认 ask）。
