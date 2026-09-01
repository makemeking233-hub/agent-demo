## Context

agent-web 的权限交互目前是**每工具调用逐次确认**：`AgentLoop` 在构造时 `new PermissionManager()`（基于 `PermissionPolicy.defaults()`：read=allow / write=ask / shell=ask + 敏感路径 glob），`ChatStreamService` 把该 `PermissionManager` 的 `ask` 结果经 `PermissionBridge` 转成 `permission_request` SSE，前端点 yes/no/always 后再 `submitDecision` 唤醒。

现状的问题：只读、工作区写盘这类**高频且低风险**的操作每次都要弹窗，且运行期无法切换全局策略（策略在会话创建时固定）。用户希望像 DeepSeek Harness 一样，在输入区提供一个**权限模式下拉**（Read Only / Workspace Write / Full access），提前设定后由后端按模式自动裁决；非放行类别仍保留弹窗兜底。

约束：
- JDK 17 / Spring Boot 3.2 / Maven 3.9，不引 Larombok 等（沿用 AGENTS.md §3 关键决策）。
- Fail-Closed 基调：工具级 `DENY`（`isDestructive` / shell 黑名单）是终态，模式放行 SHALL NOT 覆盖它。
- `AgentLoop` 已有 `setModel` 的 volatile 运行时切换范式可复用。
- CLI（`ChatCommand`）权限行为保持现状，本 change 不改 CLI。
- 前端源码在 `agent-web/frontend/src/`，构建产物在 `agent-web/src/main/resources/static/`。

## Goals / Non-Goals

**Goals:**
- 新增 `PermissionMode`（`read_only` / `workspace_write` / `full_access`）作为单点权限基准，缺省 `read_only`。
- `PermissionManager` 会话内可重设模式；`workspace_write` 按会话工作目录判定写盘边界（内→allow，外→ask）。
- 敏感路径在非 `full_access` 下强制 ask；`full_access` 放行（仅工具级 DENY 兜底）。
- 提供 `POST /api/chat/{stream_id}/permission` 实时切换；`POST /api/chat/send` 支持可选初始 `permission_mode`。
- 前端 composer 状态栏加权限下拉，切换即调后端。

**Non-Goals:**
- 不做模式**持久化**（仅会话内生效；刷新/新会话重置为 `read_only`）。
- 不改 CLI（`ChatCommand`）权限确认行为。
- 不新增工具类别；`OTHER` 在非 `full_access` 下保持 ask。
- 不改 `web-ui` 的 `permission_request` SSE 事件契约。

## Decisions

### D1: 模式裁决逻辑放 core（`PermissionMode`），不做前端复制
模式→策略映射与工作区边界判定统一收敛到 `agent-core` 的 `permission` 包，前端仅负责把用户选择透传给后端。

> 备选：前端按模式自动应答 `permission_request`（薄改动）。
> 否决理由：无真强制（伪造客户端可绕过）、每工具调用仍走一遍 SSE 往返、模式逻辑分流到前端三处。

### D2: `PermissionManager` 变为「模式感知 + 工作区感知」
- 新增 `PermissionMode` 枚举：字段 + `PermissionMode.from(String)`（非法输入返回 Optional/抛参）+ 每模式对工具类别的 allow/ask 判定。
- `PermissionManager` 增加 `setMode(PermissionMode)`、`setWorkingDirectory(Path)`，并把 `policy` 的 `final` 去掉（运行期可重设），`decide(toolName, input, ctx)` 重排裁决顺序：

```
1. mode == full_access            → allow         （工具级 DENY 由 AgentLoop 在 decide 之后另行兜底）
2. 命中敏感路径 pattern            → ask           （即便 read_only/workspace_write）
3. 否则按 mode + category:
     read_only        : READ→allow, 其它→ask
     workspace_write  : READ→allow, WRITE→(path在工作目录内? allow : ask), SHELL/OTHER→ask
     full_access      : allowed(步骤1已返回)
```

> 备选：只把 mode 映射成 `PermissionPolicy` 的 3 个布尔预设，不改 `decide`。
> 否决理由：无法表达 `workspace_write` 的**路径边界**（布尔预设无路径概念）；故保留类别+路径二级裁决。

`workingDirectory` 来源：`AgentLoop` 构造时经 `setWorkingDirectory` 注入，供 `decide` 的 `workspace_write` 路径判定（`path.normalize().startsWith(workingDir normalize)`）；`ctx` 为空时（2 参 stub）也仍可用注入值。

### D3: 运行时切换入口复用 `AgentLoop.setModel` 范式
`AgentLoop` 加 `private volatile PermissionMode mode`，默认 `READ_ONLY`，构造时 `perms.setMode(mode)` + `perms.setWorkingDirectory(workingDir)`；新增 `setPermissionMode(PermissionMode)`（volatile 保证并发可见），内部同步 `perms.setMode(...)`。已有的 `setModel` 是同一范式。

### D4: 初始模式与实时切换的 API 形状
- `POST /api/chat/send` 请求体加可选 `permission_mode` → `ChatController` 解析后传给 `ChatStreamService.create(sessionId, model, mode)` → `runtime.createLoop(..., mode)` → `AgentLoopFactory.buildLoop(..., mode)` → `AgentLoop`。
- 新增 `POST /api/chat/{stream_id}/permission`，body `{"mode": "<value>"}` → `ChatController` 调 `ChatStreamService.setPermission(streamId, mode)`（`actives.get(id).loop().setPermissionMode(mode)`），未找到流→404、非法 mode→400。
- `PermissionMode.from` 缺省：请求缺 `permission_mode` → `read_only`（与现状 read=allow/write=ask/shell=ask 一致）。

> 备选：把 mode 塞进 session 元数据并用 `GET/PUT /api/sessions/{id}/permission` 管理。
> 否决理由：mode 是**流（stream）**级运行状态（与 SSE 流生命周期一致，响应式创建/销毁），放流级更贴合；会话级会引入「无流也有 mode」的冗余状态与持久化压力。用户已确认「会话内即可，无持久化」，故不落盘。

### D5: 前端下拉
`Composer.tsx` statusBar 区加权限下拉（Read Only / Workspace Write / Full access），缺省 Read Only。onChange 调 `ChatApi.setPermission(streamId, mode)`；`ChatPanel`/`App` 持有当前 `streamId`，初始化新会话时把 `permission_mode` 传给 `send`。切换仅影响后续 `decide`，即时生效。

## Risks / Trade-offs

- **[模式切换与进行中 turn 的竞态]** → `mode` 用 volatile，仅影响切换之后的新 `decide`；正在执行的工具不受影响，符合直觉。
- **[workspace 边界误判（符号链接/父目录越界）]** → 用 `Path.normalize()` 归一化后 `startsWith` 判定；对工作区外路径一律 ask（Fail-Closed），宁多问不误放。
- **[`OTHER` 类别粒度粗]** → MCP/Skill/Plugin 工具在非 `full_access` 下统一 ask；v1 以简单可靠为优先，后续可用 `tool.isReadOnly(input)` 细化（本 change 不做）。
- **[敏感路径在 `full_access` 放行削弱 Fail-Closed]** → 用户明确选择「full_access 时也放行」；保留工具级 `DENY`（黑名单/`isDestructive`）作为兜底，避免误删/高危命令被放行。
- **[web `PermissionManager` 与 CLI 共用]** → CLI 仍走自己的 `buildConfirmer`/`allowAll`，不接收 mode；默认 `read_only` 保证无 mode 时行为与现状一致，故 CLI 无可感知影响。

## Migration Plan

- 纯增量：新增枚举、`createLoop`/`buildLoop`/`create` 增加可选 mode 参数（缺省 `read_only`），新增一个端点 + 一个可选请求字段；无 schema/数据迁移。
- 回滚：不改 CLI；web 若回退，仅需移除新端点与前端下拉，`permission_mode` 缺省即恢复现状行为。
- 部署顺序：core → web API → 前端构建（`mvn -o verify -DskipNpm=true` 跑后端；前端 `npm run build` 后再 `mvn package`）。

## Open Questions

- `ChatStreamService.create` 的 mode 缺省值是否希望由 `AgentConfig` 提供可配默认（而非硬编码 `read_only`）？——本 change 先硬编码 `read_only`，作为后续候选。
- MCP/Skill/Plugin 是否要在 `workspace_write` 下读工具（`isReadOnly`）自动放行？——先归入 ask，后续单独立项细化。
