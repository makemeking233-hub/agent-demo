# Design: web_search 阻塞调用修复（fix-web-search-blocking-call）

## Context

agent-demo 的内置搜索工具 `web_search`（add-web-search-tool change）在 CLI 模式下能正常工作（CLI 用独立线程跑 AgentLoop），但在 Web 端 SSE 流里连续失败：

```
web_search 失败: block()/blockFirst()/blockLast() are blocking,
which is not supported in thread reactor-http-nio-9
（请检查搜索 provider 的 API key 配置与网络连接）
```

根因：

- `WebSearchTool.execute()` 返回 `Mono.fromCallable(() -> { provider.search(...); ... })`
- `provider.search(...)` 内部对 WebClient Mono 调用 `mono.block()` 同步阻塞
- `Mono.fromCallable` 默认在订阅线程执行 callable；Web 端订阅来自 SSE event loop（`reactor-http-nio-N`）
- 阻塞调用落在 event loop 线程 → Reactor Netty 抛 `block() not supported`

另外错误信息末尾固定追加的 "请检查 ... 与网络连接" 误导：把 `block() not supported` 真实错误吞掉，看起来像"网络问题"。

## Goals / Non-Goals

**Goals：**

- 修复 `web_search` 在 Web 端 event loop 线程调用时的阻塞错误
- 错误信息透传原始 `e.getMessage()`，便于模型/用户诊断
- 不破坏现有 provider 接口与现有测试

**Non-Goals：**

- 不把 `WebSearchProvider.search()` 改为异步接口
- 不替换 WebClient 为其他 HTTP 客户端
- 不引入新依赖
- 不重写 provider 内部实现

## Decisions

### 决策 1：用 `Schedulers.boundedElastic()` 隔离阻塞调用

在 `WebSearchTool.execute()` 末尾追加 `.subscribeOn(Schedulers.boundedElastic())`：

```java
return Mono.fromCallable(() -> {
        // ... 原有 provider.search(...) 同步块
    })
    .subscribeOn(Schedulers.boundedElastic());
```

**为什么选 boundedElastic**：

- Reactor 内置、零新依赖
- 专门为阻塞 IO 设计（有界线程池、命名清晰、监控可见）
- 默认上限 10 × CPU 核数线程，足以应对 web_search 偶发调用
- 与 `Mono.fromCallable` 配合时语义清晰：callable 在弹性线程执行，event loop 不被阻塞

**为什么不用 parallel / single**：

- `Schedulers.parallel()` 不适合阻塞 IO（CPU-bound）
- `Schedulers.single()` 全局单线程，并发 web_search 会串行
- `Schedulers.immediate()` 等价于不加（就是当前失败原因）

### 决策 2：错误文案透传原始 `e.getMessage()`

```java
// before
return ToolResult.<String>error(
    "web_search 失败: " + e.getMessage()
    + "（请检查搜索 provider 的 API key 配置与网络连接）");

// after
return ToolResult.<String>error("web_search 失败: " + e.getMessage());
```

**取舍**：失去"网络问题"提示的引导性，但换来关键诊断信息可见（如 `block() not supported` / `DeepSeek 搜索缺少 API key`）。

### 决策 3：测试用 `StepVerifier.withVirtualTime` + 自定义调度器

新测试用例：

1. **非阻塞路径**：mock provider 让 search() 实际执行 50ms 阻塞；用 `StepVerifier.create(tool.execute(...)).verifyComplete()` 在 reactor 虚拟时钟上完成，验证不抛 `block() not supported`。
2. **错误消息透传**：mock provider 抛 `IllegalStateException("DeepSeek 搜索缺少 API key：请设置环境变量 DEEPSEEK_API_KEY")`；验证结果 `isError=true` 且文本包含该原消息。

**为什么用 StepVerifier**：

- 能验证异步/响应式行为的端到端时序
- 虚拟时钟避免真实 sleep
- Reactor 标准测试模式

## Risks / Trade-offs

- **[Risk] boundedElastic 线程池排队延迟**（web_search 高并发调用时） → 缓解：默认上限 10 × CPU 核数，对 web_search 偶发场景足够；监控线程池可后续加。
- **[Risk] 现有 WebSearchToolTest 测试时序变化**（同步 block → 弹性调度） → 缓解：手工核对现有测试无 sleep-based 断言；如有则改用 StepVerifier。
- **[Risk] 测试环境下 boundedElastic 也可能与 Reactor test scheduler 冲突** → 缓解：测试用 `Schedulers.boundedElastic()` 默认实例，不用 test scheduler 替换。

## Migration Plan

1. 单次合并，无数据迁移，无 schema 变更
2. 前端无变化（错误文案变化对前端是透明的，前端只展示 tool result 文本）
3. 回滚策略：直接 revert 即可

## Open Questions

- 是否需要在 boundedElastic 上加 metrics（线程池大小 / 队列长度）？v1 暂不加，依赖 Reactor 默认 metrics
- 是否需要把 `Schedulers.boundedElastic()` 提升为可注入 bean，便于测试替换？v1 用默认实例，v2 视需要
