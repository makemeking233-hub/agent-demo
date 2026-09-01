## Context

`SseSessionLogSink.onAssistant(assistant, thinking)` 把一次 assistant 轮次的文本、思考、工具调用转换成 SSE 事件。当前顺序固定为：`message_delta`(text) → `message_delta`(thinking) → `tool_call_start`(each)。对"同轮既说话又调工具"的轮次，客户端显示为"文本先、工具后"，与因果相反。

## Goals / Non-Goals

**Goals:**
- 同轮混合输出时，`tool_call_start` 先于文本 `message_delta` 推送。
- 不改事件载荷/类型/协议，仅调顺序。

**Non-Goals:**
- 不流式化 `AgentLoop` 的 chunk 推送（第 1 层流式重构，另 change）。
- 不改前端内联逻辑（已就位，仅受益于顺序改善）。

## Decisions

**D1: `SseSessionLogSink.onAssistant` 重排 emit 顺序。**
- 先 emit `tool_call_start`（每个 toolCall 一条），再 emit `message_delta`(text)（若非空），最后 thinking。
- 保序：工具调用 → 文本 → 思考。
- 备选：保持现序。否决——违反因果直觉。

**D2: 仅工具调用/仅文本分支保持原语义。**
- 仅有工具调用 → 只发 start；仅有文本 → 只发 delta。均已满足。

## Risks / Trade-offs

- [同轮带工具调用时文本晚于工具 start] → 符合"先调工具再解释"的真实因果，前端内联后显示正确。
- [thinking 时序] → 放最后，不影响 text/tool 主序。

## Migration Plan

1. `SseSessionLogSink.onAssistant` 重排顺序。
2. 跑 agent-web test（`SseSessionLogSinkTest` 保持通过）。
3. `mvn verify -DskipNpm=true` 全绿。
4. 前端 vitest 保持全绿。

## Open Questions

- 无。
