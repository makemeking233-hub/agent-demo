# test-review — add-true-streaming 复盘

> 批次：`2026-09-04-true-streaming`
> 复盘撰写时间：2026-09-04（与测试同批完成，无补写）

---

## 1. 流程回顾

本轮工作的起点是一份**上一轮 session 的交接说明**，其中记录了「task 1.1 卡住」的诊断：把 `bodyToMono` 改成 `bodyToFlux` 后，连最简的单段响应测试都拿到 `chunks: []`，且已试过 `SseLineBuffer`、`Flux.create`、`concatMap` 等多种写法均未成功，怀疑是 Reactor 调度问题。

实际推进顺序：

1. **先复现基线，不采信二手结论。** 还原被改乱的 provider 文件后实跑，得到与交接说明**不同**的事实：单段测试与三段测试**本来就是绿的**，只有 TTFT 用例失败（837ms）。交接说明里「连 1 段测试也 fail」是上一个 agent 自己改坏 provider 后产生的假象。
2. **二分定位。** 先用合成 `DataBuffer` 的纯单元测试证明 `SseLineBuffer` 正确（7 例全绿），再用裸 WebClient 证明 `bodyToFlux` 正确，逐步把范围收敛到 provider 内部。
3. **插探针拿实证。** 在 `streamChat` 链路里加 `doOnNext` 打印，一次运行就暴露出 `parsed=Optional.empty`——前面 buffer 和行切分都正常，是解析阶段全空。
4. **定位真因。** `parseSseLine` 内部自己会判断并剥掉 `data: ` 前缀；调用方（上一个 agent 新加的 `.map(line -> line.substring("data: ".length()).trim())`）提前剥了一次，导致它 `startsWith("data: ")` 判断失败，对每行都返回 empty。
5. **修完再验证真流式，并顺手证伪 design.md 的两条「关键改动」。**
6. 补齐 D4/D5 与测试，收尾文档。

---

## 2. 问题与根因

### 2.1 主问题：`chunks: []` 的假象

| 项 | 内容 |
|----|------|
| 现象 | `bodyToFlux` 改造后所有 E2E 用例返回空列表且正常 complete，无任何异常 |
| 表面根因 | 一度被判为「Reactor 调度 / `Flux.create` 冷流 / `concatMap` 空内层」 |
| 真实根因 | （a）上一个 agent 的 working tree 里 provider 被反复改坏（`baseUrl()` 抽象方法被删、构造器字段定义错乱），使基线本身不可信；（b）其新增的前缀剥离逻辑与 `parseSseLine` 的职责重复 |
| 关键教训 | 交接说明里的「已排除项」本身可能是被污染环境下的产物。**先还原到干净 HEAD 复现基线**，比继续在脏树上加实验省得多 |

### 2.2 测试设计错误：`withFixedDelay` 测不出 TTFT

原 TTFT 用例用 `withFixedDelay(800)`——该 stub 会把**整个响应体**延迟 800ms 才发送。这意味着即使实现已经是真流式，首字节也只能在 800ms 到达，断言「TTFT 小于 300ms」永远不可能通过。

这属于**测试设计错误而非实现缺陷**：一个永远红的用例，会被误读成「功能没做好」。

正确做法是 `withChunkedDribbleDelay(n, totalMs)`，把响应体切成 n 段、在 totalMs 内陆续发送，这样「首段早到」才可观测，并且顺带覆盖跨帧切行。

### 2.3 被证伪的两条「关键改动」

design.md 原列 7 处改动，其中 D2（`replay().all()` 改 `unicast()`）与 D3（`.block()` 改 `.subscribe()`）被标为「关键」。实测证伪：

- **D2**：`onThinkingDelta` 走的正是同一个 replay sink，其逐 token 输出一直是正常的——这是 replay 不延迟事件的直接反证。replay 只影响「订阅发生在 emit 之后」的场景，而正常时序不会这样。此外 replay 是 `spec §resume` / `Last-Event-ID` 重连的基础，改成 unicast 会破坏该语义。
- **D3**：`.block()` 阻塞的是 `Executors.newCachedThreadPool()` 里的 executor 线程，HTTP handler 线程早已返回；SSE emit 发生在 Netty 事件循环线程上。改成 `.subscribe()` 只省一个线程，与 TTFT 无关。

**真正的 TTFT 瓶颈只有一处**（`bodyToMono`），**前端「一坨出来」的直接原因是编排层攒批**（`onAssistant` 在 `collectList()` 之后才 emit 正文），与 sink 模式无关。

决策：保留 replay 与 `.block()`，改动收敛为 4 处，并把 design.md 的 D2/D3 改写为「否决加理由」，避免后续 session 重新踩同一个误判。

### 2.4 附带发现：复合 sink 吞掉 thinking 增量

`CompositeSessionLogSink` 未覆盖 `onThinkingDelta`。由于增量回调在 `SessionLogSink` 上是 `default` 空方法，Java 不会报错——**编译期完全静默**。而 web 路径在存在落盘录制器时返回的正是复合 sink，等于刚上线的 thinking 流式在正常路径下根本没到前端。

这类「接口 default 方法 + 装饰器漏转发 = 静默失效」的模式值得警惕：任何新增 `default` 回调，都必须同步检查所有装饰器实现。

---

## 3. 做得好的

1. **拒绝对二手结论做二次推理。** 花在「还原 HEAD 并实跑基线」上的少量成本，直接推翻了交接说明的核心前提，避免了在错误方向上继续试 `Flux.create` / backpressure 等方案。
2. **分层二分，每层用最合适的工具。** 纯单元（合成 buffer）验证算子、裸 WebClient 验证 HTTP 集成、探针验证业务链路——三步就把范围从「Reactor 调度玄学」收敛到「一个 `substring` 调用」。
3. **实测数据驱动设计决策。** D2/D3 的否决不是靠论证而是靠既有功能（thinking 流式）的反证，并写进了 design.md，形成可追溯的决策记录。
4. **测试断言对外契约而非内部实现。** L3/L4 用例解析真实 SSE JSON 载荷，而不是 verify mock 交互，契约破了才会红。

## 4. 可改进的

1. **交接说明应附「基线复现命令与原始输出」。** 本轮最大的时间开销来自验证交接结论的真伪；若交接文档里带上 `mvn ... -Dtest=...` 的原始输出，「连 1 段测试也 fail」这个错误结论当场即可识破。
2. **临时诊断代码应及时清理。** 上一轮的 `SseLineBuffer`、`OpenAiCompatibleProviderE2ETest` 与一堆 `DEBUG` println 混在 working tree 里未提交，是环境被污染的源头之一。本轮已删除全部临时探针。
3. **改数据流的算子前，先读被调函数的契约。** `parseSseLine` 的 Javadoc 明确写了「@param line "data: {...}" 或 "data: [DONE]" 行」，即它接受带前缀的整行。加 `.map(substring)` 之前读一眼就能避免整轮调试。
4. **jacoco 门禁欠账应尽早单独立项。** 本次已把三个违规包逐类定位到具体类（`SslCertificateGenerator` 一个类就拖垮整个 `config` 包），可直接作为下一个 change 的输入。

---

## 5. 交付物

| 类型 | 路径 |
|------|------|
| 代码（agent-core） | `OpenAiCompatibleProvider.java`、`SseLineBuffer.java`、`SessionLogSink.java`、`CompositeSessionLogSink.java`、`AgentLoop.java` |
| 代码（agent-web） | `SseSessionLogSink.java` |
| 测试 | `SseLineBufferTest.java`、`OpenAiCompatibleProviderE2ETest.java`、`ChatStreamServiceStreamingTest.java`、`ChatStreamServiceConcurrencyTest.java`、`CompositeSessionLogSinkTest.java` |
| 设计文档 | `docs/streaming-architecture.md` |
| OpenSpec | `openspec/changes/add-true-streaming/{proposal,design,tasks}.md`、`specs/web-ui/spec.md` |
| 四件套 | `docs/test-agent-demo/2026-09-04-true-streaming/{test-design,test-cases,test-report,test-review}.md` |
| 索引 | `docs/test-agent-demo/test-guide.md`（登记表与批次详情） |

---

## 6. 结论

- 本次 change 的验收目标（首 token 小于 300ms、正文逐 token 渲染、多流不串台）**全部达成**并有用例守住。
- 4 处真实缺陷已修复，其中 3 处（D1/D3/D4）是用户可见的假流式，1 处（D4/复合 sink）是刚上线功能的静默失效。
- 2 条原「关键改动」经实测证伪后否决，决策记录已写入 design.md。
- 遗留：agent-web jacoco 门禁 3 处既有违规（已逐类定位）与 4 条既有 Selenium 用例失败，均与本次无关，建议另开 change 处理。
