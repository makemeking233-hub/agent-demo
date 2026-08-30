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

