## Why

agent-demo v0.1 的「SSE 流式」实测是**假流式**，且有两条彼此独立的成因：

- **HTTP 层**：`OpenAiCompatibleProvider.streamChat` 用 `bodyToMono(String.class)` 等上游整个 HTTP 响应收完才返回，TTFT 等于总响应时长。实测（WireMock 延迟 800ms）首 emit 发生在 **837ms**。
- **编排层**：`SseSessionLogSink` 只在 `onAssistant`（即 `AgentLoop` 的 `collectList()` 之后）整段 emit 正文，所以即使 HTTP 层逐 chunk 到达，前端看到的正文仍是一次性出现。

本次变更让首 token 在 300ms 内可见，并让正文逐 token 实时渲染。

## What Changes

- **`OpenAiCompatibleProvider.streamChat` 改真流式**：`bodyToMono(String)` 改为 `bodyToFlux(DataBuffer.class)`，并用新增的 `SseLineBuffer` 重组跨 TCP 帧的半截行。
- **`SessionLogSink` 加 `default void onTextDelta(String text) {}`**，与既有 `onThinkingDelta` 对称。
- **`AgentLoop.printChunk` 正文转发 sink**：`printer.onTextDelta(t.text())` 之外新增 `sink.onTextDelta(t.text())`，在 `collectList()` 之前逐 chunk 透传。
- **`SseSessionLogSink.onTextDelta` 实现**：每个增量立即 emit `message_delta(text)`；`onAssistant` 判重，已推过增量就不再整段重发，无增量时兜底发全文。
- **`CompositeSessionLogSink` 补转发** `onTextDelta` 与 `onThinkingDelta`（附带修复：web 正常路径下 thinking 增量此前被接口默认实现吞掉）。
- **测试**：`SseLineBufferTest`、`OpenAiCompatibleProviderE2ETest` TTFT 用例、`ChatStreamServiceStreamingTest`、`ChatStreamServiceConcurrencyTest`。

**明确不做**（原提案含此两项，经实测证伪后移除，详见 design.md D2 / D3）：

- 不改 sink 模式（`replay().all()` 保留）——thinking 增量已通过同一 sink 逐 token 到达前端，证明 replay 不延迟事件；且 replay 是 spec §resume / Last-Event-ID 重连的基础。
- 不改 `.block()` —— 它阻塞的是 executor 线程而非 HTTP 线程，与 TTFT 无关。

无破坏性变更：SSE 协议契约（事件类型 / 字段）不变，前端零改动。

## Capabilities

### New Capabilities

无（`web-ui` 现有能力升级）。

### Modified Capabilities

- `web-ui`：新增 6 个 Requirement——HTTP 层真流式、跨 TCP 帧的 SSE 行重组、正文增量透传、正文不重复推送、增量回调经复合 sink 转发、多流并发隔离。

## Impact

- **agent-core（3 个文件）**：`OpenAiCompatibleProvider.java`（bodyToFlux）、`SseLineBuffer.java`（新增）、`SessionLogSink.java`、`CompositeSessionLogSink.java`、`AgentLoop.java`
- **agent-web（1 个文件）**：`SseSessionLogSink.java`
- **测试（4 个文件）**：`SseLineBufferTest`（新增）、`OpenAiCompatibleProviderE2ETest`（改写 TTFT 用例）、`ChatStreamServiceStreamingTest`（新增）、`ChatStreamServiceConcurrencyTest`（新增）、`CompositeSessionLogSinkTest`（补用例）
- **前端**：零改动
- **文档**：`docs/streaming-architecture.md` 新增；`docs/test-agent-demo/2026-09-04-true-streaming/` 四件套
- **影响范围**：约 120 行代码改动加约 250 行测试
