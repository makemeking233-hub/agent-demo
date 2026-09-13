## 1. 日志根统一（P1）

- [x] 1.1 `logback.xml` 写 `${user.home}/.agent-demo/logs/app.log`（含轮转 pattern）
- [x] 1.2 `AgentConfig` 默认日志目录改 `~/.agent-demo/logs`
- [x] 1.3 `AgentLoopFactory` / `ChatCommand` 里 `${user.dir}/logs` 改同一目录
- [x] 1.4 新增 `agent-core` 与 `agent-web` 两份 `logback-test.xml` → `target/test-logs/`
- [x] 1.5 文档：`logging-design.md` §2.3 记录「实现曾偏离设计」与测试隔离

## 2. 不可绕过的报错边界（P0）

- [x] 2.1 新增 `Throwables.reraiseIfJvmFatal` / `isJvmFatal` + `ThrowablesTest`（5 例）
- [x] 2.2 `ChatStreamService.start` 改 `catch (Throwable)`：ERROR 日志带 `streamId/sessionId/workspace/model`，收口在途工具调用，emit SSE error 并关流
- [x] 2.3 CLI REPL 边界改 `catch (Throwable)`（同样先跳过 JVM 致命错误），并收口工具调用

## 3. 工具调用强制收口（P0）

- [x] 3.1 `AgentLoop` 加本轮在途集合 `pendingToolCalls`，`executeOne` 进入时登记、出结果（成功/失败两路）时移除
- [x] 3.2 新增 `closePendingToolCalls(reason)`：ERROR 日志 + 补 `sink.onToolResult(err)`（闭合 `tools.log`）+ 回流 history；幂等
- [x] 3.3 调用点：`processTurn` 起始（安全网）/ `doOnError` / `doFinally`，以及外部边界
- [x] 3.4 `ToolCallClosureTest`（2 例：取消中途调用后闭合 + 幂等）

## 4. 配对修复告警（P1）

- [x] 4.1 `ToolCallPairing.danglingCallIds` 抽出共用判定（`repair` 与诊断共用）
- [x] 4.2 `SessionResumeLoader` 的 `loadById` / `loadArchivedById` 传入 `sessionId`，修复时打可检索 WARN（sessionId + 缺失 id 列表）

## 5. 诊断入口（P2）

- [x] 5.1 `SessionDiagnostics.scan(sessionsDir, logsDir)` + `SessionStore.loadFile` + `SessionResumeLoader.toMessagesRaw`（观察修复前真实状态）
- [x] 5.2 `GET /api/diagnostics[?workspace=]` + `DiagnosticsDto`
- [x] 5.3 `SessionDiagnosticsTest`（4 例）+ `DiagnosticsControllerTest`（3 例）

## 6. 收尾

- [x] 6.1 `mvn -o -pl agent-core,agent-web verify` 通过 + 前端 vitest 全绿
- [x] 6.2 手工确认日志迁移与隔离（见 §7）
- [x] 6.3 `openspec validate improve-failure-observability --strict` + archive + commit + push

## 7. 验收证据

| 项 | 证据 |
|----|------|
| 日志根统一生效 | `~/.agent-demo/logs/app.log` 由「停在 9/2 的 4KB」变为**持续写入**（实测 18:03:31，15.76KB）→ `/api/logs*` 不再读空目录 |
| 测试日志隔离 | 跑 `DiagnosticsControllerTest` 前后运行时 `app.log` **字节数不变**（16137 → 16137）；测试日志落在 `agent-web/target/test-logs/test.log` |
| Error 类失败被上报 | `ChatStreamServiceFailureObservabilityTest`：`Mono.error(new NoClassDefFoundError(...))` 场景下，日志含 `turn failed` + `stream=` + `session=sess-1` + `workspace=ws-1` + `NoClassDefFoundError`；`verify(loop).closePendingToolCalls(...)`；SSE 收到 `error` 并关流 |
| JVM 致命错误不降级 | `ThrowablesTest`：`OutOfMemoryError` / `StackOverflowError` 重新抛出；`NoClassDefFoundError` 与普通 `Error` 不抛出；`null` 无操作 |
| 工具调用收口 | `ToolCallClosureTest`：工具永不返回结果时取消订阅 → history 出现 `isError=true` 的结果、`ToolCallPairing.danglingCallIds` 为空、`sink.onToolResult` 恰好 1 次；重复调用返回 0 |
| 诊断入口 | `SessionDiagnosticsTest` 4 例（悬挂可报告 / 全配对为空 / 多会话只报坏的 / 目录缺失返回空）+ `DiagnosticsControllerTest` 3 例（含 `DiagnosticsDto.from(null)` 不 NPE） |
| 无回归 | agent-core 全量 + agent-web 169 用例全绿（jacoco 违规项与改动前逐项一致：config 0.40/0.46、security 0.62、api 0.66 — 均为既有欠账） |

## 8. 遗留

- 既有 jacoco 包级门禁欠账（`config` 仅由 `SslCertificateGenerator` 造成、`api` 来自 `ModelsController`/`ModelRegistry`）仍未处理，与本 change 无关。
- CLI 侧的 `/diag` 斜杠命令未做（复用 `SessionDiagnostics.scan` 即可）；本次只交付了 Web 端点。
- 日志根目前已统一，但**未提供配置项覆盖**（如需自定义位置，后续在 `AgentConfig.logging.dir` 显式配置）。
