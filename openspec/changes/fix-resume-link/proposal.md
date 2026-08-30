## Why

`/resume` 链路（`SlashCommand.doResume`，agent-core）在把 `sessions/*.jsonl` 存档转回 `List<Message>` 时丢失关键信息：

1. **assistant 丢失 toolCalls**：`new Message.Assistant(content, List.of())` 硬编码空工具调用骨架。
2. **tool_result 丢失 toolCallId/ isError**：`new Message.ToolResult("", content, false)` 硬编码空 callId 与恒 false。
3. **meta token 丢失**：token 累计信息未恢复，resume 后 `/history` 费用显示归零。
4. **snip 缺失**：超长会话全量加载，可能撑爆上下文。
5. **parallel tool_result 孤儿**：模型先返回多个 tool_call 再多个 tool_result，resume 时关联丢失。

这导致 resume 后模型无法理解"此前调用了什么工具/拿到什么结果"，链路不可用。

## What Changes

- **恢复 toolCalls / toolCallId / isError**：`doResume` 从 `SessionEntry.extras()` 正确解析 assistant 的 `toolCalls` 与 tool_result 的 `toolCallId`/`isError`，还原为 `Message.Assistant` / `Message.ToolResult`。
- **恢复 meta token**：从 `meta` 条目读回累计 token，resume 后 `/history` 费用正确。
- **snip 裁剪**：resume 后若消息 token 总量超过上限，按"保留最新 X 轮 + 前面压缩为一条系统摘要"裁剪，控制上下文。
- **parallel tool_result 孤儿处理**：为无对应 assistant.tool_calls 的孤儿 tool_result 注入合成工具调用骨架，保证消息序列满足模型出场顺序约束。
- **统一转 Message 逻辑**：把 `doResume` 的转换抽成可测工具（如 `SessionResumeLoader`），供 `SessionReplay`（事件流）复用。

## Capabilities

### New Capabilities
- `cli`（已有）：修改 `/resume` 的命令行为（MODIFIED，非新增 capability）。本次作为 `cli` cap 的 MODIFIED Requirements。

### Modified Capabilities
- `cli`：修改 `/resume` 的需求，使其恢复完整工具调用信息、token 统计、并支持 snip 裁剪与并行孤儿处理。

## Impact

- 受影响类：`agent-core/.../cli/SlashCommand.java`（`doResume`）；新增 `agent-core/.../session/SessionResumeLoader.java`。
- 受影响装配：`agent-core/.../cli/ChatCommand.java`（`/resume` 回调传入 token 估算器/裁剪上限）。
- 测试：新增 `SessionResumeLoaderTest`；扩充 `SlashCommandTest`。
- 无外部依赖变更。
- 无破坏性 API 变更（新增工具类 + 重构内部逻辑）。
