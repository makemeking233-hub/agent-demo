## Why

当前权限 / 路径沙箱体系与 dsh web 的 SandboxPolicy 抽象存在两层错位：其一，spec / 后端 / 前端三套命名不一致（`read_only` / `workspace_write` / `full_access` vs `plan` / `ask` / `danger-full` / `dontAsk`），Settings 里改 mode 不会影响实际对话；其二，FileTool / ShellAdapter / PermissionManager 三层各自决策路径边界，没有共享 `ctx.sandboxPolicy` 服务，per-call 解析粒度缺失，TOCTOU 未防护，拒绝反馈是字符串而非结构化。本次改动全面对齐 dsh web 的 SandboxMode + SandboxPolicy 抽象，统一命名、引入跨能力一致性、加固边界。

## What Changes

- **BREAKING** 后端 `PermissionMode` 枚举由 3 档（READ_ONLY / WORKSPACE_WRITE / FULL_ACCESS）改为 dsh 4 档（PLAN / ASK / DANGER_FULL / DONT_ASK），wire value 同步更换为 `plan` / `ask` / `danger-full` / `dontAsk`。
- **BREAKING** 前端 `ChatPanel.permissionMode` 不再用独立 `useState`，改为从 `useSettingsStore` 读 `general.permission.mode`，send body 透传该值。
- 新增 `SandboxPolicy` 服务（`agent-core.permission.SandboxPolicyService`），统一持有 mode + workspaceRoot + tempdir；bash / fs / 后续 terminal 三个 capability 都从它派生 writableRoots，不允许 drift。
- 新增 `writableRoots(policy)` 单一函数（Java 版对齐 dsh `sandbox/roots.ts`），返回值 = session cwd + `/tmp` + `os.tmpdir()`（canonicalize 后去重）。
- `AbstractFileTool.resolve` 改为：(1) 写入前 re-canonicalize 捕获 symlink swap（TOCTOU 防护）；(2) 根列表从 `SandboxPolicyService.resolve(ctx).writableRoots()` 取，**不再硬编码 2 个根**。
- `PermissionManager.decide` 拆分为「敏感路径检测」+「SandboxPolicy 默认裁决」两步；裁决结果携带 `FsDenialKind`（READ / WRITE / WORKSPACE_OUT_OF_BOUNDS / SENSITIVE_PATH / TOOL_DENY）+ 推荐的 escalation mode。
- 工具层拒绝时返回结构化 `PathResult.denied(kind, message, suggestedMode)`，前端渲染 `[sandbox: file access denied under <mode> mode]` marker + 同回合 escalation 提示。
- 权限模式实时切换（已有 POST `/api/chat/{streamId}/permission`）追加 SSE `sandbox/mode` 事件 + session log 持久化，便于审计与回放。
- 新增 `PermissionModeRequest` DTO 校验扩展：支持 `escalate: true` 同回合升级；后端在 deny 时返回 `{ denial: {kind, currentMode, suggestedMode, marker} }` 结构，前端据此渲染升级按钮。
- 不实现 bash 内核隔离（Seatbelt/bwrap/Landlock/Windows ACL）；`ShellAdapter` + `DefaultDenylistMatcher` 保留，留 TODO follow-up。Sensitive path 检测 + SandboxPolicy 默认裁决仍适用于 shell 工具调用。

## Capabilities

### New Capabilities
- `sandbox-policy`：统一描述权限模式（4 档 dsh 命名）+ 跨能力 sandbox policy 服务 + writableRoots 单一函数 + 拒绝反馈结构化 + SSE mode change 事件 + per-call policy 解析粒度。该 capability 合并现行 `permission-mode` 的全部需求与 `settings` 中 `permission.mode` 段。

### Modified Capabilities
- `settings`：删除 §"User can change permission mode" 段（迁移到 `sandbox-policy`）；保留 `general.permission.mode` YAML 路径作为设置持久化点，由 `sandbox-policy` 读取。
- `permission-mode`：archive 时通过 REMOVED Requirements 标注所有原需求迁移到 `sandbox-policy`，spec 文件随 archive 删除。

## Impact

- 后端代码：`agent-core/.../permission/`（PermissionMode 改名 + 拆分）+ `agent-core/.../tools/AbstractFileTool.java`（resolve 改造）+ 新增 `agent-core/.../permission/SandboxPolicyService.java` + `writableRoots.java` + `FsDenialKind.java` + `PathResult.denied()`。
- 后端 Web：`agent-web/.../api/ChatController.java`（permission_mode 字段校验放宽到 dsh 4 档）+ `agent-web/.../api/dto/PermissionModeRequest.java`（支持 escalate 字段）+ `agent-web/.../api/dto/PermissionDenialResponse.java`（新增）+ `agent-web/.../stream/ChatStreamService.java`（切换 mode 时广播 sandbox/mode SSE 事件 + 写 session log）。
- 前端代码：`agent-web/frontend/src/components/PermissionModeSelect.tsx`（保持 4 档 dsh 命名）+ `agent-web/frontend/src/components/ChatPanel.tsx`（移除独立 useState，从 settings 读）+ `agent-web/frontend/src/api/chat.ts`（permissionMode 类型对齐）+ `agent-web/frontend/src/components/ToolResult.tsx`（渲染 sandbox marker + 升级按钮）。
- 配置文件：`~/.agent-demo/settings.yaml` 的 `general.permission.mode` 路径保留；新增 `general.permission.escalatable: true`（默认）控制是否允许同回合升级。
- 测试：`PermissionModeTest`（4 档 wire value 解析）+ `SandboxPolicyServiceTest`（per-call resolve + writableRoots 派生）+ `AbstractFileToolTest`（TOCTOU 防护 + writableRoots 路径判定）+ `WecomMessageDispatcher`（FULL_ACCESS 默认改为 DANGER_FULL）+ ChatControllerTest（permission_mode 4 档校验）+ 前端 PermissionModeSelect 4 档选项 + ChatPanel 从 settings 读取 + 拒绝反馈结构化渲染。
- Spec：`openspec/specs/sandbox-policy/spec.md`（新增，~30-40 条 Requirement）+ `openspec/specs/permission-mode/spec.md`（archive 时 REMOVED 全部迁移）+ `openspec/specs/settings/spec.md`（删除 §Permission mode 段，指向 sandbox-policy）。
- 文档：`README.md` §3.11 / §3.13 同步更新（权限模式章节 + Memory 章节的 cross-reference）。
- 兼容：旧 wire value（`read_only` / `workspace_write` / `full_access`）首次启动时一次性迁移到新值（`plan` / `ask` / `danger-full`），settings.yaml 加载时识别两种写法并 normalize。
- 风险：bash 内核隔离暂不做，shell 工具仍依赖 denylist（已知限制）；TOCTOU 通过 re-canonicalize 收窄但承认非 0（与 dsh 同 trade-off）。
