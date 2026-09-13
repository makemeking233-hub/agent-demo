## ADDED Requirements

### Requirement: HTTP 层真流式

`OpenAiCompatibleProvider.streamChat` SHALL 用 `bodyToFlux(DataBuffer.class)` 替代 `bodyToMono(String.class)`，边收边解析 SSE 行并逐条 emit，使首 token 到达延迟（TTFT）小于 300ms。

#### Scenario: 上游分段吐字时首 emit 提前

- **WHEN** 上游把 3 个 SSE 事件分 5 段、共 500ms 陆续送达（首段约 100ms）
- **THEN** `streamChat(req)` 在 300ms 内 emit 第一个 `TextDelta`
- **AND** 3 个 `TextDelta` 全部被解析出来且顺序正确
- **AND** 首 emit 时刻比整体完成时刻早 200ms 以上

#### Scenario: SSE 块级流式

- **WHEN** 上游 SSE 响应有 3 个独立 data 块
- **THEN** Provider emit 3 个独立 `TextDelta` 事件（不合并为 1 个）

### Requirement: 跨 TCP 帧的 SSE 行重组

Provider SHALL 在按行解析 SSE 前重组跨 `DataBuffer` 的半截行，不得把被 TCP 切开的 `data:` 行交给解析器。

#### Scenario: 一行被切成两个 buffer

- **WHEN** `data: {"a":1}` 的前半段与后半段落在两个 `DataBuffer`
- **THEN** 解析前拼成完整一行并只 emit 一次

#### Scenario: 上游结束时残留半行

- **WHEN** 源结束时最后一行没有以换行符收尾
- **THEN** 该行仍被 emit，不静默丢弃

#### Scenario: 行缓冲按订阅隔离

- **WHEN** 对同一行重组流订阅两次
- **THEN** 两次都拿到完整、互不污染的同一组行

### Requirement: 正文增量透传

`SessionLogSink` SHALL 提供 `default void onTextDelta(String text) {}`；`AgentLoop` SHALL 在 `collectList()` 之前把每个 `StreamChunk.TextDelta` 实时转发给 sink；`SseSessionLogSink` SHALL 为每个增量立即 emit 一条 `message_delta(text)`。

#### Scenario: 逐 token 推送

- **WHEN** 模型分 3 次吐出 `A` `B` `C`
- **THEN** SSE 依次收到 3 条 `message_delta(text)`，content 分别为 `A` `B` `C`
- **AND** 首条到达时刻明显早于 `message_stop`

#### Scenario: 老 sink 兼容

- **WHEN** 老 `SessionLogSink` 实现未覆盖 `onTextDelta`
- **THEN** 编译通过，调用落到默认空实现

### Requirement: 正文不重复推送

已逐 token 推送过正文的轮次，`SseSessionLogSink.onAssistant` SHALL 不再整段重发同一段文本；整轮没有任何增量时 SHALL 兜底发出全文。判重状态 SHALL 每轮模型输出重置。

#### Scenario: 不重复

- **WHEN** 一轮中先 `onTextDelta("你")`、`onTextDelta("好")`，随后 `onAssistant(全文 "你好")`
- **THEN** SSE 只收到 `你`、`好` 两条正文增量，不出现 `你好`

#### Scenario: 无增量时兜底

- **WHEN** 一轮中没有任何 `onTextDelta`，直接 `onAssistant("整段文本")`
- **THEN** SSE 收到一条 `message_delta(text)`，content 为 `整段文本`

#### Scenario: 逐轮重置

- **WHEN** 第一轮推过增量，第二轮没有任何增量
- **THEN** 第二轮的 `onAssistant` 仍兜底发出全文

### Requirement: 增量回调经复合 sink 转发

`CompositeSessionLogSink` SHALL 转发 `onTextDelta` 与 `onThinkingDelta`，确保 web 路径（SSE adapter 加落盘 recorder）下增量事件不会被接口默认实现吞掉。

#### Scenario: 复合 sink 转发增量

- **WHEN** 对复合 sink 调用 `onThinkingDelta` 与 `onTextDelta`
- **THEN** 每个子 sink 都收到对应回调

### Requirement: 多流并发隔离

`ChatStreamService` SHALL 保证每条 `ActiveStream` 的 SSE 事件只投递给该流自己的订阅者。

#### Scenario: 4 条流并发不串台

- **WHEN** 4 条流同时运行，各自产出带自身标签的正文增量
- **THEN** 每个订阅者只收到自己那条流的增量
- **AND** 顺序与内容均不被其它流污染
