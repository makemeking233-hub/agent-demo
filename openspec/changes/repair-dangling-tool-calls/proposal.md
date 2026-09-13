## Why

`assistant.tool_calls` 必须紧跟每个 `tool_call_id` 对应的 `tool` 消息，否则 DeepSeek 直接 400：

```text
An assistant message with 'tool_calls' must be followed by tool messages responding to
each 'tool_call_id'. (insufficient tool messages following tool_calls message)
```

2026-09-13 的实际事故链（已逐条核对会话存档确认）：

1. 助手发起 `EditFile`（编辑 `~/.agent-demo/memory/MEMORY.md`）→ `assistant(toolCalls=[call_00_THugpr9Kry…])` 已经**先入 history 并落盘**（消息顺序约束要求先 assistant 再 tool）。
2. 该工具解析入参时 `EditFileTool$Input` 类缺失 → `NoClassDefFoundError`（属 `LinkageError`）。
3. Reactor 的 `Exceptions.throwIfFatal` 把它原样 rethrow，绕过 `onErrorResume`（已由 change `harden-tool-error-boundary` 修复），**整轮被打断** → 对应的 `tool_result` 永远没有落盘。
4. 存档从此停在「有 tool_calls、无 tool_result」的中间态。
5. 此后**该会话每一轮**都会重放这段历史 → 每次都 400 → 用户侧表现为"一直报错、无法继续对话"。

现有代码只处理了**反向**的孤儿情况：`SessionResumeLoader.injectOrphanSkeletons` 会给「有 `tool_result` 但没有对应 `assistant.tool_calls`」的条目补合成 assistant 骨架。**反过来的「有 `tool_calls` 但没有 `tool_result`」没有任何处理**——这正是本 bug。

扫描本机 5 个会话存档，全部存在该悬挂状态，说明这不是一次性意外，而是**缺少不变式保障**。

## What Changes

- 新增 `com.example.agent.core.ToolCallPairing.repair(List<Message>)`：为每个「`assistant.tool_calls` 之后缺少对应 `tool_result`」的调用，**就地按序补一条合成错误 `tool_result`**（`isError=true`），插在该 assistant 的既有结果之后、下一条非 tool 消息之前。
- `SessionResumeLoader.toMessages` 调用它 → **恢复历史时自愈**，已污染的存档无需人工修文件。
- `AgentLoop.toRequest` 调用它 → **同一进程内**某轮被打断后，下一轮的请求也不会带着空洞（不必等重启）。
- 幂等：已配对的正常历史零改动。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `testability`：新增「历史消息 tool_calls / tool_result 配对不变式」相关 Requirement。

## Impact

- **agent-core（3 个文件）**：新增 `ToolCallPairing.java`；`SessionResumeLoader.java`、`AgentLoop.java` 各接一行
- **测试**：新增 `ToolCallPairingTest`；`SessionResumeLoaderTest` 补悬挂场景用例
- **数据**：无需人工修复既有存档（恢复时自愈；存档文件保持 append-only 不被改写）
- **文档**：`docs/design/design.md` §11.5 补该不变式说明
