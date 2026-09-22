## MODIFIED Requirements

### Requirement: 恢复与请求两条路径都自愈

会话恢复（`SessionResumeLoader`）与每次构造请求（`AgentLoop`）SHALL 都应用**双向**配对修复：正向（assistant 的 `tool_calls` 缺结果时在其结果块末尾补合成错误结果）与反向（tool 结果缺前置 `assistant.tool_calls` 时在其前补合成 assistant 骨架）。由此，被污染的存档在恢复后即可继续对话，且同一进程内被打断的轮次、被裁剪过的历史都不会污染下一轮。

#### Scenario: 已污染存档恢复后可用

- **WHEN** 恢复一个含悬挂 `tool_calls` 的存档
- **THEN** 恢复出的消息列表满足配对约束
- **AND** 存档文件本身不被改写

#### Scenario: 进程内下一轮不被污染

- **WHEN** 内存历史中存在悬挂 `tool_calls`
- **THEN** 构造出的请求消息列表已补齐对应结果

#### Scenario: 请求路径同时修复正向与反向

- **WHEN** 内存历史中既有悬挂 `tool_calls`，又有无前置 `tool_calls` 的孤儿 `tool_result`
- **THEN** 构造出的请求消息列表对两者都已补齐
- **AND** 该列表满足双向配对约束

#### Scenario: 请求路径修复幂等

- **WHEN** 对同一内存历史连续构造两次请求
- **THEN** 两次得到的消息列表长度一致（第二次不新增任何合成消息）

#### Scenario: 内存历史不被修复改写

- **WHEN** 内存历史含悬挂 `tool_calls`
- **THEN** 修复只作用于将要发送的消息列表
- **AND** 内存历史本身的消息条数不变

## ADDED Requirements

### Requirement: 裁剪不破坏配对不变式

`SessionResumeLoader.snip` 按 token 上限从头部裁剪历史时 SHALL 以**配对组**为最小丢弃单位：SHALL NOT 丢弃某个 assistant 的 `tool_calls` 却保留其对应的 `tool_result`。裁剪结果的第一个非 system 消息 SHALL NOT 是 `ToolResult`。

#### Scenario: 裁剪点落在配对组中间

- **WHEN** 裁剪边界恰好落在 `assistant(tool_calls=[c1,c2])` 与其后续 `tool_result(c1)`、`tool_result(c2)` 之间
- **THEN** 该 assistant 与其全部结果一起被丢弃，或一起被保留
- **AND** 裁剪后的列表中没有无前置 `tool_calls` 的 `tool_result`

#### Scenario: 对齐后仍满足上限

- **WHEN** 裁剪点因对齐而后移
- **THEN** 保留的消息数不多于对齐前的保留数（对齐只减少保留量，不会重新超限）

#### Scenario: 未超限时不裁剪

- **WHEN** 消息总量不超过上限
- **THEN** 原样返回，不插入任何 summary 消息

#### Scenario: 裁剪后头部有压缩提示

- **WHEN** 发生了裁剪
- **THEN** 保留列表的首条为以 `[RESUMED]` 开头的 system 消息
