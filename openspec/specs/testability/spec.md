# testability Specification

## Purpose

可测性：日志驱动测试（golden 事件断言）、会话回放能力、日志模块可测试性。
## Requirements
### Requirement: 事件断言工具

系统 SHALL 提供测试可用的会话事件断言工具：读取 `session.jsonl` 后可按 `type` 过滤、按 `seq` 顺序断言、对指定字段做值断言；断言前 SHALL 对时间戳、`seq`、uuid 等易变字段做归一化（替换为占位符），保证 E2E 断言稳定。

#### Scenario: 断言事件类型序列

- **WHEN** 测试跑完一轮对话（含一次工具调用）后调用断言工具
- **THEN** 断言工具能按序匹配事件类型序列 `[turn/start, context/snapshot, user/message, assistant/message, tool/call, tool/result, turn/end]`，中间夹带的 `system/*` 事件可被忽略

#### Scenario: 断言事件字段

- **WHEN** 测试断言某条 `tool/call` 事件
- **THEN** 断言工具能验证 `name` 等于期望工具名、`arguments` 包含期望参数片段

#### Scenario: 归一化易变字段

- **WHEN** 两次独立运行的 golden 事件对比
- **THEN** 断言工具将 `timestamp`、`seq`、`callId` 归一化后，两次运行的事件类型序列与关键字段一致

### Requirement: 会话回放

系统 SHALL 提供只读回放工具：给定 `session.jsonl` 路径，按事件顺序重建 `MessageHistory`（`user/message` → `assistant/message`（含 toolCalls）→ `tool/result`），跳过 `context/snapshot` 与 `system/*` 事件；重建结果 SHALL 与原始对话的消息顺序一致。

#### Scenario: 单轮对话重建

- **WHEN** 回放一个含 `user` → `assistant` 的会话事件流
- **THEN** 重建的 `MessageHistory` 含 2 条消息，顺序为 user → assistant，内容与事件一致

#### Scenario: 工具调用轮重建

- **WHEN** 回放一个含 `user` → `assistant(toolCalls)` → `tool/result` → `assistant` 的会话事件流
- **THEN** 重建的 `MessageHistory` 消息顺序与真实对话一致，`assistant` 消息携带完整 `toolCalls`，`tool/result` 的 `toolCallId` 与对应 `toolCalls` 匹配

#### Scenario: 未知事件类型被跳过

- **WHEN** 事件流含未知/未来类型的事件行
- **THEN** 回放工具跳过该行不报错，其余事件正常重建

### Requirement: 日志模块可测试性

系统 SHALL 保证日志模块自身可测且故障隔离：日志写入失败（IO 异常、格式化异常）SHALL 只记录 WARN 并继续，绝不向调用方抛出；`logging.enabled=false` 时 SHALL 不产生任何会话日志文件且主流程零副作用；新增日志代码（Redactor、保留策略清理、事件写入扩展）SHALL 有单元测试覆盖并满足 jacoco 门禁。

#### Scenario: 日志写失败不打断对话

- **WHEN** `session.jsonl` 写入抛 IO 异常（如磁盘满）
- **THEN** 对话正常继续，仅输出 WARN 日志

#### Scenario: 关闭日志零副作用

- **WHEN** `logging.enabled=false`
- **THEN** 不创建 `logs/sessions/` 下任何目录或文件，CLI 行为与未接日志时完全一致

#### Scenario: 脱敏与清理有单测

- **WHEN** 运行 `mvn test`
- **THEN** `Redactor` 与保留策略清理器的单元测试全部通过，且覆盖分支满足 jacoco BRANCH≥70%

### Requirement: 悬挂 tool_calls 自愈

对每个含 `tool_calls` 的 assistant 消息，若其中某个 `tool_call_id` 在其后、下一条非 tool 消息之前没有对应的 `tool_result`，系统 SHALL 在该位置补一条合成错误 `tool_result`（`isError=true`），使消息序列满足 `tool_calls` 与 `tool` 消息的配对约束。

#### Scenario: 完全无结果

- **WHEN** 历史中某 assistant 带 `tool_calls=[c1]`，其后紧跟一条 user 消息
- **THEN** 在 assistant 与 user 之间插入一条 `tool_result(toolCallId=c1, isError=true)`
- **AND** 该结果的内容说明该工具调用未完成

#### Scenario: 部分结果

- **WHEN** 历史中某 assistant 带 `tool_calls=[c1,c2]`，其后只有 `tool_result(c1)`
- **THEN** 为 `c2` 补一条合成错误结果
- **AND** 补齐顺序与 `tool_calls` 顺序一致

#### Scenario: 已配对的历史零改动

- **WHEN** 每个 `tool_calls` 的 id 都有紧随其后的对应 `tool_result`
- **THEN** 消息列表不被修改

#### Scenario: 幂等

- **WHEN** 对同一消息列表连续执行两次修复
- **THEN** 第二次不产生任何新增消息

#### Scenario: 无 tool_calls 的 assistant 不受影响

- **WHEN** assistant 消息的 `tool_calls` 为空
- **THEN** 不对其做任何补齐

### Requirement: 恢复与请求两条路径都自愈

会话恢复（`SessionResumeLoader`）与每次构造请求（`AgentLoop`）SHALL 都应用该配对修复，使被污染的存档在恢复后即可继续对话，且同一进程内被打断的轮次不会污染下一轮。

#### Scenario: 已污染存档恢复后可用

- **WHEN** 恢复一个含悬挂 `tool_calls` 的存档
- **THEN** 恢复出的消息列表满足配对约束
- **AND** 存档文件本身不被改写

#### Scenario: 进程内下一轮不被污染

- **WHEN** 内存历史中存在悬挂 `tool_calls`
- **THEN** 构造出的请求消息列表已补齐对应结果

