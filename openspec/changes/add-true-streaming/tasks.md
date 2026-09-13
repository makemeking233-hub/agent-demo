## 1. Provider HTTP 层（agent-core）

- [x] 1.1 `OpenAiCompatibleProvider.streamChat` 改 `bodyToFlux(DataBuffer.class)` + 跨帧行重组（`SseLineBuffer`）
- [x] 1.2 `SseLineBufferTest` 新增（7 例：跨帧拼行、逐字符滴灌、残留半行、订阅隔离）+ `OpenAiCompatibleProviderE2ETest` 改写 TTFT 用例（`withChunkedDribbleDelay`）

## 2. ChatStreamService sink 模式与订阅方式（**经证据否决，见 design.md D2/D3**）

- [x] 2.1 ~~`replay().all()` → `unicast().onBackpressureBuffer()`~~ **不做**：thinking 增量今天已通过同一 replay sink 逐 token 到达前端，证明 replay 不延迟事件；而 replay 是 spec §resume / Last-Event-ID 重连的基础，改动会破坏该语义
- [x] 2.2 ~~`.block()` → `.subscribe()`~~ **不做**：`.block()` 阻塞的是 executor 线程而非 HTTP handler 线程，与 TTFT 无关，属独立的线程占用优化，不在本 change 范围
- [x] 2.3 端到端增量验证改为 `ChatStreamServiceStreamingTest`：订阅 SSE 后逐 chunk 断言首 token 早于 `message_stop`

## 3. SessionLogSink 正文增量（agent-core + agent-web）

- [x] 3.1 `SessionLogSink` 接口加 `default void onTextDelta(String text) {}`
- [x] 3.2 `SseSessionLogSink.onTextDelta` → emit `MessageDelta("text", text)`，并让 `onAssistant` 判重不重复推送
- [x] 3.3 `AgentLoop.printChunk` text 走 `sink.onTextDelta(t.text())`（保留 printer 的 stdout 输出）
- [x] 3.4 `CompositeSessionLogSink` 补转发 `onTextDelta` 与 `onThinkingDelta`（此前 thinking 增量在 web 正常路径被默认实现吞掉）

## 4. 并发回归测试（agent-web）

- [x] 4.1 `ChatStreamServiceConcurrencyTest`：4 条流同时 create + 订阅 + start，各自只收到自身增量

## 5. 文档 + 收尾

- [x] 5.1 `docs/streaming-architecture.md` 新增（v0.1 假流式根因 + 本次改动 + 两条被否决的改动及理由）
- [x] 5.2 四件套 `docs/test-agent-demo/2026-09-04-true-streaming/{test-design,test-cases,test-report,test-review}.md` + 更新 `test-guide.md`
- [x] 5.3 `mvn -o -pl agent-core,agent-web verify` + `vitest run` + `openspec validate add-true-streaming --strict` + archive + commit + push

## 6. 任务范围修订说明

原 tasks.md 的 2.1 / 2.2 基于「replay sink 导致假流式」与「.block 导致 TTFT 高」的判断，实测均不成立：

- 真 TTFT 瓶颈只有一处——`bodyToMono(String.class)` 等整个 HTTP body 收完才返回（v0.1 实测 TTFT 837ms ≈ 上游延迟 800ms，改 `bodyToFlux` 后同样场景 TTFT 250ms）。
- 前端「一坨出来」的直接原因是 `SseSessionLogSink.onAssistant` 在 `collectList()` 之后才整段 emit 正文，与 sink 模式无关。

因此保留 replay sink 与 `.block()`，改动收敛为 4 处（1.1 / 3.1 / 3.2+3.4 / 3.3）。
