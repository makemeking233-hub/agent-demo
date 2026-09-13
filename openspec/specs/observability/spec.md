# observability Specification

## Purpose

会话可观测性：全动作事件记录、context 快照、敏感信息脱敏、日志保留策略。
## Requirements
### Requirement: 全动作事件记录

系统 SHALL 将会话事件流（`session.jsonl`）扩展为覆盖 agent 全部动作的结构化事件序列：除现有 `user/message`、`assistant/message`、`tool/call`、`tool/result`、`turn/start`、`turn/end` 外，SHALL 追加 `context/snapshot`、`system/config`、`system/compact`、`system/retry`、`system/error`、`permission/decision` 六类事件，且现有事件形状不变（向后兼容）。

#### Scenario: 配置加载被记录

- **WHEN** 会话启动、日志功能启用
- **THEN** `session.jsonl` 首行 header 记录 `version: 2`，随后至少一条 `system/config` 事件，含 `provider`、`model`、`loggingEnabled` 字段，且不含明文 API key

#### Scenario: 上下文压缩被记录

- **WHEN** `ContextCompressor` 触发一次压缩（成功或失败）
- **THEN** 事件流追加一条 `system/compact` 事件，含 `beforeTokens`、`afterTokens`、`success` 与截断后的 `summary`

#### Scenario: provider 重试被记录

- **WHEN** `LlmRetry` 对一次请求重试（首次失败后）
- **THEN** 事件流追加一条 `system/retry` 事件，含 `attempt`、`errorClass` 与截断后的 `errorMsg`

#### Scenario: 回合异常被记录

- **WHEN** 单个回合抛出未捕获异常（REPL 或 web 回合失败路径）
- **THEN** 事件流追加一条 `system/error` 事件，含 `errorClass` 与 `message`，且主流程不被打断（REPL 继续等待下一条输入）

#### Scenario: 权限裁决被记录

- **WHEN** 某工具调用触发权限裁决且结果为 `ask` 或 `deny`
- **THEN** 事件流追加一条 `permission/decision` 事件，含 `tool`、`path`、`decision` 与 `reason`

### Requirement: context 快照

系统 SHALL 在每一轮对话发起 LLM 请求前记录一条 `context/snapshot` 事件，包含该轮实际发送上下文的元数据：`turn`、`systemPrompt`（截断至 `snapshotMaxChars`，默认 2000 字符）、`memoryInjected`、`compacted`、`recentFiles`（路径列表）、`toolNames`（工具名列表）、`messageCount`、`estTokens`。

#### Scenario: 每轮快照写入

- **WHEN** 用户提交一条消息且日志功能启用
- **THEN** 该轮 `turn/start` 之后、`assistant/message` 之前出现一条 `context/snapshot`，`messageCount` 等于该轮 history 消息数，`toolNames` 包含已注册工具名

#### Scenario: 快照正文截断

- **WHEN** `systemPrompt` 长度超过 `snapshotMaxChars`
- **THEN** 快照中的 `systemPrompt` 字段被截断至上限并带截断标记，其余字段完整

#### Scenario: 压缩后的快照标记

- **WHEN** 上一轮发生过上下文压缩
- **THEN** 本轮 `context/snapshot` 的 `compacted` 为 `true`，且 `recentFiles` 含压缩后重注入的文件路径

### Requirement: 敏感信息脱敏

系统 SHALL 在写入任何会话日志文件（`session.jsonl` / `chat.log` / `thinking.log` / `tools.log`）前，对已知敏感模式进行打码替换，替换为 `***REDACTED***`；至少覆盖：`sk-` 前缀的 API key（≥16 位字母数字）、`Bearer <token>`、`apiKey`/`api_key` 键值对。

#### Scenario: 对话中的 key 被打码

- **WHEN** 用户消息或工具结果中包含形如 `sk-aBcDeFgHiJkLmNoPqRsT0123456789` 的字符串
- **THEN** 四个日志文件中该字符串均被替换为 `***REDACTED***`，原文不出现

#### Scenario: 配置事件不含明文 key

- **WHEN** `system/config` 事件写出
- **THEN** 事件内容不含配置中的 `apiKey` 明文（缺失或以打码形式出现）

### Requirement: 日志保留策略

系统 SHALL 限制会话日志与通用日志的磁盘占用：会话日志目录在新建会话时清理超过 `retention.maxAgeDays`（默认 30）天未修改的目录，并最多保留 `retention.keepSessions`（默认 50）个目录（超限删除最旧）；`app.log` 按大小与时间轮转。

#### Scenario: 过期会话目录被清理

- **WHEN** 新建会话目录时，`logs/sessions/` 下存在 mtime 超过 `maxAgeDays` 的旧目录
- **THEN** 旧目录被删除，新会话目录正常创建

#### Scenario: 数量上限生效

- **WHEN** `logs/sessions/` 下目录数超过 `keepSessions`
- **THEN** 最旧的目录被删除，剩余目录数不超过上限

#### Scenario: 清理失败不阻断

- **WHEN** 清理过程中单个目录删除失败（权限等）
- **THEN** 该目录跳过、记录 WARN，清理继续处理其余目录，新会话正常启动

### Requirement: HTTP Client Timeouts

The system SHALL configure explicit HTTP timeouts on the LLM provider WebClient to prevent indefinite blocking on slow or hung upstream services.

#### Scenario: connection timeout fires

- GIVEN the LLM provider WebClient is configured with `connectTimeout=10s`
- WHEN the upstream host is unreachable (TCP SYN times out)
- THEN the HTTP call fails within 10s with a `WebClientRequestException`
- AND the failure is logged at WARN level
- AND `SlashCommand.dispatch` propagates the failure to the REPL user

#### Scenario: response timeout fires

- GIVEN the LLM provider WebClient is configured with `responseTimeout=60s`
- WHEN the upstream returns headers but no body within 60s
- THEN the HTTP call fails with a timeout exception
- AND the failure is logged at WARN level

### Requirement: 故障上报不可被绕过

回合执行 SHALL 在 Reactor 之外设置错误边界：订阅侧（`ChatStreamService.start` 的 executor 任务、CLI REPL 的 turn 循环）SHALL 捕获 `Throwable` 并上报，SHALL NOT 仅捕获 `Exception`。

`VirtualMachineError` 与 `ThreadDeath` SHALL 原样抛出，不降级。

#### Scenario: Error 类失败仍被上报

- **WHEN** 回合执行过程中逃出 `LinkageError`（如 `NoClassDefFoundError`）
- **THEN** 日志出现一条 ERROR，含 `streamId` / `sessionId` / `workspace` / 模型名与异常摘要
- **AND** 该回合落一条 `system/error` 事件
- **AND** SSE 收到 `error` 事件并正常关流

#### Scenario: OOM 不被吞掉

- **WHEN** 回合执行抛出 `OutOfMemoryError`
- **THEN** 该错误继续向调用方传播，不被降级为回合失败

### Requirement: 工具调用必须有始有终

每个成功进入执行的工具调用 SHALL 产生唯一一条对应的结束记录（`tools.log` 的 `TOOL<`），无论其成功、失败或异常中断。

#### Scenario: 异常中断也收口

- **WHEN** 某工具调用在参数解析或执行中被异常中断，未产生结果
- **THEN** 该调用被补一条错误结果
- **AND** `tools.log` 出现对应的 `TOOL<` 行
- **AND** 日志出现 ERROR 指明该 `toolCallId` 未完成

#### Scenario: 幂等

- **WHEN** 同一回合内收口被触发多次
- **THEN** 每个未完成调用只被补一次

### Requirement: 日志根目录稳定且读写一致

运行时日志与 per-session 日志 SHALL 写入 `~/.agent-demo/logs`，SHALL NOT 依赖进程工作目录。日志查看接口 SHALL 读取同一目录。

#### Scenario: 不同工作目录写同一处

- **WHEN** 分别从仓库根目录与模块目录启动应用
- **THEN** 两次的日志都落在 `~/.agent-demo/logs/`

#### Scenario: 查看接口与写入一致

- **WHEN** 查询日志查看接口列出的会话
- **THEN** 列出的是实际被写入的会话日志

### Requirement: 测试日志与运行时日志隔离

测试运行 SHALL NOT 向运行时日志文件（`~/.agent-demo/logs/app.log`）写入内容。

#### Scenario: 跑测试不污染运行时日志

- **WHEN** 执行一次完整测试
- **THEN** 运行时 `app.log` 不新增来自测试的行

### Requirement: 会话记录完整性可诊断

系统 SHALL 提供诊断入口，报告存在 `tool_calls` 与 `tool_result` 不配对的会话及其缺失的 `toolCallId`。

#### Scenario: 报告不完整会话

- **WHEN** 某会话存档存在悬挂 `tool_calls`
- **THEN** 诊断结果包含该会话 id 与缺失的 `toolCallId` 列表

#### Scenario: 全部完整时报告为空

- **WHEN** 所有会话存档均满足配对不变式
- **THEN** 诊断结果中不完整会话列表为空

#### Scenario: 会话恢复时的修复被记录

- **WHEN** 恢复一个含悬挂 `tool_calls` 的存档并触发自动修复
- **THEN** 日志出现可检索的 WARN，含 `sessionId` 与被补的 `toolCallId` 列表

