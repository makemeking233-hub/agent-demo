## Why

agent-web 前端工具调用卡片曾被聚集到 assistant 消息下方（先显示解释文本、后显示工具调用），与"模型先调工具、再基于结果说话"的因果顺序相反。此前 `polish-tool-call-display` 已把工具卡片内联到 assistant 消息（归属正确），但**事件推送顺序**仍是"文本先、工具后"（`SseSessionLogSink.onAssistant` 固定先发 `message_delta` 再发 `tool_call_start`）。本 change 调整该顺序，让工具调用先于文本推送，客户端按真实因果显示。

## What Changes

- 调整 `SseSessionLogSink.onAssistant` 的 emit 顺序：先发 `tool_call_start`（若有工具调用），再发 `message_delta`（文本），后发 `thinking`。
- 使前端收到的事件顺序为"工具调用 start → 文本"，工具卡片显示在解释文本之前（或在其所属 assistant 消息内靠前），更贴近实际因果。

## Capabilities

### New Capabilities
- （无）

### Modified Capabilities
- `web-ui`：修改"流式聊天（SSE）"中事件按因果序到达的行为——对同轮既含文本又含工具调用的 assistant，`tool_call_start` 先于 `message_delta` 推送。

## Impact

- 受影响：`agent-web/.../stream/SseSessionLogSink.java`（`onAssistant` 方法顺序）。
- 测试：`SseSessionLogSinkTest`（不依赖顺序，保持通过）。
- 前端：无需改（内联逻辑已就位），仅事件到达顺序改善显示。
- 无 API/协议形状变更（事件类型、载荷不变，仅顺序调整）。
