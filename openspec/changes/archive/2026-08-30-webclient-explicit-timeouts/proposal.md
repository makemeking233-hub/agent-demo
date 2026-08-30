## Why

`OpenAiCompatibleProvider` 当前用 `WebClient.builder().baseUrl().defaultHeader().build()` 构建 HTTP 客户端，**没显式配置任何超时**。结果是：DeepSeek 长时间无响应时（服务端 hang、TCP 半开、Provider 故障）会无限等待，REPL 卡住，Ctrl+C 也不一定能中断（依赖 `streamChat` 的 Reactor timeout）。plan §"已知简化"明确要求 v0.2 显式 `responseTimeout` + `connectTimeout`。

## What Changes

- `OpenAiCompatibleProvider` 构造加 `Duration responseTimeout` 和 `Duration connectTimeout` 参数
- 用 `ExchangeStrategies` + `responseTimeout` 配置整体请求-响应超时（不是流超时；流式 chunk 单独算）
- 默认值：`responseTimeout=60s`（首 token TTFT 上限；reasoner 留余量），`connectTimeout=10s`（TCP 三次握手）
- 在 `build()` 前 `.clientConnector(ReactorClientHttpConnector(...))` 配置 connectTimeout

## Capabilities

### New Capabilities
- 无（属于 provider 内部超时配置）

### Modified Capabilities
- `observability`：超时失败也算可观测事件（log warn）
- `provider` 内部：HTTP 客户端有明确超时边界

## Impact

- 受影响代码：`OpenAiCompatibleProvider` 构造签名（public 改动 → 编译 break）
- 受影响构造方：`DeepSeekProvider(String)` / `DeepSeekProvider(String, String)` / `MiniMaxProvider(String)` / `MiniMaxProvider(String, String)`
- 测试：构造器签名变化，所有 wiremock-based test 需要更新
- 估计：< 1 天
- Out of Scope：v0.2 不做 per-call 可配置超时（hard-code 默认值；v0.3+ 加 config 项）
