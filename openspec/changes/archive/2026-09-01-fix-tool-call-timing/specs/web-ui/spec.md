# web-ui Specification (delta)

> 本文件是 `fix-tool-call-timing` 的 delta spec。在 archive 时合并到 `openspec/specs/web-ui/spec.md`。

## ADDED Requirements

### Requirement: 同轮混合输出的事件因果序

系统 SHALL 在一个 assistant 轮次同时包含文本与工具调用时，先推送 `tool_call_start`，再推送该轮的 `message_delta`（文本），使客户端按"工具调用 → 文本依赖"的因果顺序渲染。

#### Scenario: 工具调用先于文本推送

- **WHEN** 一个 assistant 轮次既包含文本（`content` 非空）又包含工具调用（`toolCalls` 非空）
- **THEN** 服务端先为该轮推送 `tool_call_start`（每个工具调用各一条），再推送 `message_delta`（文本）
- **AND** 客户端按该顺序渲染，工具调用卡片显示在文本之前

#### Scenario: 仅工具调用时无文本

- **WHEN** 一个 assistant 轮次只有工具调用、无文本
- **THEN** 服务端只推送 `tool_call_start`（不推送空的 `message_delta`），后续工具执行后推送对应 `tool_call_end`

#### Scenario: 仅文本时不受影响

- **WHEN** 一个 assistant 轮次只有文本、无工具调用
- **THEN** 服务端照常推送 `message_delta`（文本），无 `tool_call_start`
