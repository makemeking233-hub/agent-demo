## Why

每次 Sidebar 刷新会触发 `SessionResumeLoader.toMessages()`，对每个历史 session 调 `ToolCallPairing.danglingCallIds()`，发现不配对就 `log.warn("会话存档存在 tool_calls/tool_result 不配对...")`。同一 session 多次加载会产生重复告警，污染日志。

期望：同一 sessionId 仅**首次**发现不配对时打 INFO（含 sessionId + toolCallId 列表）；后续加载同一 session 静默跳过。仍是 WARN 级别的不合理行为（修复本身是正常的），降级为 INFO + dedupe。

## What Changes

- `SessionResumeLoader.toMessages()` 增加 `Set<String> seenWarnedSessions` dedupe
- WARN → INFO（"首次发现历史不配对"，无操作性）
- 同一 sessionId 仅打一次；用 ConcurrentHashMap.newKeySet() 保证线程安全

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `tool-execution`: tool pair repair 日志从 WARN + 重复 降级为 INFO + dedupe

## Impact

- 后端：`SessionResumeLoader.toMessages()` 改造
- 测试：增加 dedupe 验证测试
- 日志：噪音显著降低；功能不变
