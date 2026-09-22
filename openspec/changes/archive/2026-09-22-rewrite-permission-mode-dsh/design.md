# rewrite-permission-mode-dsh — Design

## Context

agent-demo 当前权限体系存在三层错位：

1. **命名不一致**：后端 `PermissionMode` 枚举（READ_ONLY / WORKSPACE_WRITE / FULL_ACCESS）与前端 `PermissionModeSelect`（plan / ask / danger-full / dontAsk）使用两套命名；settings spec 写 4 档 dsh 命名，permission-mode spec 写 3 档老命名；Settings 改 mode 不影响实际对话（ChatPanel 持有独立 `useState`）。
2. **跨能力不一致**：`AbstractFileTool.resolve` 硬编码 2 个根（cwd + agentDataDir），与 `PermissionManager`（mode × ToolCategory 决策表）完全独立；`ShellAdapter` + `DefaultDenylistMatcher` 又独立第三层。三者各自决策路径边界，没有共享 policy 服务。
3. **per-call 解析粒度缺失**：`ToolContext` 在 AgentLoop 构造时一次性传入 mode + cwd，工具调用期间 mode 变化要重新构造 AgentLoop；dsh 每个 tool call 通过 `ctx.sandboxPolicy.resolve()` 重新解析，可响应 sandbox/mode 事件。

dsh web 的 SandboxPolicy 设计（`packages/sandbox/sandbox-policy/` + `packages/fs/fs-sandbox/` + `packages/sandbox/sandbox/src/roots.ts`）解决了上述三点。本 change 把这套抽象移植到 agent-demo，对齐命名、引入跨能力一致性、加固边界（TOCTOU + 结构化拒绝反馈 + escalate）。

## Goals / Non-Goals

**Goals**

- 统一后端枚举、wire value、前端类型、settings.yaml 路径到 4 档 dsh 命名（plan / ask / danger-full / dontAsk）。
- 引入 `SandboxPolicyService` 单例 bean，统一持有 mode + workspaceRoot + tempRoots；fs / bash / terminal 三能力共享 writableRoots。
- 引入 per-call policy 解析（每次 tool 调用 `resolve(ctx, capability)`），支持运行时 mode 切换无需重建 AgentLoop。
- 实现 TOCTOU 防护（写之前 `Path.toRealPath()` 重新解析，捕获 symlink swap）。
- 实现结构化拒绝反馈（`FsDenialKind` + `PathResult.denied(...)` + 模型侧 marker 渲染）。
- 实现 escalate 同回合升级（denial 推荐 → 前端按钮 → 后端临时切 mode → turn 结束恢复）。
- 实现 SSE `sandbox/mode` 事件广播 + session log 持久化，便于审计与回放。
- 旧 wire value（read_only / workspace_write / full_access）自动迁移到新四档。
- ChatPanel 改为从 `useSettingsStore` 读 mode，移除独立 `useState`。

**Non-Goals**

- 不实现 bash 内核隔离（Seatbelt / bwrap / Landlock / Windows ACL）；`ShellAdapter` + `DefaultDenylistMatcher` 保留，留 follow-up。
- 不实现 embedding / sandbox 自动决策；本 change 仅做 access control 抽象，不引入 AI 决策。
- 不重写 `PermissionPathMatcher`；保留 Ant-style glob 语义，仅迁移调用方。
- 不改 Wecom 通道以外的其他 channel；其他 channel 默认 mode 沿用现有行为（不强制 DANGER_FULL）。
- 不改 session.jsonl 之外的日志格式；sandbox/mode 事件作为新事件类型追加。
- 不引入 cross-platform JNI；TOCTOU 通过 JDK 自带 `Path.toRealPath()` 实现，承认非 0 残余风险（与 dsh 同 trade-off）。

## Decisions

### Decision 1：SandboxPolicyService 作为 Spring 单例 bean

**选型**：用 Spring `@Component` 注入；不引入新 DI 框架。

**理由**：agent-core + agent-web 已用 Spring Boot 3.2，`@Component` + 构造注入是统一模式；不引入新的 holder 抽象。

**备选**：

- A. 静态 holder：`public class SandboxPolicyService { private static final INSTANCE = ... }`；引入隐藏依赖，难测试。
- B. Per-tool 实例：每个 tool 构造时 `new SandboxPolicyService()`；导致 fs / bash 拿到不同实例，writableRoots drift（违反 dsh one home 原则）。
- C. ✅ Spring 单例：与现有架构一致；测试时可手动 `new SandboxPolicyService()` 绕过 Spring。

**实现**：`agent-core/.../permission/SandboxPolicyService.java` 加 `@Component`；构造器可注入（无依赖）；持有 `AtomicReference<SandboxMode> mode`、`Path workspaceRoot`、`Capability capability`（resolve 时入参覆盖）。

---

### Decision 2：writableRoots 用 `Path.toRealPath()` 实现

**选型**：用 JDK 自带 `Path.toRealPath(LinkOption.NOFOLLOW_LINKS)`，不引入第三方 native 库。

**理由**：

- `toRealPath()` 是 JDK 标准 API；跨平台，无需 JNI。
- 行为对齐 dsh 的 `realpathSync.native`（dsh 注释：`the native implementation follows the filesystem's component-by-component lookup, matching chdir/spawn`）；Java 的 `toRealPath()` 行为一致。
- `NOFOLLOW_LINKS` 选项在路径本身是 symlink 时不跟随（避免解析到 target），符合 canonicalize 语义。

**备选**：

- A. JNI / JNA 调 `realpath(3)`：与 dsh 行为最接近，但需要 native 库，部署复杂。
- B. `Path.normalize()` + `getName()`：仅做词法规范化，不解析 symlink，无法防护 symlink swap。
- C. ✅ `Path.toRealPath()`：JDK 内置，跨平台，行为正确。

**实现**：

```java
public static List<Path> writableRoots(SandboxPolicy policy) {
  if (policy.mode() == SandboxMode.PLAN || policy.mode() == SandboxMode.ASK) {
    return List.of();
  }
  if (policy.mode() == SandboxMode.DANGER_FULL) {
    return List.of();  // 调用方据此判断不 fence
  }
  // DONT_ASK: workspace + /tmp + tmpdir
  Path cwd = canonicalize(policy.workspaceRoot());
  Path tmpUnix = canonicalize(Path.of("/tmp"));
  Path tmpUser = canonicalize(Path.of(System.getProperty("java.io.tmpdir")));
  return List.of(cwd, tmpUnix, tmpUser).stream().distinct().toList();
}

private static Path canonicalize(Path p) {
  try { return p.toRealPath(); }
  catch (IOException e) { return p; }  // 路径不存在时回退原拼写
}
```

---

### Decision 3：FsDenialKind 枚举作为拒绝原因分类

**选型**：在 `agent-core/.../permission/FsDenialKind.java` 定义枚举，承载拒绝原因分类。

**理由**：dsh 用 `FsError(code: 'FS_SANDBOX_DENIED')` 单一 code，不细分；agent-demo 选择细分（5 种 kind）便于：

- 前端渲染不同 marker / 不同升级按钮
- 拒绝统计 / session log 审计（哪种拒绝最多）
- `suggestedMode` 决策表（每种 kind 对应不同建议）

**备选**：

- A. 单一 `SANDBOX_DENIED` code：简单但前端无法区分。
- B. ✅ 细分枚举：5 种 kind 对应决策表（见 sandbox-policy §"DenialKind 与 escalation 推荐映射"）。

---

### Decision 4：escalate 状态用 in-memory Map 管理

**选型**：在 `SandboxPolicyService` 持有 `ConcurrentHashMap<String streamId, SandboxMode originalMode>`；`escalate(streamId, targetMode)` 时记录原 mode，`restoreOnTurnEnd(streamId)` 时恢复。

**理由**：

- agent-web 是 Spring Boot，进程内 in-memory 状态足够；不持久化（escalate 是 turn-scoped，进程重启即丢失，符合预期）。
- `ConcurrentHashMap` 线程安全；AgentLoop 与 SSE handler 可能并发触发。

**备选**：

- A. 持久化到 session.jsonl：escalate 跨进程恢复；过于复杂，与 turn-scoped 语义不符。
- B. ✅ in-memory Map：简单、与 turn 生命周期对齐。
- C. ThreadLocal：粒度太细，跨线程恢复困难。

---

### Decision 5：TOCTOU 实现位置在 AbstractFileTool 子类

**选型**：在 `AbstractFileTool.writeFile` / `editFile` 等写入方法里，调用底层 `Files.write` / `Files.move` 之前加 `target.toRealPath()` 检查。

**理由**：

- AbstractFileTool 是所有写文件工具的基类（WriteFile / EditFile），一处改动覆盖所有写工具。
- 比在 `resolve` 里做更合理：`resolve` 仅做路径合法化判断；`toRealPath()` 是 fs 操作前置检查，与"写之前"语义对齐。

**备选**：

- A. 在 `resolve` 里做：写之前与 resolve 之间仍有时间窗口。
- B. ✅ 在 writeFile / editFile 子类方法里：紧贴 fs 调用，窗口最小。
- C. 全局文件系统层拦截：需重写 `FileSystem` 抽象，过度工程。

**实现**（伪代码）：

```java
// AbstractFileTool.writeFile 子类调用模式
@Override
public ToolResult writeFile(Input in, ToolContext ctx) {
  Path target = resolve(in, ctx).path();
  PathResult checked = checkedTarget(target, ctx);  // 含 TOCTOU re-canonicalize
  if (checked.denied()) return checked.toToolResult();
  Files.writeString(checked.path(), in.content());
  return ToolResult.ok(...);
}
```

---

### Decision 6：旧 wire value 迁移在 SettingsLoader

**选型**：在 `agent-web/.../config/SettingsLoader` 加载 YAML 后、写回之前，做一次 normalize；记录 INFO 日志。

**理由**：

- SettingsLoader 是 settings.yaml 的唯一入口；一处改动覆盖所有读 settings 的路径。
- 一次性迁移，写回时已 normalize；下次启动不再触发。

**备选**：

- A. 在 PermissionManager 构造时迁移：只覆盖 mode，不覆盖 YAML。
- B. ✅ 在 SettingsLoader：覆盖 YAML，永久迁移。

**实现**：

```java
private static final Map<String, String> OLD_TO_NEW = Map.of(
  "read_only", "plan",
  "workspace_write", "ask",
  "full_access", "danger-full"
);

if (mode != null && OLD_TO_NEW.containsKey(mode)) {
  String newMode = OLD_TO_NEW.get(mode);
  log.info("migrated permission mode from '{}' to '{}'", mode, newMode);
  mode = newMode;
  // 写回 YAML
}
```

---

### Decision 7：SSE sandbox/mode 事件 schema

**选型**：复用现有 SSE 事件通道（在 `ChatStreamService` 已有 `permission_request` 事件）；新增 `sandbox/mode` 事件类型。

**理由**：避免新建 SSE 通道；与现有 permission 流对齐。

**Payload schema**：

```json
{
  "type": "sandbox/mode",
  "stream_id": "abc-123",
  "from_mode": "ask",
  "to_mode": "danger-full",
  "reason": "escalate",
  "ts": "2026-09-13T14:30:00Z"
}
```

`reason` 取值：`initial` / `user_set` / `escalate` / `turn_end_restore`。

---

### Decision 8：denial response 结构

**选型**：在 `ToolResult.error(...)` 现有结构上扩展；新增 `denial: { kind, currentMode, suggestedMode, marker }` 字段。

**Payload schema**：

```json
{
  "ok": false,
  "error": "path_denied",
  "denial": {
    "kind": "WRITE_OUT_OF_BOUNDS",
    "currentMode": "ask",
    "suggestedMode": "danger-full",
    "marker": "[sandbox: file access denied under ask mode] Escalate: POST /api/chat/{streamId}/permission with mode=danger-full"
  }
}
```

前端从 `denial.marker` 渲染；`denial.suggestedMode` 非 null 时显示升级按钮。

---

### Decision 9：前端 ChatPanel 改造

**选型**：`ChatPanel.tsx` 删除 `useState<PermissionMode>("read_only")`；改用 `useSettingsStore((s) => (s.snapshot?.general?.permission as { mode?: string })?.mode ?? "plan")`。

**理由**：

- settings.yaml 是 mode 的 single source of truth（除 session escalate）。
- 删除独立 state 避免两端不同步。

**首屏默认值**：`"plan"`（与后端 `SandboxMode.DEFAULT` 一致）；settings 加载完成后自动同步。

---

### Decision 10：Wecom 默认 mode

**选型**：`WecomMessageDispatcher` 把 `PermissionMode.FULL_ACCESS` 改为 `SandboxMode.DANGER_FULL`。

**理由**：Wecom 是受信环境自动化通道，无需询问；danger-full 是新枚举对应值。

---

## Risks / Trade-offs

### [Risk] TOCTOU 残余风险
- **风险**：写之前 `toRealPath()` 与底层 syscall 之间仍有窗口（攻击者可 swap symlink）。
- **缓解**：与 dsh 同 trade-off（承认非 0 残余）；agent-demo 是单进程 CLI/Web，攻击面小于 dsh；future 可加 `openat2` 类原语。

### [Risk] 旧 wire value 迁移误改用户配置
- **风险**：启动时自动改写 settings.yaml 可能让用户困惑（"为什么我的配置变了？"）。
- **缓解**：INFO 日志记录迁移内容；README 文档说明。

### [Risk] SandboxPolicyService 单例 vs 多实例
- **风险**：测试时若手动 `new SandboxPolicyService()` 而非走 Spring，可能拿到不同实例。
- **缓解**：测试统一用 Spring 上下文；提供 `SandboxPolicyService.createForTest()` 工厂方法。

### [Risk] escalate 跨 turn 泄漏
- **风险**：turn 异常结束（网络断 / 进程崩）时 escalate 状态可能未恢复。
- **缓解**：escalate 状态 in-memory，进程崩即丢；下次启动仍是默认 mode；WebSocket 重连后从 settings 读默认 mode。

### [Risk] bash 隔离缺失
- **风险**：本 change 不实现 bash 内核隔离，shell 工具仍依赖 denylist。
- **缓解**：明确写入 spec 与 README "Known Limitations"；留 follow-up change ID。

### [Risk] FS_BASH_TERMINAL 三 capability 抽象可能在过度工程
- **风险**：agent-demo 当前只有 fs + bash 两类工具；引入 terminal capability 是为未来扩展。
- **缓解**：terminal capability 暂不挂任何工具；spec 描述行为，code 端 `Capability` 枚举保留 TERMINAL 值。

### [Risk] 与现有 PermissionManager 测试的兼容性
- **风险**：PermissionManager 拆分后，原 PermissionManagerTest 部分 case 要改（部分行为迁移到 SandboxPolicyServiceTest）。
- **缓解**：archive 时同步更新；保留 PermissionManager 的 wrapper 模式（委托给 SandboxPolicyService + SensitivePathMatcher）让部分测试继续通过。

---

## Migration Plan

### 阶段 0：建分支（§2.7）

`git worktree add .worktrees/rewrite-permission-mode-dsh -b feat/rewrite-permission-mode-dsh`

### 阶段 1：基础抽象（任务 T1-T5）

1. 新增 `SandboxMode` 枚举（4 档）+ `SandboxPolicy` record + `Capability` 枚举 + `FsDenialKind` 枚举
2. 新增 `WritableRoots` 工具类（单一函数 + canonicalize）
3. 新增 `SandboxPolicyService`（@Component 单例 + per-call resolve）
4. 新增 `PermissionDecision` 扩展（携带 suggestedMode 字段）
5. TDD：单测覆盖上述 5 个新类

### 阶段 2：FileTool 集成（任务 T6-T8）

1. 改造 `AbstractFileTool.resolve` 调用 SandboxPolicyService 而非硬编码 2 根
2. 在子类 `writeFile` / `editFile` 加 TOCTOU re-canonicalize
3. 把 `PathResult.ok(...)` 改为支持 `.denied(FsDenialKind, currentMode, suggestedMode, message)`

### 阶段 3：PermissionManager 拆分（任务 T9-T10）

1. 拆 PermissionManager 为 SandboxPolicyService + SensitivePathMatcher + DecisionRecorder（保留 wrapper）
2. 全量 PermissionManagerTest 跑通，迁移失败的 case 移到 SandboxPolicyServiceTest

### 阶段 4：API 与事件（任务 T11-T13）

1. 改造 `POST /api/chat/{streamId}/permission`：支持 `escalate: bool` 字段 + 返回 `effective_mode`
2. 改造 `POST /api/chat/send`：支持 `permission_mode` 新 wire value + 旧值自动迁移
3. 新增 SSE `sandbox/mode` 事件广播 + SessionLogger 持久化

### 阶段 5：前端改造（任务 T14-T15）

1. `PermissionModeSelect.tsx` 保持 4 档 dsh 命名 + 接受 sandbox-policy 的默认值
2. `ChatPanel.tsx` 删除 `useState`，改从 `useSettingsStore` 读
3. `ToolResult.tsx` 渲染 denial marker + 升级按钮

### 阶段 6：兼容迁移（任务 T16）

1. `SettingsLoader` 加旧 wire value 迁移（read_only → plan 等）
2. README 文档说明

### 阶段 7：spec archive（§2.5）

1. `mvn -o -pl agent-core,agent-web verify` 全绿
2. `openspec archive rewrite-permission-mode-dsh` 把 delta 合并到 main spec
3. 合并回 main（§2.7.5）+ 复验 + push

### 回退策略

若任一阶段测试不过，回退到上一阶段起点。archive 前不 push main。若 archive 后 main 复验失败，按 §2.7.5.4 `git revert -m 1 <merge-commit>` 回退。

---

## Open Questions（已决）

1. **`/api/settings/general/permission/mode` 允许 `dontAsk`**：✅ 允许；README 加警告「不要在多人共享电脑上用 dontAsk」。
2. **`/api/chat/{streamId}/permission` 权限校验**：✅ **校验「同 IP + 同一会话所有者」**；新加 `SessionOwnerRegistry`（session_id → owner_ip 映射），mode 切换时校验调用方 IP 与 owner 一致；不匹配返回 403。
3. **TOCTOU re-canonicalize 失败时**：✅ **改为乐观 CAS + 冲突重试**；写入路径使用 `Files.write(tmp, content) + Files.move(tmp, target, ATOMIC_MOVE)` 两阶段；写之前 re-canonicalize 一次；冲突时回退一次 re-canonicalize + 重写一次（最多 1 次 retry）。
4. **崩溃恢复 mode**：✅ **启动时重放过期 turn**；AgentLoop 在 turn 开始时记录 `sandbox/mode` 事件（含 escalate reason）；进程重启时从 session.jsonl 读最后未结束 turn 的 sandbox/mode 序列，重放 to_mode；新 turn 开始时按 settings.yaml 默认值。
5. **`general.permission.escalatable` 配置项默认值**：✅ `true` 默认；Settings 提供开关；用户在 Settings 关掉后必须手动改 mode 才能越权。

### Q2 实现细节

**SessionOwnerRegistry**（新增类 `agent-web/.../api/SessionOwnerRegistry.java`）：

```java
@Component
public class SessionOwnerRegistry {
  private final Map<String, String> sessionOwners = new ConcurrentHashMap<>();  // session_id -> owner_ip

  public void register(String sessionId, String ip) { sessionOwners.put(sessionId, ip); }
  public Optional<String> ownerOf(String sessionId) { return Optional.ofNullable(sessionOwners.get(sessionId)); }
  public boolean verify(String sessionId, String ip) { return ip.equals(sessionOwners.get(sessionId)); }
}
```

在 `ChatController.send` 时注册：`registry.register(sessionId, request.getRemoteAddr())`；在 `POST /api/chat/{streamId}/permission` 时校验：`if (!registry.verify(streamId, request.getRemoteAddr())) return 403`。

### Q3 实现细节

**乐观 CAS 写入**（修改 `AbstractFileTool.writeFile`）：

```java
private PathResult writeWithCas(Path target, String content) {
  // 1. 写之前 re-canonicalize（TOCTOU 防护）
  Path fresh = target.toRealPath();  // IOException → 回退原路径 + WARN
  if (!writableRoots.contains(fresh)) return denied(WRITE_OUT_OF_BOUNDS, ...);

  // 2. 乐观 CAS：写到 tmp 文件 + 原子 move
  Path tmp = Files.createTempFile(target.getParent(), ".tmp-", ".cas");
  try {
    Files.writeString(tmp, content);
    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    return ok(target);
  } catch (AtomicMoveNotSupportedException e) {
    // 跨 fs 退化为非原子 move
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    return ok(target);
  } catch (AccessDeniedException e) {
    // 冲突：retry 一次（最多 1 次）
    Path reFresh = target.toRealPath();
    if (!reFresh.equals(fresh)) {
      // 路径被换，重新判定
      return writeWithCas(target, content);  // 1 次 retry
    }
    return denied(WRITE_OUT_OF_BOUNDS, ...);
  } finally {
    Files.deleteIfExists(tmp);
  }
}
```

### Q4 实现细节

**启动时 turn 重放**（新增 `SandboxPolicyReplayService`）：

```java
@Component
public class SandboxPolicyReplayService {
  public void replayOnStartup(String sessionId) {
    List<SessionLogEvent> events = sessionLogger.readEvents(sessionId, type="sandbox/mode");
    // 找最后一个未结束的 escalate（reason=escalate 且后续没有 turn_end_restore）
    int idx = events.size() - 1;
    while (idx >= 0) {
      var e = events.get(idx);
      if (e.reason().equals("escalate")) {
        // 重放：恢复 to_mode
        sandboxPolicyService.setMode(SandboxMode.from(e.toMode()));
        return;
      }
      if (e.reason().equals("turn_end_restore")) return;  // 正常结束，无需重放
      idx--;
    }
  }
}
```

启动钩子：`ApplicationReadyEvent` 后扫所有未结束 session.jsonl，调 `replayOnStartup`。

---

## 决策表（最终）

| Q | 决策 | 工作量 | 备注 |
|---|------|--------|------|
| Q1 | 允许 dontAsk + README 警告 | 0 | design 已说 |
| Q2 | SessionOwnerRegistry 同 IP + owner 校验 | +1d | 需新增类 + 测试 + IP 注入 |
| Q3 | 乐观 CAS + 1 次 retry | +1d | writeFile/editFile 改造 |
| Q4 | 启动时从 session.jsonl 重放未结束 escalate | +0.5d | SessionLogEvent 读 API + replay 钩子 |
| Q5 | escalatable 默认 true | 0 | design 已说 |
| **合计** | | **+2.5d** | 原 L3 7-10d → 实际 10-13d |
