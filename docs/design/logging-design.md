# agent-demo 日志架构设计

> 版本：v0.1（草稿）
>
> 配套文档：[design.md](./design.md)（§10 会话存储、§17 中断与编码、§11 错误处理）+ [test-design.md](../test-agent-demo/test-design.md)（本设计的测试视角）
>
> 设计来源：DSH（DeepSeek Harness）会话持久化与日志组织方式，按 agent-demo 的 Java CLI 规模裁剪。

## 1. 定位与目标

### 1.1 要解决的问题

agent-demo 当前只有一份全局日志 `~/.agent-demo/logs/agent.log`（`logback.xml` 的 `FileAppender`），所有模块的 SLF4J 日志都写进同一个文件。这带来三个问题：

| 问题 | 现状 | 影响 |
|------|------|------|
| 会话不可区分 | 所有会话日志混杂在一个文件 | 复盘某次对话时无法单独定位，需要 grep 过滤 |
| 过程不可追踪 | 对话、思考、工具调用只有零散 warn/info | 无法回放「模型是怎么一步步调用工具得出结论的」 |
| 会话未真正落盘 | `SessionStore` 已实现，但 `ChatCommand` 未接入 | 对话只跑在内存里，`/quit` 后无法找回或恢复 |

### 1.2 目标

仿照 DSH 的做法，把日志从「单文件应用日志」升级为「按会话分层的结构化日志」：

1. **每个会话拥有独立日志目录**，互不干扰。
2. **每类信息单独存放**：会话事件流、每轮聊天、思考过程、工具调用四类分开。
3. **会话真正落盘**：把已实现但未接入的 `SessionStore` 接进 `ChatCommand`，让对话可持久化。
4. **保留一份通用应用日志**（`app.log`）兜底全量诊断，WARN+ 镜像 stderr 不变。

### 1.3 不做什么（v0.1 边界）

- 不做会话恢复/加载（v0.2 加 `/resume`）。
- 不做 UI 回放面板。
- 不做日志压缩/轮转（单会话日志量可控；v0.2 加策略）。
- 不引入额外数据库，日志仍是文件。

---

## 2. 总体目录结构

### 2.1 目录树

```text
~/.agent-demo/
├── config.yaml
├── memory/                    # 长期记忆（§design.md 5.4）
├── cache/                     # 临时缓存
├── sessions/                  # 会话存档（JSONL，可恢复；v0.1 仅写）
└── logs/
    ├── app.log               # 通用应用日志（SLF4J，全局兜底）
    └── sessions/             # 按会话分层的结构化日志
        └── 2026-08-26T10-23-45-{uuid}/
            ├── session.jsonl   # 会话事件流（turn/message/tool 全量带 seq）
            ├── chat.log        # 每轮聊天（人类可读，只含对话正文）
            ├── thinking.log    # 思考过程（reasoning 流）
            └── tools.log       # 工具调用（call + result）
```

### 2.2 会话目录命名

采用「时间戳 + 短 ID」组合，保证全局唯一且可排序，与 `sessions/` 下的存档文件一致：

```text
{YYYY-MM-DD}T{HH-mm-ss}-{uuid8}
```

- 时间戳用当会话启动时刻（本地时区）。
- `uuid8` 取自会话级 UUID 前 8 位，用于重名物理区分。
- 生成逻辑集中在 `SessionId` 工具类，`SessionStore` 与 `SessionLogger` 共用。

### 2.3 目录与权限

| 路径 | 权限 | 说明 |
|------|------|------|
| `~/.agent-demo/logs/` | 0700 | 会话日志根 |
| `~/.agent-demo/logs/sessions/` | 0700 | 按会话分组 |
| `~/.agent-demo/logs/sessions/<id>/` | 0700 | 单会话 |
| `session.jsonl` / `chat.log` / `thinking.log` / `tools.log` | 0600 | 单文件 |

Windows 上 `Files.setPosixFilePermissions` 抛 `UnsupportedOperationException` 时静默跳过（现有 `SessionStore` 已这样处理）。

---

## 3. 四类日志的分工

### 3.1 分层总览

| 日志文件 | 内容 | 面向 | 格式 | 关键命名空间 |
|----------|------|------|------|--------------|
| `session.jsonl` | 全会话事件流（含消息、turn 边界、工具调用、token） | 程序回放 / 未来恢复 | JSONL | `event.*` |
| `chat.log` | 每轮用户输入 + 模型回答正文 | 人类速读对话 | 文本 | `chat.*` |
| `thinking.log` | 模型思考增量（reasoning 流） | 人类调试 | 文本 | `thinking.*` |
| `tools.log` | 每个工具调用的入参、出参、耗时、结果 | 人类调试 | 文本 | `tools.*` |

> **约定**：`session.jsonl` 是唯一「结构化真相源」，其余三类是面向人类可读的投影/切片。未来 `/resume` 只读 `session.jsonl`；`chat/thinking/tools` 仅用于查看。三类可读日志不是权威，重建优先级最低。

### 3.2 `session.jsonl`（事件流真相源）

仿 DSH 事件模型：首行是会话 header，后续每行一个事件，均带自增 `seq` 序号。采用 append-only 追加，崩溃尾行容忍（只保留完整行）。

```json
{"type":"session","version":1,"id":"...","createdAt":1785...000,"cwd":"E:\\claude-projects\\agent-demo"}
{"seq":0,"type":"user/message","timestamp":1785...001,"role":"user","content":"你好"}
{"seq":1,"type":"turn/start","timestamp":1785...002,"turn":0}
{"seq":2,"type":"assistant/message","timestamp":1785...050,"role":"assistant","content":"你好！..."
{"seq":3,"type":"turn/end","timestamp":1785...051,"turn":0,"usage":{"prompt":12,"completion":8}}
{"seq":4,"type":"assistant/message","timestamp":1785...060,"role":"assistant","toolCalls":[{"id":"call_1","name":"ReadFile"}]}
{"seq":5,"type":"tool/call","timestamp":1785...070,"callId":"call_1","name":"ReadFile","arguments":"{\"path\":\"README.md\"}"}
{"seq":6,"type":"tool/result","timestamp":1785...090,"callId":"call_1","name":"ReadFile","isError":false,"result":"...","elapsedMs":12}
```

事件类型表：

| type | 必填字段 | 说明 |
|------|---------|------|
| `session` | `version` `id` `createdAt` | 仅首行。header，标识格式版本与归属 |
| `user/message` | `content` | 用户输入 |
| `assistant/message` | `content`（可空）`toolCalls`（可空） | 模型回复，含可选工具调用骨架 |
| `thinking` | `content` | 思考增量（reasoner 模型的 `reasoning_content`） |
| `tool/call` | `callId` `name` `arguments` | 工具调用开始（拿到完整入参后） |
| `tool/result` | `callId` `isError` `result` `elapsedMs` | 工具执行结果 |
| `turn/start` | `turn` | 单轮对话开始（一次用户输入即一个 turn） |
| `turn/end` | `turn` `usage` | 单轮对话结束，带该轮累计 token |

> **seq 序号**：全局递增，从 0 开始。`.md` 文档中展示的时间戳为可读示例；真实值为毫秒数。

### 3.3 `chat.log`（每轮聊天）

人类可读的对话正文，不包含技术噪声（工具 schema、token、错误堆栈）。每轮一个块：

```text
──[2026-08-26 10:23:45] 用户 ──
你好

──[2026-08-26 10:23:50] 助手 ──
你好！有什么我可以帮你的？

──[2026-08-26 10:24:00] 用户 ──
读一下 README
```

### 3.4 `thinking.log`（思考过程）

仅当模型返回思考段（`reasoning_content`，如 `deepseek-reasoner`）时写入。增量文本按到达顺序追加：

```text
[2026-08-26 10:23:46] thinking> 用户问的是项目简介，我先定位 README。
[2026-08-26 10:23:47] thinking> README 在仓库根，用 ReadFile 读取最直接。
```

v0.1 默认为 `deepseek-chat`（无思考段），此文件通常为空，但保留结构以便 `deepseek-reasoner`（v0.2）直接复用。

### 3.5 `tools.log`（工具调用）

按时间顺序记录每次工具调用，含耗时与结果（截断后）：

```text
[10:23:50] TOOL> ReadFile   callId=call_1
  args: {"path":"README.md"}
[10:23:51] TOOL< done in 12ms, 3.2KB
```

失败时：

```text
[10:23:52] TOOL> Shell   callId=call_2
  args: {"command":"ls -la"}
[10:23:53] TOOL< ERROR in 1200ms: 命令返回非零退出码
```

> **输出截断**：工具结果可能很大（§design.md §6.5 有 `resultMaxBytes` 30KB）。`tools.log` 与 `session.jsonl` 都写截断后的结果（在模型能看到的同一份内容），避免日志无限膨胀。

---

## 4. 事件埋点：谁写哪类日志

### 4.1 写入位置映射

| 事件 | 写入谁 | 触发点 |
|------|--------|--------|
| session header | `SessionLogger.open()` | `ChatCommand.run()` 启动会话时 |
| `user/message` | `SessionLogger.recordUser()` | `ChatCommand` 收到一行用户输入 |
| `turn/start` | `SessionLogger.recordTurnStart()` | `AgentLoop.processTurn()` 开始 |
| `assistant/message` + `thinking` | `SessionLogger.recordAssistant()` | `AgentLoop` 提取 assistant 后 |
| `tool/call` | `SessionLogger.recordToolCall()` | `AgentLoop.executeOne()` 执行前 |
| `tool/result` | `SessionLogger.recordToolResult()` | `AgentLoop.executeTools()` 收尾 |
| `turn/end` | `SessionLogger.recordTurnEnd()` | `AgentLoop.processTurn()` 返回 |
| `/clear` | `SessionLogger.flush()` | `SlashCommand` `/clear` |

### 4.2 接入方式（避免侵入 AgentLoop 核心逻辑）

不把 `SessionLogger` 直接耦合进 `AgentLoop`（会污染主循环），改为在 `AgentLoop` 上挂一个可选的 `SessionLogSink` 观察者接口，主循环只负责广播事件，不关心日志实现：

```java
public interface SessionLogSink {
    void onTurnStart(int turn);
    void onUser(Message.User user);
    void onAssistant(Message.Assistant assistant, List<StreamChunk.Thinking> thinking);
    void onToolCall(ToolCall call, String argumentsJson);
    void onToolResult(ToolResult<?> result, long elapsedMs);
    void onTurnEnd(TurnResult result);
}
```

`AgentLoop` 构造器增加 `SessionLogSink sink` 参数（可空，默认 no-op）。`SessionLogger` 实现该接口，把事件分别写往四个文件。

> **思考段**：v0.1 的 `StreamChunk` 没有独立的 `thinking` 类型（`deepseek-reasoner` v0.2 才引入）。设计文档先预留 `SessionLogSink.onAssistant` 的 `thinking` 参数，v0.1 传空列表，v0.2 接上真实 reasoning 流即可，接口不变。

---

## 5. 会话落盘接入（SessionStore）

### 5.1 现状

`SessionStore`（append-only JSONL、双路径 flush、`lastSyncedOffset`、0600/0700）已完整实现，但 `ChatCommand` 从未实例化它——对话只写在内存 `MessageHistory`。

### 5.2 接入点

在 `ChatCommand.run()` 中创建会话：

```java
Path sessionId = SessionId.newSessionId();
Path sessDir = Paths.get(userHome, ".agent-demo", "sessions");
Path sessionFile = sessDir.resolve(sessionId + ".jsonl");
SessionStore store = new SessionStore(sessionFile, flushBatchSize, flushIntervalMs);
```

- 每次 `processTurn` 前把 user/assistant/tool 消息 append 进 `SessionStore`（复用 `SessionEntry` 工厂方法，结构与现有 `sessions/` 存档一致）。
- 关键节点（用户提交、Finished、工具调用完成）调用 `store.syncFlush()`。
- `ChatCommand` 退出（`/quit` 或正常结束）时 `store.close()`。

### 5.3 写入内容映射（sessions/ 存档 vs logs/sessions/ 结构化日志）

| 内容 | `sessions/<id>.jsonl`（存档） | `logs/sessions/<id>/session.jsonl`（结构化） |
|------|------------------------------|---------------------------------------------|
| 用途 | 数据持久化 / 未来恢复 | 事件回放 / 程序解析 |
| 单元 | `SessionEntry`（user/assistant/tool_result/meta） | 事件（带 seq） |
| 模型无关 | 是 | 是 |
| 写入时机 | `ChatCommand` + `AgentLoop` | `AgentLoop` 广播 |

两者并存、职责分离：存档面向「恢复对话」，结构化日志面向「回放/分析」。

---

## 6. 配置项

在 `~/.agent-demo/config.yaml` 的 `logging` 段（新增），提供日志根目录、开关与截断上限：

```yaml
logging:
  enabled: true               # 是否写会话结构化日志；false 时 SessionLogger 为 no-op（仍走 app.log）
  dir: ~/.agent-demo/logs/    # 会话日志根（独立于 app.log 自动生成的位置）
  resultMaxChars: 30000       # tools.log / session.jsonl 中工具结果截断上限（与 tools.resultMaxBytes 对齐）
```

**与 `app.log` 的关系**：

| 项 | `app.log` | 会话结构化日志 |
|----|-----------|----------------|
| 写入者 | SLF4J（logback） | `SessionLogger`（直接文件写） |
| 位置 | `<logging.dir>/app.log`（logback 配置） | `<logging.dir>/sessions/<id>/*` |
| 级别过滤 | 有（root=INFO，WARN+ 镜像 stderr） | 无（按事件类型全量写） |
| 用途 | 系统/模块诊断兜底 | 业务事件回放 |

**通用日志（`app.log`）级策略**：启动/配置/错误/重试等系统级日志走 SLF4J 写 `app.log`；会话内容类事件不走 SLF4J，改走 `SessionLogger`，避免「结构化业务日志」与「系统日志」混在 appender 里互相膨胀。

---

## 7. 测试策略

### 7.1 单元测试

| 模块 | 测试点 |
|------|--------|
| `SessionId` | 命名格式、唯一性、时间戳前缀 |
| `SessionLogger` | open 写 header；四类文件是否各自只写对应内容；seq 自增；`resultMaxChars` 截断 |
| `SessionLogSink`（no-op 默认） | AgentLoop 不接 sink 时零副作用、零异常 |
| `AgentLoop` 事件广播 | 用 fake sink 断言 turn/start→assistant→tool/call→tool/result→turn/end 顺序 |
| `ChatCommand` 会话落盘 | 注入 `SessionStore`，`processTurn` 后断言 `sessions/*.jsonl` 存在且含 user/assistant 行 |

### 7.2 集成测试

- 真实 `SessionLogger` + `AgentLoop` 跑一轮 fake provider：断言四个文件内容符合 §3 格式。
- `SessionStore` + `ChatCommand`：一轮对话后 `sessions/` 下有存档，`/quit` 触发 `close()`。

### 7.3 验收标准

1. 一次 `chat` 会话在 `logs/sessions/<id>/` 下生成四个文件。
2. `session.jsonl` 首行为 header，后续事件 seq 连续。
3. 工具调用结果超过 `resultMaxChars` 时被截断并带截断标记。
4. `/clear` 后新历史仍写入同一会话的四个文件（会话不切换）。
5. 不接日志时（`logging.enabled=false`）行为与旧版完全一致（无会话文件生成，仅 `app.log`）。

---

## 8. 演进路线

| 版本 | 能力 | 说明 |
|------|------|------|
| v0.1 | 四类会话日志 + 会话落盘接入 | 本文所述 |
| v0.1 | `deepseek-reasoner` 思考段接入 | `SessionLogSink.onAssistant` 的 `thinking` 参数由空转实（§4.2 已预留） |
| v0.2 | `/resume` 读 `session.jsonl` | 用事件流恢复 `MessageHistory`，替代 `sessions/` JSONL |
| v0.2 | 日志轮转 / 保留策略 | 会话级目录按年龄清理；`app.log` 加 `SizeAndTimeBasedRollingPolicy` |
| v0.2 | `/history` 展示思考与工具调用 | 读取 `thinking.log` / `tools.log` 提供细节视图 |
| v1.0 | 会话日志 Web 回放 | DSH Web UI 侧展示 |

---

## 9. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 双写路径（SessionStore + SessionLogger）不一致 | 存档与结构化日志漂移 | 两者都由同一 `AgentLoop` 事件驱动；单测覆盖顺序一致性 |
| 四文件写入性能 | 每轮多个小文件 IO | `SessionLogger` 内部用 BufferedWriter + `flush` 聚合；高频事件批量 flush |
| `config.yaml` 的 `dir` 与 logback 内 `app.log` 路径不一致 | 文件散落两处 | `dir` 是会话日志根；`app.log` 由 logback 单独管理（§6 表格明确） |
| 工具结果过大撑爆日志 | 磁盘膨胀 | `resultMaxChars` 截断（已对齐 `tools.resultMaxBytes`） |
| 事件写入异常影响主流程 | 对话被打断 | `SessionLogger` 写失败仅 `log.warn` 并继续，不让日志故障影响对话 |

---

> 修订记录
> v0.1.0（2026-08-26）：初版；仿 DSH 会话日志组织，定义四类日志分层与会话落盘接入。

## 10. 可观测性扩展（v0.1.2）

> 本版在四类日志基础上补齐「全动作事件」与「可维可测」能力（对应 OpenSpec change `add-observability-testability`）。

### 10.1 新增事件类型

在 §3.2 事件表基础上追加（现有事件形状不变，向后兼容）：

| type | 触发点 | 关键字段 |
|------|--------|---------|
| `context/snapshot` | `AgentLoop.toRequest()` 每轮一次（含工具后续推） | turn、systemPrompt（按 `snapshotMaxChars` 截断）、memoryInjected、compacted、recentFiles、toolNames、messageCount、estTokens |
| `system/config` | 启动装配完成 | provider、model、loggingEnabled（脱敏） |
| `system/compact` | `ContextCompressor` 压缩成功/失败 | beforeTokens、afterTokens、success、summary（截断）、errorClass |
| `system/retry` | `LlmRetry` 每次重试 | attempt、errorClass、errorMsg（截断） |
| `system/error` | 回合级异常 | errorClass、message（截断 500） |
| `permission/decision` | 权限裁决 ask/deny | tool、path、decision、reason（allow 不记录） |

`session.jsonl` header `version` 1→2；旧 reader 忽略未知 type 兼容。

### 10.2 context 快照

- 每轮请求前记录「模型看到了什么」：system prompt 截断（默认 2000 字符，`logging.snapshotMaxChars`）+ 消息元数据（数量/估算 token/工具列表/压缩与文件重注入标记）
- 消息正文不重复转储（由 user/message 与 assistant/message 事件覆盖），避免体积膨胀

### 10.3 敏感信息脱敏

- `log/Redactor`：sk- 前缀 key、Bearer token、apiKey 键值对三种模式统一替换 `***REDACTED***`
- 四个文件写路径统一过滤（session.jsonl 序列化后、chat/thinking/tools 正文），杜绝明文 key 落盘

### 10.4 保留策略

| 项 | 配置 | 默认 |
|----|------|------|
| 会话目录过期清理 | `logging.retentionMaxAgeDays` | 30 天 |
| 会话目录数量上限 | `logging.retentionKeepSessions` | 50 |
| app.log 轮转 | logback `SizeAndTimeBasedRollingPolicy` | 10MB × 7 天 |

清理在 `SessionLogger` 构造（新会话创建）时执行，失败仅 WARN。

### 10.5 会话回放

- `log/SessionReplay.replay(session.jsonl)` → `MessageHistory`（user/assistant(toolCalls)/tool 顺序重建；context/snapshot、system/*、未知类型跳过）
- 供调试与测试复用；v0.2 `/resume` 可切到事件流

### 10.6 Web 日志查看

| API | 功能 |
|-----|------|
| `GET /api/logs/sessions` | 列出日志会话目录 |
| `GET /api/logs/sessions/{id}/events?offset=&limit=` | 分页读事件 |
| `GET /api/logs/sessions/{id}/files/{name}` | 读 chat/tools/thinking/session 文本 |

`id`/`name` 白名单校验防路径穿越；沿用 trusted-hosts 鉴权。前端 `/logs` 页面提供事件/聊天/工具三视图。

> 修订记录
> v0.1.2（2026-08-29）：新增 §10 可观测性扩展（全动作事件、context 快照、脱敏、保留策略、回放、Web 日志查看）。
