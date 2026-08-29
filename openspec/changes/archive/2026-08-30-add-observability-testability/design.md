## Context

agent-demo 现有日志体系（logging-design.md）通过 `SessionLogSink` 观察者模式 + `SessionLogger` 产出四类文件：`session.jsonl`（事件流真相源）、`chat.log`、`thinking.log`、`tools.log`，另有 `SessionStore` 会话存档（`sessions/*.jsonl`）。已覆盖：用户对话、模型回复、工具调用与返回值、turn 边界。

**缺口**（本次 change 要解决的）：
1. **输入的 context 无记录**——每轮实际发给模型的完整上下文（system prompt + memory 注入 + 压缩摘要 + 文件重注入 + tools schema）无法事后查看，出现"模型为什么这么答"时无从排查。
2. **系统级动作无结构化事件**——配置加载、provider 路由、重试、上下文压缩、权限裁决只散落 SLF4J app.log（非结构化、与会话无关联）。
3. **日志不可驱动测试**——E2E 无法断言 agent 的行为轨迹；没有 golden 事件、没有回放工具。
4. **无脱敏与保留策略**——`session.jsonl` 若记录 context 可能含 API key 占位；日志无限增长。

约束：JDK 17 / 无新增依赖 / jacoco LINE≥80% BRANCH≥70% / 日志故障不得打断主流程（现有 `safe()` 模式）/ 不引入数据库。

## Goals / Non-Goals

**Goals:**
- 每轮可回答"模型看到了什么"（context 快照）与"agent 做了什么"（全动作事件序列）
- 事件模型向后兼容（现有事件类型不变，新增仅追加）
- 日志可驱动 E2E 断言（golden 事件）与调试回放
- API key 等敏感信息在日志中打码
- 日志有界（保留策略），不无限膨胀
- Web UI 可查看会话事件流与可读日志

**Non-Goals:**
- 不做日志轮转 UI 管理（策略由 config 控制）
- 不做基于事件流的 `/resume`（v0.2；本次只做只读回放工具，供调试/测试）
- 不做 metrics/指标聚合（非本次需求）
- 不改变 CLI 与 web 现有行为（`logging.enabled=false` 时与现状一致）

## Decisions

### D1. 事件模型扩展：增量追加，不重构

在现有 7 类事件（session / turn/start / user/message / assistant/message / tool/call / tool/result / turn/end）基础上**追加** 6 类，不修改现有事件形状：

| 新事件类型 | 触发点 | 关键字段 |
|-----------|--------|---------|
| `context/snapshot` | `AgentLoop.toRequest()` 后（每轮 1 次） | turn、systemPrompt（截断）、memoryInjected、compacted、recentFiles、toolNames、messageCount、estTokens |
| `system/config` | 启动装配完成 | provider、model、logging 开关、retention（全部脱敏） |
| `system/compact` | `ContextCompressor` 压缩成功/失败 | beforeTokens、afterTokens、summary（截断）、success |
| `system/retry` | `LlmRetry` 重试 | attempt、errorClass、errorMsg（截断） |
| `system/error` | 回合级异常（REPL 捕获 / web 回合失败） | errorClass、message |
| `permission/decision` | 权限裁决产生 ask/deny | tool、path、decision、reason |

实现方式：`SessionLogSink` 增加 **default 方法**（`onContextSnapshot` / `onSystemEvent` / `onPermissionDecision`），`SessionRecorder` 透传，`SessionLogger` 写 `session.jsonl`；`AgentLoop` / `ContextCompressor` / `PermissionManager` / `LlmRetry` 在现有埋点处追加广播。

> 备选：重建统一事件总线（所有动作走一个 `recordEvent(type, payload)`）。否决——侵入面大、与现有 sink 广播重复；default 方法追加即可覆盖需求，且保持"日志不侵入主循环"的既有设计。

### D2. context 快照粒度：元数据 + 截断正文，不做全量转储

每轮完整 history + system prompt 全量写入会导致 `session.jsonl` 每轮重复膨胀（一轮 100KB 上下文 × 50 轮 = 5MB/会话）。决策：
- `systemPrompt`：截断到 2000 字符（可配 `logging.context.snapshotMaxChars`）
- messages：只记 **role 序列 + 总数 + 估算 token**，不重复转储正文（正文已由 user/message 与 assistant/message 事件覆盖）
- 压缩/文件重注入：记 `compacted: true` + `recentFiles: [path...]` 元数据

**可回答性**：`context/snapshot`（元数据）+ 同轮 user/assistant 事件（正文）+ `system/compact`（摘要）= 完整还原"模型看到了什么"。

### D3. 脱敏：写入前单点 Redactor

`SessionLogger` 所有 `session.jsonl` 写入统一过 `Redactor`（新增 `log/Redactor.java`）：
- 规则（正则，覆盖 `.gitleaks.toml` 的 key 模式）：`sk-[a-zA-Z0-9]{16,}`、`Bearer [A-Za-z0-9._-]+`、`api[_-]?key['"]?\s*[:=]\s*['"]?[^'"]+`
- 替换为 `***REDACTED***`
- 配置值在 `system/config` 事件直接打码（`ConfigLoader` 不参与，SessionLogger 写前处理）
- 单测用真实 key 格式验证不泄漏

> 备选：各埋点处脱敏。否决——遗漏风险高；单点过滤保证所有写路径一致。注意 chat.log/tools.log 也走同一 Redactor（对话内容可能包含用户贴的 key）。

### D4. 保留策略：启动清理 + logback 轮转

- 会话日志目录：`SessionLogger` 构造（新会话创建）时扫描 `logs/sessions/`，删除 mtime 超过 `logging.retention.maxAgeDays`（默认 30）的目录；数量上限 `keepSessions`（默认 50，超限删最旧）
- `app.log`：logback.xml 改 `SizeAndTimeBasedRollingPolicy`（10MB × 7 天，`logs/app.%d{yyyy-MM-dd}.%i.log`）
- 不引入调度器（CLI 是短生命周期进程，启动时清理足够）

### D5. 日志驱动测试：规范化 golden + 事件断言工具

- 新增测试工具 `SessionEventAssertions`（test 目录）：读 `session.jsonl` → 按 type 过滤/断言顺序/断言字段
- Golden 事件文件：`src/test/resources/e2e/events/<case>.jsonl`，E2E 用 wiremock 跑一轮（读文件 → 工具调用 → 回复），断言事件类型序列 == golden 的规范化序列
- **归一化**：时间戳、seq、uuid 在断言前归一化（替换为占位符），避免脆弱
- 新增日志代码（Redactor / RetentionCleaner / SessionLogger 扩展）全部有单测，满足 jacoco 门禁

### D6. 会话回放：只读重建 MessageHistory

新增 `log/SessionReplay.java`：
- 输入：`session.jsonl` 路径
- 输出：`MessageHistory`（按事件顺序重建 user → assistant(tool_calls) → tool 消息；`context/snapshot` 与 system 事件跳过）
- 用途：调试工具（CLI 隐藏命令或测试直接调用）+ E2E 测试复用；v0.2 的 `/resume` 可切到事件流（比 SessionStore 存档更完整）

### D7. Web UI 日志查看

agent-web 新增 `LogController`（沿用 trusted-hosts 鉴权与 loopback 默认）：

| API | 功能 |
|-----|------|
| `GET /api/logs/sessions` | 列出日志会话目录（id、createdAt、文件存在性） |
| `GET /api/logs/sessions/{id}/events?offset=&limit=` | 分页读 `session.jsonl` 事件 |
| `GET /api/logs/sessions/{id}/files/{name}` | 读 chat.log / tools.log / thinking.log（文本） |

- 安全：`{id}` 与 `{name}` 白名单校验（`^[0-9A-Za-z._-]+$`）+ 路径解析限制在 logs 根内，防路径穿越
- 前端：React 新增 `/logs` 路由——会话列表 → 事件流表格（类型/时间/内容，按 type 过滤）+ 聊天/工具视图切换
- 数据流：`LogController` 复用 `SessionLogger` 的目录约定（`logs/sessions/<id>/`），不新增存储

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| 事件膨胀导致 session.jsonl 过大 | context 快照截断（D2）+ 保留策略（D4）+ 现有 `truncate()` |
| 脱敏遗漏新 key 格式 | Redactor 规则集中可扩展 + 单测覆盖常见格式 + 与 .gitleaks.toml 规则对齐 |
| 日志驱动测试脆弱（顺序/时间戳变化） | 断言前归一化（D5），golden 只断言类型序列与关键字段 |
| 新增 sink default 方法被误实现 | default 方法 + NOOP 保证零副作用；SessionRecorder 透传统一 |
| Web 日志 API 暴露对话内容 | trusted-hosts + 默认仅 loopback + 文件白名单校验 |
| 回放消息顺序与真实对话不一致 | 事件模型保证因果序（现有 seq）；回放单测覆盖多轮+工具场景 |

## Migration Plan

1. **向后兼容**：现有事件类型与文件结构不变；新增事件为追加；`session.jsonl` header `version` 1→2（老文件仍可读，reader 忽略未知 type）
2. 部署顺序：agent-core（事件模型 + 脱敏 + 保留）→ agent-core（回放 + 测试基建）→ agent-web（日志 API + 前端）→ 文档
3. 回滚：`logging.enabled=false` 即回到无日志行为；新增代码均为独立类，不影响主链路

## Open Questions

- `context/snapshot` 的 `systemPrompt` 截断上限默认值（2000 字符）是否合适？—— 可在实施时按实际 prompt 长度调整
- Web UI 日志页是否需要实时刷新（SSE 推送新事件）？—— v0.1 先做轮询/手动刷新，实时推送留 v0.2
