# sandbox-policy Specification

## Purpose
TBD - created by archiving change rewrite-permission-mode-dsh. Update Purpose after archive.
## Requirements
### Requirement: SandboxMode 四档定义

系统 SHALL 提供四档 SandboxMode：`plan`（只读 + 计划工具）/ `ask`（只读 + 写/Shell 询问）/ `danger-full`（解除文件 fence，工具级 DENY 兜底）/ `dontAsk`（自动 allow 无 UI 弹窗）。后端枚举对应 `PLAN` / `ASK` / `DANGER_FULL` / `DONT_ASK`；wire value 为字符串小写 `plan` / `ask` / `danger-full` / `dontAsk`。

#### Scenario: 四档枚举存在
- **WHEN** 加载 `agent-core/.../permission/SandboxMode.java`
- **THEN** 枚举 SHALL 包含 `PLAN`、`ASK`、`DANGER_FULL`、`DONT_ASK` 四个值
- **AND** 每个值的 `wireValue()` 返回对应小写字符串

#### Scenario: 非法 wire value 解析失败
- **WHEN** 调用 `SandboxMode.from("read_only")` 或 `SandboxMode.from("invalid")`
- **THEN** SHALL 抛出 `IllegalArgumentException`，错误信息包含支持的四档列表

#### Scenario: 默认模式为 plan
- **WHEN** 未显式指定初始 mode 时创建会话
- **THEN** 该会话 SHALL 使用 `PLAN` 作为初始 SandboxMode

---

### Requirement: SandboxPolicy 记录完整策略

系统 SHALL 用 `SandboxPolicy` record 描述一次工具调用的完整策略：`{ mode: SandboxMode, workspaceRoot: Path, tempRoots: List<Path>, capability: Capability }`。`Capability` 为枚举 `FS` / `BASH` / `TERMINAL` 三档，表示调用所属能力。

#### Scenario: SandboxPolicy 字段完整
- **WHEN** 创建一个 `SandboxPolicy(PLAN, Path.of("/proj"), List.of(Path.of("/tmp")), FS)`
- **THEN** record SHALL 暴露 `mode`、`workspaceRoot`、`tempRoots`、`capability` 四个字段

#### Scenario: capability 决定 policy 适用面
- **WHEN** `capability = BASH` 的 SandboxPolicy 被传给 fs 工具
- **THEN** fs 工具 SHALL 拒绝并抛 `IllegalArgumentException`（防止 capability 误用）

---

### Requirement: writableRoots 单一派生函数

系统 SHALL 在 `agent-core/.../permission/WritableRoots.java` 提供单一函数 `writableRoots(SandboxPolicy policy): List<Path>`，派生规则：mode == PLAN/ASK 时返回空列表（无写入）；mode == DANGER_FULL 时返回 `List.of()`（不 fence）；mode == DONT_ASK 时返回 `[workspaceRoot, /tmp, tmpdir]` 经 canonicalize 后去重。

#### Scenario: PLAN/ASK 不允许写入
- **WHEN** 调用 `writableRoots(SandboxPolicy(PLAN, ..., FS))` 或 `writableRoots(SandboxPolicy(ASK, ..., FS))`
- **THEN** SHALL 返回空 `List<Path>`

#### Scenario: DONT_ASK 派生三个根
- **WHEN** 调用 `writableRoots(SandboxPolicy(DONT_ASK, Path.of("/proj"), FS))`
- **THEN** SHALL 返回 `[Path("/proj").toRealPath(), Path("/tmp").toRealPath(), tmpdir().toRealPath()]`
- **AND** 三个路径 SHALL 经 canonicalize 后去重

#### Scenario: DANGER_FULL 不 fence
- **WHEN** 调用 `writableRoots(SandboxPolicy(DANGER_FULL, ..., FS))`
- **THEN** SHALL 返回空 `List<Path>`（调用方据此判断无需 fence）

#### Scenario: canonicalize 失败回退原拼写
- **WHEN** `Path.toRealPath()` 抛出 IOException
- **THEN** writableRoots SHALL 回退到原拼写路径，不抛异常

---

### Requirement: per-call policy 解析

系统 SHALL 在 `agent-core/.../permission/SandboxPolicyService` 提供 `resolve(Tool.ToolContext ctx, Capability capability): SandboxPolicy` 方法，每次工具调用时调用以获取当前完整策略。`resolve` SHALL 组合：(1) 当前会话 mode（从 ctx 或注入字段）；(2) session workspaceRoot（从 ctx.workingDirectory()）；(3) capability 入参；(4) tempdir（从 `System.getProperty("java.io.tmpdir")`）。

#### Scenario: 每次调用重新解析
- **WHEN** 同一 session 内连续两次调用 `SandboxPolicyService.resolve(ctx, FS)`
- **AND** 中间通过 `setMode(DANGER_FULL)` 修改了 mode
- **THEN** 第二次调用 SHALL 返回 mode=DANGER_FULL 的 policy

#### Scenario: capability 必填
- **WHEN** 调用 `SandboxPolicyService.resolve(ctx, null)`
- **THEN** SHALL 抛出 `IllegalArgumentException`

#### Scenario: workingDirectory 为空时抛错
- **WHEN** `ctx.workingDirectory()` 为 null
- **THEN** resolve SHALL 抛 `IllegalStateException`，错误信息提示「session 未绑定工作目录」

---

### Requirement: mode × capability 默认裁决表

系统 SHALL 按下表裁决 `SandboxPolicyService.defaultDecision(SandboxMode mode, Capability capability, Path target)`：

| mode \ capability | FS read | FS write | BASH | TERMINAL |
|-------------------|---------|----------|------|----------|
| PLAN | allow | ask（target 在 workspace 内仍 ask；plan 模式禁止写入）| ask | ask |
| ASK | allow | ask（target 在 workspace 内 allow，超出 ask）| ask | ask |
| DANGER_FULL | allow | allow | allow | allow |
| DONT_ASK | allow | allow（workspace + temp）| allow | allow |

`allow` 表示 `PermissionDecision.allow()`；`ask` 表示 `PermissionDecision.ask()`。

#### Scenario: ASK 模式 workspace 内 fs write 放行
- **WHEN** mode=ASK，capability=FS，target=/proj/foo.txt（在 workspaceRoot 内）
- **THEN** SHALL 返回 `PermissionDecision.allow()`

#### Scenario: ASK 模式 workspace 外 fs write 询问
- **WHEN** mode=ASK，capability=FS，target=/etc/passwd（不在 workspaceRoot 内）
- **THEN** SHALL 返回 `PermissionDecision.ask()`

#### Scenario: PLAN 模式任何 fs write 都询问
- **WHEN** mode=PLAN，capability=FS，target=/proj/foo.txt
- **THEN** SHALL 返回 `PermissionDecision.ask()`（plan 模式禁止写入）

#### Scenario: DANGER_FULL 模式任何操作都放行
- **WHEN** mode=DANGER_FULL，capability=BASH，target=null
- **THEN** SHALL 返回 `PermissionDecision.allow()`

---

### Requirement: 敏感路径强制询问

系统 SHALL 保留 `PermissionPathMatcher` 检测敏感路径（`**/.ssh/**`、`**/.env*`、`**/*.pem`、`**/*credentials*` 默认列表）；当目标路径命中敏感 pattern 且 mode 不为 DANGER_FULL 时，裁决 SHALL 升级为 `ask`（即使默认裁决为 allow）。

#### Scenario: ASK 模式命中敏感路径
- **WHEN** mode=ASK，target=/home/user/.ssh/id_rsa
- **THEN** SHALL 返回 `PermissionDecision.ask()`（即使 capability=FS read）

#### Scenario: DANGER_FULL 模式命中敏感路径
- **WHEN** mode=DANGER_FULL，target=/home/user/.ssh/id_rsa
- **THEN** SHALL 返回 `PermissionDecision.allow()`（危险模式下放行敏感路径）

---

### Requirement: 工具级 DENY 兜底

系统 SHALL 保证 `SandboxPolicyService.defaultDecision` 返回 `allow` 时，仍由 `Tool.checkPermissions` 做最终裁决；若工具返回 `DENY`，最终决策 SHALL 为 `deny`，不弹窗。

#### Scenario: DANGER_FULL 下破坏性工具被 DENY
- **WHEN** mode=DANGER_FULL，调用 `WriteFile` 工具
- **AND** WriteFile 工具的 `checkPermissions` 返回 `DENY`
- **THEN** AgentLoop SHALL 拒绝该调用
- **AND** SHALL 不推送 `permission_request` SSE 事件

---

### Requirement: TOCTOU 防护（写之前 re-canonicalize）

系统 SHALL 在 `AbstractFileTool` 的写入路径（writeText / editText / 等）中，**调用底层 fs 操作前**对目标路径调用 `Path.toRealPath()` 重新解析，捕获自工具解析以来发生的 symlink swap。canonicalize 失败时（路径不存在），回退到工具已 normalize 的路径并记录 WARN。

#### Scenario: symlink swap 写入被拒
- **WHEN** 工具解析 target=/proj/safe.txt（确认在 workspaceRoot 内）
- **AND** 在 AbstractFileTool 调用底层 write 之间，攻击者把 /proj/safe.txt 替换为指向 /etc/passwd 的 symlink
- **THEN** AbstractFileTool SHALL 在写入前调用 `Path.toRealPath()` 解析到 /etc/passwd
- **AND** SHALL 抛 `PathResult.denied(OUT_OF_BOUNDS, ...)` 拒绝写入

#### Scenario: 路径不存在回退
- **WHEN** target 路径不存在（新建文件）
- **THEN** SHALL 调用 `Path.toRealPath()` 捕获 IOException
- **AND** SHALL 回退到原 normalized 路径继续写入
- **AND** SHALL 记录一条 WARN 日志

---

### Requirement: 结构化拒绝反馈（FsDenialKind）

系统 SHALL 定义枚举 `FsDenialKind`：`READ_OUT_OF_BOUNDS` / `WRITE_OUT_OF_BOUNDS` / `SENSITIVE_PATH` / `TOOL_DENY` / `MODE_REJECTED`。`AbstractFileTool.resolve` 拒绝时 SHALL 返回 `PathResult.denied(FsDenialKind, currentMode, suggestedMode, message)`，结构化携带拒绝原因 + 当前 mode + 推荐升级 mode。

#### Scenario: WORKSPACE_OUT_OF_BOUNDS 携带 suggestedMode
- **WHEN** mode=ASK 下写入 /etc/passwd
- **THEN** `PathResult.denied()` SHALL 携带 `kind=WRITE_OUT_OF_BOUNDS`, `currentMode=ASK`, `suggestedMode=DANGER_FULL`

#### Scenario: SENSITIVE_PATH 不建议升级
- **WHEN** mode=ASK 下读取 ~/.ssh/id_rsa
- **THEN** `PathResult.denied()` SHALL 携带 `kind=SENSITIVE_PATH`, `currentMode=ASK`, `suggestedMode=null`（升级到 DANGER_FULL 也仅放行文件，仍受其他层约束）

---

### Requirement: 模型侧 marker 渲染

系统 SHALL 在前端工具结果组件（`ToolResult.tsx`）渲染 `[sandbox: file access denied under <mode> mode]` marker + 推荐升级 mode 的提示（`suggestedMode` 不为 null 时显示 "Try with danger-full mode for this session"），便于模型理解拒绝原因并按需 escalate。

#### Scenario: 模型看到 marker
- **WHEN** 工具返回 PathResult.denied(WRITE_OUT_OF_BOUNDS, ASK, DANGER_FULL, ...)
- **THEN** 前端 SHALL 在工具结果区域渲染文本 `Decision: Ask`
- **AND** SHALL 在 SSE 流中携带 denial 结构 `{ kind, currentMode, suggestedMode, marker }`

#### Scenario: 模型看到 escalation hint
- **WHEN** denial 携带 `suggestedMode=DANGER_FULL`
- **THEN** marker SHALL 包含提示 `Escalate: send POST /api/chat/{streamId}/permission with mode=danger-full to retry`
- **AND** 前端 SHALL 在工具结果下方显示一个 "升级到 danger-full" 按钮（点击调 POST /api/chat/{streamId}/permission）

---

### Requirement: escalate 同回合升级

系统 SHALL 在 `POST /api/chat/{stream_id}/permission` 请求体支持 `escalate: true` 字段；该字段为 true 时，服务端 SHALL 临时把 stream 的 mode 切换到 `suggestedMode`，保持直到该 turn 结束；turn 结束后 SHALL 恢复之前的 mode。请求响应体 SHALL 包含 `effective_mode` 字段表示当前实际生效的 mode。

#### Scenario: escalate 临时升级
- **WHEN** 客户端 POST `{"mode": "ask", "escalate": true}` 但服务端根据工具 denial 推荐 DANGER_FULL
- **THEN** 服务端 SHALL 临时把 mode 设为 DANGER_FULL
- **AND** 响应体 SHALL 包含 `{"ok": true, "mode": "ask", "effective_mode": "danger-full"}`

#### Scenario: escalate 持续到 turn 结束
- **WHEN** escalate 已生效，正在执行下一个工具调用
- **THEN** 该工具 SHALL 按 DANGER_FULL 裁决

#### Scenario: turn 结束后恢复
- **WHEN** 当前 turn 正常结束
- **THEN** stream 的 mode SHALL 恢复到 escalate 之前的值（ask）

#### Scenario: 非法 escalate 被拒
- **WHEN** 请求体 `mode` 不是 plan/ask/danger-full/dontAsk 之一
- **THEN** 服务端 SHALL 返回 400 `{"error": "invalid_mode"}`

---

### Requirement: SSE sandbox/mode 事件广播

系统 SHALL 在模式切换（包括初始设置 + escalate + turn 结束恢复）时向当前 stream 的 SSE 连接广播 `sandbox/mode` 事件，事件 payload SHALL 包含 `{ stream_id, from_mode, to_mode, reason, ts }`。`reason` 取值 `initial` / `user_set` / `escalate` / `turn_end_restore`。

#### Scenario: 初始模式广播
- **WHEN** 新 stream 创建时设置初始 mode=PLAN
- **THEN** SSE SHALL 广播 `{ from_mode: null, to_mode: "plan", reason: "initial" }`

#### Scenario: escalate 广播
- **WHEN** escalate 把 mode 从 ASK 临时升级到 DANGER_FULL
- **THEN** SSE SHALL 广播 `{ from_mode: "ask", to_mode: "danger-full", reason: "escalate" }`

#### Scenario: turn 结束恢复广播
- **WHEN** escalate 升级在 turn 结束时恢复
- **THEN** SSE SHALL 广播 `{ from_mode: "danger-full", to_mode: "ask", reason: "turn_end_restore" }`

---

### Requirement: session log 持久化模式变更

系统 SHALL 把每次 sandbox/mode 事件写入 session.jsonl，便于审计与回放；事件字段 `{ type: "sandbox/mode", stream_id, from_mode, to_mode, reason, ts }`。

#### Scenario: 模式变更落盘
- **WHEN** stream mode 从 ASK 切到 DANGER_FULL（任意 reason）
- **THEN** SessionLogger SHALL 追加一条 `sandbox/mode` 事件到当前 session.jsonl
- **AND** 该事件 SHALL 包含 from_mode/to_mode/reason/ts 四字段

---

### Requirement: settings.yaml general.permission.mode 持久化

系统 SHALL 在 `~/.agent-demo/settings.yaml` 的 `general.permission.mode` 段持久化当前用户的默认 mode；首次启动时 SHALL 用 PLAN 作为初始值；PATCH `/api/settings/general/permission/mode` SHALL 校验值在 plan/ask/danger-full/dontAsk 四档之内。

#### Scenario: 模式持久化
- **WHEN** 用户在 Settings 面板选择 danger-full 并 PATCH
- **THEN** settings.yaml SHALL 写入 `general.permission.mode: danger-full`
- **AND** 下次启动 SHALL 自动加载该值

#### Scenario: 非法值被拒
- **WHEN** PATCH `general.permission.mode` 携带值 `full_access`（旧 wire value）
- **THEN** 服务端 SHALL 返回 400，错误信息「mode 必须是 plan/ask/danger-full/dontAsk 之一」

#### Scenario: 新会话读取
- **WHEN** 新 stream 创建且未指定 permission_mode
- **THEN** 后端 SHALL 从 settings.yaml 读 `general.permission.mode`
- **AND** 用该值作为该 stream 的初始 mode

---

### Requirement: 旧 wire value 兼容迁移

系统 SHALL 在首次启动（或 settings.yaml 加载时）检测旧 wire value（`read_only` / `workspace_write` / `full_access`）并自动迁移到新四档：

| 旧值 | 新值 |
|------|------|
| `read_only` | `plan` |
| `workspace_write` | `ask` |
| `full_access` | `danger-full` |

迁移 SHALL 一次性写入 settings.yaml 并记录 INFO 日志。

#### Scenario: settings.yaml 旧值迁移
- **WHEN** 加载 settings.yaml 发现 `general.permission.mode: workspace_write`
- **THEN** 启动器 SHALL 改写为 `general.permission.mode: ask`
- **AND** SHALL 记录 INFO 日志 `migrated permission mode from 'workspace_write' to 'ask'`

#### Scenario: 旧值出现在 send body
- **WHEN** 客户端发送 `POST /api/chat/send` 携带 `permission_mode: "full_access"`
- **THEN** 服务端 SHALL 接受并 normalize 为 DANGER_FULL
- **AND** 响应体 SHALL 包含 `{"effective_mode": "danger-full"}`（告知客户端已迁移）

---

### Requirement: 前端 ChatPanel 从 settings 读取 mode

系统 SHALL 让 `ChatPanel.tsx` 不再持有独立的 `useState<PermissionMode>`；改为通过 `useSettingsStore` 读取 `general.permission.mode`，并在 send body 中透传该值；首屏加载 settings 后立即生效。

#### Scenario: ChatPanel 读取 settings
- **WHEN** 用户在 Settings 选择 danger-full
- **AND** 下次 ChatPanel 发送消息
- **THEN** send body 的 `permission_mode` 字段 SHALL 为 `"danger-full"`

#### Scenario: 首屏默认值
- **WHEN** ChatPanel 首次挂载，settings 尚未加载
- **THEN** SHALL 使用 `"plan"` 作为临时默认
- **AND** settings 加载完成后 SHALL 自动同步为 settings 中的值

---

### Requirement: Wecom 通道默认 mode

系统 SHALL 在企业微信通道（`WecomMessageDispatcher`）默认使用 DANGER_FULL（替代原 FULL_ACCESS）；理由是企业微信是受信环境下的自动化调用，无需询问用户。

#### Scenario: Wecom 默认 mode
- **WHEN** WecomMessageDispatcher 创建 session
- **AND** 未显式指定 mode
- **THEN** SHALL 使用 DANGER_FULL 作为初始 mode
- **AND** 写入 session log 时 mode 字段 SHALL 为 danger-full

---

### Requirement: SandboxPolicyService 单例注入

系统 SHALL 让 `SandboxPolicyService` 作为单例 bean 由 Spring 注入到所有需要 sandbox policy 的工具（FileTool / ShellTool / 后续 Terminal）；保证三个 capability 共享同一个 service 实例，writableRoots 不 drift。

#### Scenario: 单例保证
- **WHEN** FileTool 和 ShellTool 在同一 AgentLoop 中分别注入 SandboxPolicyService
- **THEN** 两个工具 SHALL 持有同一个 service 实例
- **AND** `service.resolve(ctx, FS)` 与 `service.resolve(ctx, BASH)` SHALL 返回同一个 `workspaceRoot` 字段

#### Scenario: 模式变更全局可见
- **WHEN** FileTool 调 `service.setMode(DANGER_FULL)` 后 ShellTool 调 `service.resolve(ctx, BASH)`
- **THEN** ShellTool 拿到的 policy SHALL mode=DANGER_FULL（同一 service 状态共享）

---

### Requirement: 默认敏感路径 patterns

系统 SHALL 在 `PermissionPathMatcher` 默认包含 4 个 pattern（`**/.ssh/**`、`**/.env*`、`**/*.pem`、`**/*credentials*`），可在 `settings.yaml` 的 `general.permission.sensitivePatterns` 段扩展（追加，不替换默认）。

#### Scenario: 默认 patterns 生效
- **WHEN** 任意 mode（除 DANGER_FULL）下工具目标路径命中 `**/.ssh/**`
- **THEN** SHALL 触发敏感路径询问

#### Scenario: 用户扩展
- **WHEN** settings.yaml 含 `general.permission.sensitivePatterns: ["**/secret.txt"]`
- **THEN** 默认 4 个 + 该 1 个共 5 个 pattern 生效

---

### Requirement: 模式持久化粒度（session 级 vs 全局）

系统 SHALL 把 mode 的持久化拆为两层：(1) 全局默认（settings.yaml `general.permission.mode`，影响新会话）；(2) session 覆盖（当前 stream 的 in-memory mode，escalate/turn_end_restore 影响当前会话）。session 级 SHALL 不写回 settings.yaml。

#### Scenario: session 级不持久化
- **WHEN** escalate 把 stream mode 临时改为 DANGER_FULL
- **THEN** settings.yaml SHALL 保持原值不变

#### Scenario: 用户手动改 settings 才持久化
- **WHEN** 用户在 Settings 面板选择 danger-full 并 PATCH
- **THEN** settings.yaml SHALL 更新为 danger-full
- **AND** 当前活动 stream SHALL 立即应用新值（下次工具调用生效）

---

### Requirement: DenialKind 与 escalation 推荐映射

系统 SHALL 在 `FsDenialKind` 决定 `suggestedMode` 时按下表：

| kind | suggestedMode |
|------|---------------|
| READ_OUT_OF_BOUNDS | null（reading /tmp 之外也仅 ASK 询问，不建议升级） |
| WRITE_OUT_OF_BOUNDS | DANGER_FULL（升级后可写任意路径） |
| SENSITIVE_PATH | null（升级后仍可能受其他层约束） |
| TOOL_DENY | null（工具级 DENY 与 mode 无关） |
| MODE_REJECTED | ASK（被 mode 本身拒绝，升级到 ASK 即可） |

#### Scenario: WRITE_OUT_OF_BOUNDS 推荐升级
- **WHEN** mode=ASK 下写入 /etc/passwd 触发 WRITE_OUT_OF_BOUNDS
- **THEN** denial SHALL 携带 `suggestedMode=DANGER_FULL`

#### Scenario: SENSITIVE_PATH 不推荐升级
- **WHEN** mode=ASK 下读取 ~/.ssh/id_rsa 触发 SENSITIVE_PATH
- **THEN** denial SHALL 携带 `suggestedMode=null`

