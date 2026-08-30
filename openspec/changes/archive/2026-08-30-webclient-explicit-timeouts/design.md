# Design: WebClient Explicit Timeouts

## Architecture

```
OpenAiCompatibleProvider(apiKey, baseUrl)
  ↓
WebClient.builder()
  .baseUrl(baseUrl)
  .defaultHeader("Authorization", "Bearer " + apiKey)
  .clientConnector(ReactorClientHttpConnector(
      HttpClient.create()
        .responseTimeout(Duration.ofSeconds(60))   // 整体请求-响应
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)  // TCP 连接
  ))
  .build()
```

## Files Changed

- `agent-core/.../provider/openai/OpenAiCompatibleProvider.java`：构造加 2 个 Duration 参数
- `agent-core/.../provider/deepseek/DeepSeekProvider.java`：构造链透传（用默认 Duration）
- `agent-core/.../provider/minimax/MiniMaxProvider.java`：同上
- `agent-core/src/test/.../DeepSeekProviderTest.java`：构造调用更新

## Key Design Decisions

- **responseTimeout 用 `HttpClient.responseTimeout()`**（Reactor Netty 3.6+ 支持）：覆盖整个 request → first response 周期；流式 chunk 单独计时（不在 responseTimeout 内）
- **connectTimeout 用 `ChannelOption.CONNECT_TIMEOUT_MILLIS`**：避免 connection 阶段永久阻塞
- **默认值**：60s response / 10s connect（覆盖大多数 LLM 调用；reasoner 长思考 case 仍可正常返回首 token）
- **构造签名变更（破坏性）**：但 v0.1 → v0.2 升级期；只影响 DeepSeekProvider / MiniMaxProvider / OpenAiCompatibleProvider 3 个类构造
- **不通过 config 注入**（v0.2 hard-code 默认值；v0.3+ per-provider 可配）

## Notes

- `Duration` 是 `java.time.Duration`（不是 Reactor Duration；Reactor Duration 已被 deprecated）
