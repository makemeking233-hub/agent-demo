## MODIFIED Requirements

### Requirement: WebSearch 工具可调用

系统 SHALL 提供一个模型可调用的 `web_search` 工具，接收查询词（`query`）返回结构化检索结果（标题 / URL / 摘要 / 日期），并将结果注入模型上下文。`web_search` 工具的执行 SHALL 在 Reactor 调度器隔离的线程上运行，不得在 Reactor Netty event loop 线程上执行阻塞 IO 调用。

#### Scenario: 正常检索返回结构化结果

- **WHEN** 模型调用 `web_search` 且 `query` 非空
- **THEN** 工具返回若干条结构化来源（title / url / snippet / publishedAt），供模型引用

#### Scenario: 空查询返回错误

- **WHEN** 模型调用 `web_search` 但 `query` 为空或空白
- **THEN** 工具返回错误结果（isError=true），提示需提供查询词

#### Scenario: Web 端 event loop 线程不阻塞

- **WHEN** `web_search` 在 Reactor Netty event loop 线程（如 `reactor-http-nio-N`）上被订阅执行
- **THEN** provider 的同步阻塞调用不会落到 event loop 线程上，工具正常返回结果或带原始异常消息的错误结果，不抛 `block()/blockFirst()/blockLast() are blocking` 错误

### Requirement: 失败时 Fail-Closed

系统 SHALL 在搜索 provider 无凭据、调用失败或超时的情况下返回带有指引的错误结果（isError=true），不得静默返回空结果或抛未捕获异常。错误结果的文本 SHALL 透传 provider 抛出的原始异常消息（`e.getMessage()`），不得用通用文案覆盖真实诊断信息。

#### Scenario: 无凭据

- **WHEN** 选中的 provider 缺少其 API key（deepseek 无 key / tavily 无 `TAVILY_API_KEY`）
- **THEN** 系统返回说明缺失凭据及处理指引的错误结果

#### Scenario: 网络/超时失败

- **WHEN** provider 调用超时或返回错误
- **THEN** 系统返回错误结果并保留原始错误信息，不影响 agent 主流程

#### Scenario: 错误消息透传

- **WHEN** provider 抛出异常（线程模型错误 / 超时 / 反序列化失败等任意类型）
- **THEN** 错误结果的文本包含原始 `e.getMessage()`，便于上游模型与用户诊断
