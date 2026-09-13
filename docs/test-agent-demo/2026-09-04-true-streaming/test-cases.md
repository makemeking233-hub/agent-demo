# test-cases — add-true-streaming

> 批次：`2026-09-04-true-streaming`
> 用例编号规则：`TS-<层><序号>`（L1 单元 / L2 Provider 集成 / L3 编排 / L4 并发 / C 辅助）
> 优先级：P0 必测（本 change 的核心验收）、P1 重要、P2 补充

---

## 1. 新增与改动用例

### 1.1 `SseLineBufferTest`（L1 单元，7 例）

| 编号 | 用例名 | 优先级 | 前置 | 步骤 | 预期 |
|:----:|--------|:------:|------|------|------|
| TS-L1-01 | `splitsCompleteLinesWithinSingleBuffer` | P0 | 单个 buffer 内容 `alpha\nbeta\n` | 调 `lines()` 收集 | 输出 `["alpha","beta"]` |
| TS-L1-02 | `joinsLineSplitAcrossTwoBuffers` | P0 | 两个 buffer，切点落在 `data: {...}` 行中间 | 调 `lines()` 收集 | 拼成完整一行加一个空行，不出现半截 JSON |
| TS-L1-03 | `joinsLineSplitAtEverySingleCharacter` | P0 | 把 `data: {"a":1}\ndata: [DONE]\n` 逐字符拆成 29 个 buffer | 调 `lines()` 收集 | `["data: {\"a\":1}", "data: [DONE]"]` |
| TS-L1-04 | `flushesTrailingPartialLineOnCompletion` | P1 | 源只有 `data: {"a":1}`，无收尾换行 | 调 `lines()` 收集 | 仍输出该行，不静默丢弃 |
| TS-L1-05 | `keepsCrFromCrlfForCallerToTrim` | P2 | 行为 `data: x\r\n` | 调 `lines()` 收集 | 输出 `"data: x\r"`（`\r` 留给调用方 trim） |
| TS-L1-06 | `bufferStateIsPerSubscription` | P1 | 同一 `Flux<String>` 订阅两次 | 连续两次 `collectList` | 两次都得到 `["a","b"]`，无跨订阅残留 |
| TS-L1-07 | `emptySourceCompletesWithoutEmitting` | P2 | 空源 | 调 `lines()` 收集 | 输出为空且正常 complete |

### 1.2 `OpenAiCompatibleProviderE2ETest`（L2 Provider 集成，3 例）

| 编号 | 用例名 | 优先级 | 前置 | 步骤 | 预期 |
|:----:|--------|:------:|------|------|------|
| TS-L2-01 | `streamChatEmitsFirstChunkLongBeforeResponseCompletes` | P0 | WireMock `withChunkedDribbleDelay(5, 500)`，响应体含 3 个 `data:` 事件加 `[DONE]` | 记录每个 chunk 到达时刻后收集全部 | 3 个 `TextDelta`；TTFT 小于 300ms；整体耗时比 TTFT 大 200ms 以上 |
| TS-L2-02 | `streamChatDoesNotMergeAllTextIntoSingleChunk` | P0 | WireMock 单段响应 | 收集全部 chunk | `TextDelta` 数量为 1（不合并、不丢失） |
| TS-L2-03 | `streamChatEmitsTextDeltaForEachSseChunk_separately` | P1 | WireMock 3 段独立 data | `StepVerifier` 逐个断言 | 依次拿到 `你`、`好`、`！` 三个 `TextDelta` |

> TS-L2-01 取代原 `streamChatEmitsAfterFullResponse_receivedNotDuringResponse`。原用例用 `withFixedDelay(800)`，该 stub 会把整个响应体延迟 800ms 才发，真流式也只能等到 800ms，因此**永远无法通过**，属测试设计错误而非实现缺陷。

### 1.3 `ChatStreamServiceStreamingTest`（L3 编排，4 例）

假 provider 每 150ms 吐一个 chunk（`A`、`B`、`C`、`Finished`），订阅真实 `ChatStreamService` 的 SSE 并解析 JSON 载荷。

| 编号 | 用例名 | 优先级 | 前置 | 步骤 | 预期 |
|:----:|--------|:------:|------|------|------|
| TS-L3-01 | `textDeltasReachSseIncrementallyAndAreNotRepeatedAsWholeParagraph` | P0 | 已 `create` 一条流 | 先订阅 SSE，再 `start`，等流结束 | 正文载荷恰为 `["A","B","C"]`（无重复的 `"ABC"`）；首个正文到达时刻比 `message_stop` 早 200ms 以上 |
| TS-L3-02 | `onAssistantStillEmitsWholeTextWhenNothingWasStreamed` | P0 | 整轮无任何 `onTextDelta` | 直接 `onAssistant("整段文本")` | SSE 收到恰一条正文载荷 `"整段文本"` |
| TS-L3-03 | `onAssistantDoesNotRepeatTextAlreadyStreamedIncrementally` | P0 | 先 `onTextDelta("你")`、`onTextDelta("好")` | 再 `onAssistant(全文 "你好")` | 正文载荷恰为 `["你","好"]`，不出现 `"你好"` |
| TS-L3-04 | `textStreamedFlagResetsPerModelIteration` | P1 | 第一轮推过增量；第二轮无增量 | 连续两次 `onAssistant` | 正文载荷为 `["第一轮","第二轮全文"]`（判重逐轮独立） |

### 1.4 `ChatStreamServiceConcurrencyTest`（L4 并发，1 例）

| 编号 | 用例名 | 优先级 | 前置 | 步骤 | 预期 |
|:----:|--------|:------:|------|------|------|
| TS-L4-01 | `fourConcurrentStreamsDoNotInterleaveEachOther` | P0 | 4 个会话各配一个假 provider，各自吐 `S<n>-a`、`S<n>-b` | 4 条流全部 `create` 并订阅后再全部 `start`，等全部结束 | 每条流的正文载荷恰为其自身的两段，顺序正确、互不串台 |

### 1.5 `CompositeSessionLogSinkTest`（C 辅助，新增 1 例）

| 编号 | 用例名 | 优先级 | 前置 | 步骤 | 预期 |
|:----:|--------|:------:|------|------|------|
| TS-C-01 | `forwardsIncrementalDeltas` | P0 | 复合 sink 挂两个记录型子 sink | 依次调 `onThinkingDelta("想")`、`onTextDelta("你")`、`onTextDelta("好")` | 两个子 sink 都收到这 3 次回调 |

---

## 2. 回归用例

| 编号 | 范围 | 命令 | 用例数 |
|:----:|------|------|:------:|
| TS-R-01 | agent-core 全量 | `mvn -o -pl agent-core verify` | 353 |
| TS-R-02 | agent-web 全量（排除 e2e 包） | `mvn -o -pl agent-web verify -Dsurefire.excludes=**/e2e/**` | 158 |
| TS-R-03 | 前端 | `npx vitest run` | 见 `test-report.md` |

---

## 3. 落地情况

| 测试类 | 用例数 | 落地 | 结果 |
|--------|:------:|:----:|:----:|
| `SseLineBufferTest` | 7 | ✅ | ✅ 全绿 |
| `OpenAiCompatibleProviderE2ETest` | 3 | ✅ | ✅ 全绿 |
| `ChatStreamServiceStreamingTest` | 4 | ✅ | ✅ 全绿 |
| `ChatStreamServiceConcurrencyTest` | 1 | ✅ | ✅ 全绿 |
| `CompositeSessionLogSinkTest` | 5（新增 1） | ✅ | ✅ 全绿 |

未落地用例：无。

---

## 4. 已知未覆盖场景（非本次范围）

| 场景 | 原因 |
|------|------|
| 真实 DeepSeek / MiniMax 上游的端到端流式 | 需要真实 API key 与配额；协议层已由 WireMock 覆盖 |
| 浏览器端 EventSource 的实际渲染帧率 | 前端零改动，SSE 契约未变 |
| `UiLayoutE2ETest` 4 条 Selenium 用例 | 既有失败（选择器 `button[aria-label='新建会话']` 已被工作区改版移除），与本次无关，详见 `test-report.md` |
