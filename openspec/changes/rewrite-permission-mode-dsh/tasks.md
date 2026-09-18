# rewrite-permission-mode-dsh — Tasks

## 1. 基础抽象层（5 个 task）

- [ ] 1.1 新建 `SandboxMode` 枚举（PLAN / ASK / DANGER_FULL / DONT_ASK）+ `wireValue()` + `from(String)` + `DEFAULT = PLAN`
- [ ] 1.2 新建 `Capability` 枚举（FS / BASH / TERMINAL）+ `SandboxPolicy` record（mode + workspaceRoot + tempRoots + capability）
- [ ] 1.3 新建 `FsDenialKind` 枚举（READ_OUT_OF_BOUNDS / WRITE_OUT_OF_BOUNDS / SENSITIVE_PATH / TOOL_DENY / MODE_REJECTED）
- [ ] 1.4 新建 `WritableRoots` 工具类（单一 `writableRoots(SandboxPolicy)` 函数 + `canonicalize(Path)` 私有方法 + IOException 回退原拼写）
- [ ] 1.5 新建 `SandboxPolicyService`（@Component 单例 + `AtomicReference<SandboxMode> mode` + `Path workspaceRoot` + `resolve(ctx, capability): SandboxPolicy` + `setMode` + `escalate` + `restoreOnTurnEnd` + `createForTest()` 工厂）

## 2. TDD 验证基础抽象

- [ ] 2.1 `SandboxModeTest`：4 档枚举值、`wireValue()` 往返、`from()` 非法值抛错、DEFAULT
- [ ] 2.2 `SandboxPolicyTest`：record 字段访问、capability 误用抛错
- [ ] 2.3 `WritableRootsTest`：PLAN/ASK/DANGER_FULL 空列表、DONT_ASK 三个根 canonicalize 去重、IOException 回退
- [ ] 2.4 `SandboxPolicyServiceTest`：per-call resolve、capability null 抛错、workingDirectory null 抛错、单例保证、setMode 立即生效、escalate/restoreOnTurnEnd 配对

## 3. FileTool 集成（3 个 task）

- [ ] 3.1 改造 `AbstractFileTool.resolve`：根列表从 `SandboxPolicyService.resolve(ctx, FS).writableRoots()` 取，删除原硬编码 2 根
- [ ] 3.2 在 `AbstractFileTool.writeFile` / `editFile` 加 TOCTOU re-canonicalize：调用 `target.toRealPath()` 重新解析，捕获 symlink swap
- [ ] 3.3 改造 `PathResult.ok(...)` 扩展 `PathResult.denied(FsDenialKind, currentMode, suggestedMode, message)`，拒绝时返回结构化 denial

## 3.5 FileTool 乐观 CAS 写入（Q3 决策，1 个 task）

- [ ] 3.5.1 `AbstractFileTool` 新增 `writeWithCas(target, content)`：tmp 文件 + `Files.move(ATOMIC_MOVE)` 两阶段；写之前 re-canonicalize；冲突时 retry 一次（最多 1 次）；跨 fs 退化为非原子 move

## 4. FileTool TDD 与场景覆盖

- [ ] 4.1 `AbstractFileToolTest`：workspace 内 write 放行、workspace 外 write 拒绝、sensitive path 拒绝、TOCTOU symlink swap 拒绝、乐观 CAS 冲突 retry
- [ ] 4.2 `AbstractFileToolTest`：DANGER_FULL 下任意路径允许、DONT_ASK 下 workspace + temp 允许但其他拒绝

## 5. PermissionManager 拆分（3 个 task）

- [ ] 5.1 拆 `PermissionManager` 为 `SandboxPolicyService` + `SensitivePathMatcher`（抽离自 PermissionPathMatcher）+ `DecisionRecorder`（会话日志 sink）
- [ ] 5.2 `PermissionManager` 保留 wrapper：构造注入上述 3 个；`decide()` 委托给 SandboxPolicyService.defaultDecision + SensitivePathMatcher 命中升级 ask
- [ ] 5.3 改造 `AgentLoop`：调用顺序改为 SandboxPolicyService → Tool.checkPermissions → 后者 DENY 直接拒绝

## 6. PermissionManager TDD 迁移

- [ ] 6.1 跑全量 `PermissionManagerTest`，迁移失败 case 到 `SandboxPolicyServiceTest` / `SensitivePathMatcherTest`
- [ ] 6.2 `PermissionManagerTest`：保留 wrapper 测试，确认对外行为不变（READ/WRITE/SHELL/OTHER × READ_ONLY/WORKSPACE_WRITE/FULL_ACCESS × workspace 内/外 × sensitive）

## 7. API 改造（4 个 task）

- [ ] 7.1 改造 `ChatController`：`POST /api/chat/{streamId}/permission` 支持 `escalate: bool` 字段 + 返回 `effective_mode`
- [ ] 7.2 改造 `ChatController`：`POST /api/chat/send` 的 `permission_mode` 字段接受新 wire value + 旧值自动 normalize
- [ ] 7.3 新增 `PermissionDenialResponse` DTO：`{ kind, currentMode, suggestedMode, marker }`
- [ ] 7.4 新增 `SessionOwnerRegistry`（Q2 决策）：`session_id -> owner_ip` 映射；`send` 时注册、`POST /api/chat/{streamId}/permission` 时校验同 IP；不匹配返回 403

## 8. SSE 事件与持久化（4 个 task）

- [ ] 8.1 在 `ChatStreamService` 新增 SSE `sandbox/mode` 事件广播：`{ stream_id, from_mode, to_mode, reason, ts }`，reason 取值 `initial` / `user_set` / `escalate` / `turn_end_restore`
- [ ] 8.2 在 `SessionLogger` 新增 `sandbox/mode` 事件持久化（追加到 session.jsonl）
- [ ] 8.3 AgentLoop turn 结束时调 `SandboxPolicyService.restoreOnTurnEnd(streamId)` + 广播 SSE 事件
- [ ] 8.4 新增 `SandboxPolicyReplayService`（Q4 决策）：启动钩子扫未结束 session.jsonl，重放最后一个 escalate；恢复 to_mode

## 9. API TDD 与场景覆盖

- [ ] 9.1 `ChatControllerTest`：escalate=true 临时升级 + turn 结束恢复 + effective_mode 字段
- [ ] 9.2 `ChatControllerTest`：permission_mode 新值（plan/ask/danger-full/dontAsk）通过 + 旧值（read_only/workspace_write/full_access）normalize
- [ ] 9.3 `ChatStreamServiceTest`：SSE sandbox/mode 事件广播在 mode 变化时触发
- [ ] 9.4 `SessionLoggerTest`：sandbox/mode 事件落盘字段完整
- [ ] 9.5 `SessionOwnerRegistryTest`：同 IP + owner 校验通过；异 IP 返回 403
- [ ] 9.6 `SandboxPolicyReplayServiceTest`：session.jsonl 含 escalate 时启动重放 to_mode；含 turn_end_restore 时不重放

## 10. 前端改造（3 个 task）

- [ ] 10.1 `PermissionModeSelect.tsx`：保持 4 档 dsh 命名 + 接受 sandbox-policy 默认值（`"plan"` 替代 `"ask"`）
- [ ] 10.2 `ChatPanel.tsx`：删除 `useState<PermissionMode>("read_only")`，改从 `useSettingsStore((s) => (s.snapshot?.general?.permission as { mode?: string })?.mode ?? "plan")` 读
- [ ] 10.3 `ToolResult.tsx`：渲染 denial marker `[sandbox: file access denied under <mode> mode]` + suggestedMode 非 null 时显示 "升级到 danger-full" 按钮（点击调 POST /api/chat/{streamId}/permission with escalate=true）

## 11. 前端 TDD 与场景覆盖

- [ ] 11.1 `PermissionModeSelect.test.tsx`：4 档选项渲染 + PATCH 触发
- [ ] 11.2 `ChatPanel.test.tsx`：从 settings 读 mode + send body 透传该值
- [ ] 11.3 `ToolResult.test.tsx`：denial marker 渲染 + 升级按钮触发

## 12. 兼容迁移（2 个 task）

- [ ] 12.1 `SettingsLoader`：加旧 wire value 迁移（`read_only` → `plan`、`workspace_write` → `ask`、`full_access` → `danger-full`） + INFO 日志 + 写回 YAML
- [ ] 12.2 `WecomMessageDispatcher`：把 `PermissionMode.FULL_ACCESS` 改为 `SandboxMode.DANGER_FULL`

## 13. 文档与 README

- [ ] 13.1 `README.md`：删除原 §3.x 权限模式章节（3 档老命名），新增 §"权限模式与 Sandbox Policy"（4 档 dsh 命名 + sandbox-policy 服务 + writableRoots + TOCTOU + escalate 流程图）
- [ ] 13.2 `README.md`：在 §"Known Limitations" 段加 bash 内核隔离未实现（denylist 兜底，留 follow-up）

## 14. spec archive（按 §2.5 / §2.7.5）

- [ ] 14.1 全量 `mvn -o -pl agent-core,agent-web verify` 全绿（含新增 SandboxPolicy 系列测试 + 迁移后 PermissionManager 测试）
- [ ] 14.2 全量 `npx vitest run` 全绿（含前端 PermissionModeSelect / ChatPanel / ToolResult 改造）
- [ ] 14.3 `npx tsc --noEmit` 错误数不超过基线（7）
- [ ] 14.4 同步 main：`git fetch origin main && git merge origin/main`（若有冲突解决冲突后回到任务 14.1 重跑门禁）
- [ ] 14.5 `openspec archive rewrite-permission-mode-dsh`：把 `specs/sandbox-policy/spec.md` 内容合并到 `openspec/specs/sandbox-policy/spec.md`；把 `specs/permission-mode/spec.md` 的 REMOVED 段处理；把 `specs/settings/spec.md` 的 REMOVED 段处理
- [ ] 14.6 合并回 main：`git checkout main && git merge feat/rewrite-permission-mode-dsh --no-ff`
- [ ] 14.7 main 复验：`mvn -o -pl agent-core,agent-web verify` + `npx vitest run` 全绿
- [ ] 14.8 push main：`git push origin main`
- [ ] 14.9 清理：删除 `feat/rewrite-permission-mode-dsh` 分支（local + remote），删除 `.worktrees/rewrite-permission-mode-dsh`
