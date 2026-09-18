# permission-mode Specification（rewrite-permission-mode-dsh REMOVED）

## REMOVED Requirements

### Requirement: 权限模式选择

**Reason**：原 3 档（read_only / workspace_write / full_access）命名与前端 4 档 dsh 命名（plan / ask / danger-full / dontAsk）不一致；统一迁移到 `sandbox-policy` capability 的 4 档 SandboxMode。

**Migration**：详见 `openspec/specs/sandbox-policy/spec.md` §"SandboxMode 四档定义"；旧 wire value 兼容由 sandbox-policy §"旧 wire value 兼容迁移" 覆盖。

---

### Requirement: 权限模式裁决策略

**Reason**：原 READ/WRITE/SHELL/OTHER 四分类 + mode × category 决策表，与 dsh 的 per-call capability（FS/BASH/TERMINAL）抽象不一致；统一为 SandboxPolicyService + mode × capability 默认裁决表（见 sandbox-policy §"mode × capability 默认裁决表"）。

**Migration**：原决策表不再使用；新决策表见 sandbox-policy。

---

### Requirement: 工作区写入边界

**Reason**：原「工作区写入边界」由 PermissionManager 通过 `isWithinWorkspace(path, ctx)` 单独判断；新设计改为 writableRoots 单一派生函数（sandbox-policy §"writableRoots 单一派生函数"），FileTool 与 ShellTool 都从 SandboxPolicyService 拿，避免 drift。

**Migration**：原 PermissionManager.isWithinWorkspace 私有方法随 PermissionManager 拆分而删除；workspace 内/外判定改由 FileTool 调 `SandboxPolicyService.defaultDecision` 时传入 target 自动判定。

---

### Requirement: 敏感路径处理

**Reason**：保留行为不变，仅 API 路径迁移；原 PermissionManager 的敏感路径强制询问逻辑下沉到 `PermissionPathMatcher` + `SandboxPolicyService`（见 sandbox-policy §"敏感路径强制询问" + §"默认敏感路径 patterns"）。

**Migration**：PermissionPathMatcher 保留（package + class 名不变）；PermissionManager 不再持有 pathMatcher 实例，改由 SandboxPolicyService 注入。

---

### Requirement: 权限模式实时切换

**Reason**：原 `POST /api/chat/{stream_id}/permission` 实时切换 API 保留；新增 escalate 升级（sandbox-policy §"escalate 同回合升级"）+ SSE sandbox/mode 事件广播（sandbox-policy §"SSE sandbox/mode 事件广播"）+ session log 持久化（sandbox-policy §"session log 持久化模式变更"）。

**Migration**：API 路径不变；请求体新增 `escalate: bool` 字段；响应体新增 `effective_mode` 字段；SSE 订阅端需订阅新增的 `sandbox/mode` 事件。

---

### Requirement: 初始权限模式

**Reason**：原 `POST /api/chat/send` 请求体 `permission_mode` 字段保留；wire value 由 `read_only` / `workspace_write` / `full_access` 改为 `plan` / `ask` / `danger-full` / `dontAsk`，旧值自动迁移（sandbox-policy §"旧 wire value 兼容迁移"）。

**Migration**：前端 ChatPanel 不再持有独立 `useState<PermissionMode>`，改从 `useSettingsStore` 读 `general.permission.mode`（sandbox-policy §"前端 ChatPanel 从 settings 读取 mode"）；send body 透传该值。

---

### Requirement: 工具级拒绝兜底

**Reason**：保留行为不变；原 Tool.checkPermissions 返回 DENY 兜底逻辑下沉到 SandboxPolicyService + AgentLoop；语义不变。

**Migration**：AgentLoop 调用顺序调整为 (1) SandboxPolicyService.defaultDecision → (2) Tool.checkPermissions → (3) 后者返回 DENY 时直接拒绝（不弹窗）。
