## Why

agent-demo 目前只有四类会话日志（session.jsonl / chat.log / thinking.log / tools.log），但**输入的 context 完全没有记录**，系统级动作（配置、provider 路由、重试、压缩、权限裁决）只散落在 SLF4J app.log，且日志无法驱动测试或回放——出现"模型为什么这么答 / 这个动作哪来的"时无从排查，E2E 测试也无法断言 agent 的行为轨迹。

## What Changes

- 扩展会话事件模型：新增 `context/snapshot`（每轮完整上下文：system prompt + memory + 压缩摘要 + 文件重注入 + tools schema 摘要）、`system/*`（config / compact / retry / error）、`permission/decision` 事件；session header 引入 schemaVersion
- `SessionLogSink` / `SessionLogger` 升级：事件字段稳定、新增截断与长度上限、写失败仍不打断主流程
- 敏感信息脱敏：日志写出前对 API key（`sk-...`、`Bearer` 等已知模式）打码
- 保留策略：会话日志目录按年龄清理（config 可配），app.log 走 logback 轮转
- 日志驱动测试：golden 事件文件 + 事件序列断言工具，E2E 断言 agent 行为轨迹
- 会话回放：`session.jsonl` → `MessageHistory` 重建（调试 / /resume 复用）
- Web UI：新增日志查看（会话列表 + 事件流 + chat/tools 视图），沿用 trusted-hosts 鉴权
- 文档：更新 logging-design.md 与 test-design.md

## Capabilities

### New Capabilities

- `observability`: 可观测性——会话事件模型扩展（context 快照、全动作事件）、敏感信息脱敏、保留策略
- `testability`: 可测性——日志驱动测试（golden 事件断言）、会话回放能力

### Modified Capabilities

- `web-ui`: 新增日志查看 API（`GET /api/logs/**`）与前端页面；沿用 trusted-hosts 鉴权

## Impact

- `agent-core`：`log/`（SessionLogSink/SessionLogger/SessionRecorder/SessionId）、`core/`（AgentLoop 埋点扩展、ContextCompressor 事件）、`config/`（保留策略配置）、`session/`（回放复用）
- `agent-web`：新增 `LogController` + 前端日志页（React）
- 依赖：无新增（logback 轮转策略内置）；OpenSpec specs 新增 2 个 capability、修改 1 个
- 兼容性：现有事件类型与文件结构保持向后兼容（新增字段仅追加）；`logging.enabled=false` 时行为与现状一致
