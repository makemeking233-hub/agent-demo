## Why

`web_search` 工具在 Web 端 SSE 流（Reactor Netty event loop 线程）调用时，连续失败并报 `block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-9`。根因是 `WebSearchTool.execute()` 用 `Mono.fromCallable(...)` 包住 `provider.search(...)`，而两个 provider（`DeepSeekWebSearchProvider` / `TavilyWebSearchProvider`）内部对 WebClient `Mono` 调用了 `mono.block()`——`Mono.fromCallable` 默认在订阅线程（即 event loop）执行 callable，导致阻塞调用落在 event loop 线程上。此外错误信息末尾统一文案 "请检查搜索 provider 的 API key 配置与网络连接" 把真实异常吞掉，让用户/模型看不到 `block() are not supported` 这条关键诊断。

## What Changes

- `WebSearchTool.execute()` 在 `Mono.fromCallable(...)` 末尾追加 `.subscribeOn(Schedulers.boundedElastic())`，把同步阻塞 IO 调度到 Reactor 的弹性线程池（专门吃阻塞 IO），event loop 不再被阻塞
- `WebSearchTool.execute()` 错误文案改为透传 `e.getMessage()`，不再附加 "请检查 ... 与网络连接" 误导性提示
- `WebSearchToolTest` 新增两个用例：在 reactor 测试调度器上调用 execute() 不触发 `block()` 抛错；provider 抛错时错误文本包含原始 `e.getMessage()`
- 修改 `openspec/specs/search/spec.md`：在 "WebSearch 工具可调用" Requirement 上追加 MODIFIED 子段（调度隔离 + 错误消息透传）

## Capabilities

### New Capabilities

无

### Modified Capabilities

- `search`：在 WebSearch 工具可调用的执行调度上追加约束（不允许在 Reactor event loop 线程阻塞）；错误结果 SHALL 透传原始异常消息

## Impact

- 修改：`agent-core/src/main/java/com/example/agent/tools/WebSearchTool.java`（新增 import + execute 末尾追加 `.subscribeOn(...)` + 改 catch 块）
- 新增测试用例：`agent-core/src/test/java/com/example/agent/tools/WebSearchToolTest.java`
- `openspec/specs/search/spec.md`：追加 MODIFIED Requirements 段
- 不改：`DeepSeekWebSearchProvider` / `TavilyWebSearchProvider` / `WebSearchProvider` 接口 / `WebSearchProviderFactory` / 其他 4 个 search 测试
- 不引入新依赖（Reactor `Schedulers.boundedElastic()` 已随 spring-boot-starter-webflux 引入）
- 风险：现有 `WebSearchToolTest` 已有用例可能在 boundedElastic 调度下时序略变（同步 block → 弹性调度），需关注是否有 sleep-based 断言；初步看代码无 sleep-based 断言
