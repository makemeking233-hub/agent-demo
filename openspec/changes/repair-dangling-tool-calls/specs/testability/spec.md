## ADDED Requirements

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
