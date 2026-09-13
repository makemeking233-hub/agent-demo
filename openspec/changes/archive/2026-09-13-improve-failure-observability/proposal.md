## Why

2026-09-13 的两次事故（`NoClassDefFoundError: EditFileTool$Input` 撕连接、以及由此导致的会话永久 400）排查极其费劲，暴露出**可观测性在故障路径上全线失效**。逐条核实后的缺陷：

| # | 缺陷 | 证据 |
|:--:|------|------|
| 1 | 错误上报挂在**会被绕过的接缝**上 | `AgentLoop.processTurn` 用 `.doOnError(e -> sink.onSystemEvent("system/error", ...))`，但 `LinkageError` 被 Reactor `Exceptions.throwIfFatal` rethrow，永不变成 `onError` 信号 → 事故时 `system/error` **一条都没落** |
| 2 | 工具调用「有始无终」无声 | `logs/sessions/1789277081599/tools.log` 末行是 `[16:10:38] TOOL> EditFile` 且**无对应 `TOOL<`**，文件写入时间即 16:10:38。故障指纹一直在，但没有任何机制标记它 |
| 3 | 关键关联 id 缺失 | `ChatStreamService` 只打 `log.warn("turn failed for stream {}", streamId, ...)`，无 `sessionId` / `workspace` → 拿到 400 无法反查会话，只能靠文件 mtime 猜 |
| 4 | 日志**写入方与读取方目录不一致** | 写：`logback.xml` / `AgentConfig` / `AgentLoopFactory` / `ChatCommand` 用 `${user.dir}/logs`（随 CWD 漂移）；读：`LogController`（`/api/logs*`）与 `InitCommand` 用 `~/.agent-demo/logs`。实测后者停在 9/2，**日志查看入口读的是一份再没人写过的旧目录** |
| 5 | 测试污染运行时日志 | 无 `logback-test.xml` → 测试回落主配置。实测 `agent-core/logs/app.log` 有 78 行含 junit 临时目录，其中 10 处 `NoClassDefFoundError` 全是跑测试写进去的 |
| 6 | 不变式违规静默 | 「有 `tool_calls` 无 `tool_result`」完全可检测，但无校验、无告警、无诊断入口；数据躺在存档里直到上游 400 才被动暴露 |

结果：整个定位过程是「400 报文 → 翻日志只拿到 streamId → 靠 mtime 猜会话 → 手写 PowerShell 扫 JSONL」，**没有任何一步是日志主动告知的**。

## What Changes

**P0 — 让故障自己喊出来**

- 报错边界搬到 **Reactor 之外**：`ChatStreamService.start` 与 CLI REPL 的 turn 边界由 `catch (Exception)` 改为捕获 `Throwable`（`VirtualMachineError` / `ThreadDeath` 仍原样抛），保证 `Error` 类失败也落一条 `system/error` 事件与一行带全量关联 id 的 ERROR 日志。
- 工具调用**强制收口**：`AgentLoop` 维护本轮在途工具调用集合；turn 边界与外部边界都会 `closePendingToolCalls(reason)`——对残留调用打 ERROR 日志、补 `sink.onToolResult(err)`（使 `tools.log` 闭合）并回流 history。
- `ChatStreamService` 的失败日志补齐 `sessionId` / `workspace` / `model`。

**P1 — 让日志可找、可关联**

- 统一日志根到 `~/.agent-demo/logs`（与 `InitCommand` 约定、`LogController` 读取一致），不再随 `user.dir` 漂移；`LogController` 保持读取同一目录。
- 新增 `logback-test.xml`，测试日志写 `target/test-logs/`，不再污染运行时日志。
- 会话恢复时的配对修复打可检索 WARN，带 `sessionId` 与缺失的 `toolCallId` 列表。

**P2 — 把人工排查固化成一条命令**

- `SessionDiagnostics.scan(sessionsDir)`：扫描会话存档的配对不变式，报告存在悬挂 `tool_calls` 的会话及缺失 id。
- `GET /api/diagnostics`：暴露上述报告 + 当前日志根目录，一条请求即可看到「哪个会话记录不完整」。

## Capabilities

### Modified Capabilities

- `observability`：新增「故障路径不可绕过」「工具调用收口」「日志根一致」「诊断入口」相关 Requirement。

## Impact

- **agent-core**：`AgentLoop`、`ChatCommand`、`AgentConfig`、`AgentLoopFactory`、`SessionResumeLoader`、`ToolCallPairing`、`logback.xml`、新增 `Throwables`、`SessionDiagnostics`、`logback-test.xml`
- **agent-web**：`ChatStreamService`、新增 `DiagnosticsController`
- **配置**：日志根目录变更（需同步 README / logging-design 文档）
- **测试**：新增 `ToolCallClosureTest`、`SessionDiagnosticsTest`、`ThrowablesTest`、`DiagnosticsControllerTest`；扩 `ChatStreamServiceTest`、`SessionResumeLoaderTest`
