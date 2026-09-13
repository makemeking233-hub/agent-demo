## Why

agent-demo 目前只在会话日志里零散记录每轮 `usage{prompt,completion}`，**没有面向用户的会话级统计**：`SessionController.current()` 恒返回 `{session_id:null}`（spec 声明的 `turn_count/tokens_in/tokens_out` 从未实现），也没有耗时、首 token 延迟、吞吐与缓存命中率。用户希望在 Web UI 底部（输入框下方）像 DeepSeek Harness 那样看到 `轮次 · 步数 | LLM 耗时 · 工具耗时 | 首 token 平均 · tok/s | 缓存命中 | 输入 token` 的实时状态栏，用于判断成本与性能。

## What Changes

- **指标模型**：新增 `SessionStats`（会话级累计）——`turns`（轮）、`steps`（工具调用次数）、`tokensIn`/`tokensOut`/`reasoningTokens`、`llmMillis`/`toolMillis`、`ttftMillis`+`ttftSamples`（首 token 平均）、`cacheHitTokens`/`cacheMissTokens`（可空 = N/A）。派生 `avgTtft` / `tokPerSec` / `cacheHitRate`。
- **采集**：`StreamChunk.Usage` 扩展缓存字段（DeepSeek `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens`）；`AgentLoop` 内计时（请求→首 token = TTFT；流结束 = llmMillis；工具执行 = toolMillis + steps++）。
- **落盘与恢复**：累计 stats 写入会话侧车 `<id>.meta.json`（与 `title` 同族），resume / 切换会话时读回。
- **推送**：新增 SSE 事件 **`turn_stats`**（每轮结束推累计 stats，工具执行中亦可推中间态）；新增 `GET /api/sessions/{id}/stats` 供首屏 / resume 回填。
- **UI**：Web 输入框**下方**新增状态栏组件，分段以 `|` 分隔并在超长时省略。
- **降级**：provider 不返回缓存字段时缓存命中显示 `N/A`（不按 0% 处理）。
- 不显示成本（`AgentConfig.Cost` 不接入本轮）。

## Capabilities

### New Capabilities
- `session-stats`: 会话级统计的指标模型、采集口径（轮/步/耗时/TTFT/吞吐/缓存命中）、`turn_stats` SSE 事件、`GET /api/sessions/{id}/stats` 接口，以及 Web 底部状态栏的行为要求。

### Modified Capabilities
- `web-ui`: 「流式聊天（SSE）」的事件白名单新增 `turn_stats` 事件类型（原白名单只有 `message_start` / `message_delta` / `tool_call_start` / `tool_call_end` / `permission_request` / `message_stop` / `error`）。

## Impact

- **core**：`agent-core/.../llm/StreamChunk.java`（Usage 加缓存字段）；`agent-core/.../provider/openai/OpenAiCompatibleMapper.java`（`parseUsage` 读缓存字段）；`agent-core/.../core/AgentLoop.java`（计时 + 累计，新增 `SessionStats` 采集）；`agent-core/.../core/TurnResult.java`（带每轮 stats）；新增 `agent-core/.../stats/SessionStats.java`。
- **web**：`agent-web/.../api/dto/SseEvent.java`（新增 `TurnStats` 事件）；`agent-web/.../stream/{SseSessionLogSink, ChatStreamService, WebAgentRuntime}.java`（推送 + 会话级 stats 存取）；`agent-web/.../api/SessionController.java`（`GET /api/sessions/{id}/stats`）。
- **前端**：新增 `agent-web/frontend/src/components/StatsBar.tsx`；`ChatPanel.tsx`（订阅 `turn_stats` + 首屏拉 stats）、`api/chat.ts`（`sessionStats`）。
- **测试**：core `Usage` 解析 / `SessionStats` 累计 / `AgentLoop` 计时；web `SseEventTest` / `ChatStreamServiceTest` / `SessionControllerTest` / `WebIntegrationTest`；前端 `StatsBar` 测试。
- **无破坏性 API**：新增事件 + 新增端点；SSE 事件白名单为**增量扩充**（旧客户端忽略未知事件即可）。

## Out of Scope

- 不显示估算成本（`AgentConfig.Cost` 暂不接入）。
- 不做跨会话 / 跨工作区的全局聚合（只做单会话累计）。
- 不做历史趋势图 / 时序持久化（只保留会话级累计标量）。
- 不改 CLI 的输出统计。
