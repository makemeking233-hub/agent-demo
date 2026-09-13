## Context

agent-demo 当前的用量/统计现状：

- `StreamChunk.Usage(promptTokens, completionTokens, reasoningTokens)` 从 provider usage 解析；`AgentLoop.buildTurnResult` 每轮累计 prompt/completion；`SessionLogger.onTurnEnd` 写 `turn/end{usage{prompt,completion}}` 到会话日志。
- **缺**：缓存命中字段（`OpenAiCompatibleMapper.parseUsage` 未读 `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens`）、耗时统计（LLM/工具/TTFT/吞吐）。
- **缺**：会话级累计统计出口——`SessionController.current()` 恒返回 `{session_id:null}`（`web-ui` spec 声明的 `turn_count/tokens_in/tokens_out` 从未实现）。
- Web UI 输入区已有 `Composer`（含字符数/快捷键/权限下拉状态栏）；本次在**输入框下方**再加一条统计状态栏。
- 会话侧车 `<id>.meta.json{title}` 已由 add-workspaces-and-rename 引入（`SessionStore.writeTitle/readTitle`），可承载统计。

约束：JDK17 / Fail-Closed / 中文 commit·文档 / jacoco LINE≥80% BRANCH≥70%；SSE 事件白名单为**已归档 spec 契约**，扩充需 MODIFIED delta。

## Goals / Non-Goals

**Goals:**
- 会话级累计统计（轮/步/token/耗时/TTFT/吞吐/缓存命中），支持落盘与恢复。
- 每轮结束经 SSE `turn_stats` 实时推送；`GET /api/sessions/{id}/stats` 供首屏/resume 回填。
- Web 输入框下方统计状态栏，超长省略，不可用指标显示 `N/A`。

**Non-Goals:**
- 不显示估算成本（不接入 `AgentConfig.Cost`）。
- 不做跨会话/跨工作区全局聚合，不做历史趋势图/时序持久化。
- 不改 CLI 输出统计。

## Decisions

### D1: `SessionStats` 为会话级累计标量 + 派生指标
新增 `agent-core/.../stats/SessionStats.java`（不可变 record + `plus(TurnDelta)` 累加）：

```
turns, steps, tokensIn, tokensOut, reasoningTokens,
llmMillis, toolMillis, ttftMillis, ttftSamples,
cacheHitTokens, cacheMissTokens     // 可空(N/A) 语义：两者皆 0 且无样本视为不可用
派生：avgTtftMs = ttftMillis / ttftSamples
      tokPerSec = tokensOut / ((llmMillis - ttftMillis) / 1000)   // 纯生成耗时
      cacheHitRate = cacheHitTokens / (cacheHitTokens + cacheMissTokens)
```

> **吞吐分母用"纯生成耗时"**（`llmMillis - ttftMillis`）：代表真实解码速率，与 DSH 的 `120 tok/s` 语义一致；用 LLM 总耗时会因含 TTFT 与多轮工具往返而系统性偏低。
> 备选（LLM 总耗时）否决：语义是"端到端"而非"吞吐"。

### D2: 采集点在 `AgentLoop` 内自计时
- `streamChat` 订阅前记 `t0`；首个 `TextDelta` 记 `t1` → `ttftMillis += (t1-t0)`、`ttftSamples++`；流完成记 `tEnd` → `llmMillis += (tEnd-t0)`。
- 工具执行前后计时 → `toolMillis += d`、`steps++`（**含失败的工具调用**，与 DSH"步"语义一致）。
- provider 计时由后端负责（provider 不返回耗时）。

### D3: 缓存字段扩展 + N/A 降级
- `StreamChunk.Usage` 增加 `cacheHitTokens` / `cacheMissTokens`（可空 `Integer`）。
- `OpenAiCompatibleMapper.parseUsage` 读 `usage.prompt_cache_hit_tokens` / `prompt_cache_miss_tokens`（DeepSeek 返回；MiniMax 等无此字段 → 留空）。
- 全部轮次都无缓存字段 → `cacheHitRate` 为 `null` → 前端显示 `N/A`。
> **不按 0% 处理**：0% 与"该 provider 不支持缓存"语义不同，按 0% 会误导。

### D4: 落盘复用会话侧车（向后兼容扩展）
- `<id>.meta.json` 从 `{title}` 扩为 `{title, stats:{...}}`；`SessionStore` 新增 `readStats/writeStats`（与 `readTitle/writeTitle` 同族，路径白名单复用）。
- 旧侧车只有 `title` → 读 stats 时缺字段视为全 0（不报错）。
- resume / 选中会话 → 从侧车读回累计值，**不归零**。

### D5: SSE 新增独立 `turn_stats` 事件
- 新增 `SseEvent.TurnStats(...)`；每轮结束（正常/中断/出错）在 `message_stop` **之前**推送。
- **不改** `message_stop` 载荷（既有契约不动），白名单以 MODIFIED `web-ui` delta 增量扩充。
> 备选（扩展 `message_stop`）否决：message_stop 是"回合结束"语义，混入统计会让载荷职责不清，且无法在回合中途（工具执行时）推中间态。

### D6: `GET /api/sessions/{id}/stats`
- 未知会话 → 404 `session_not_found`；已知但无数据 → 200 + 全 0 + 空派生值。
- 与该会话的工作区路由一致（复用 `WebAgentRuntime.sessionsDirFor`）。

### D7: 前端 `StatsBar` 组件
- 新增 `components/StatsBar.tsx`，渲染在 `ChatPanel` 的 `Composer` **下方**；分段 `|` 分隔，`text-overflow: ellipsis` 单行省略。
- 数据来源：首屏 `api.sessionStats(sessionId)` + 运行时 `turn_stats` 事件刷新。
- 空值段显示 `N/A`（如缓存命中、无 TTFT 样本）。

## Risks / Trade-offs

- **[多轮工具往返使 llmMillis 含工具等待]** → 计时只覆盖 `streamChat` 调用区间（不含工具执行），工具耗时单列，两者不重叠、不重复计。
- **[`tokPerSec` 分母为 0 或负]** → 分母 ≤ 0 时返回 `null`，前端 `N/A`，避免除零/异常数值。
- **[侧车扩展破坏旧文件]** → 读时容错（缺 `stats` 视为空），只增字段不改 `title` 语义。
- **[SSE 新增事件对旧前端]** → 未知事件被忽略（现有前端已按 `type` 分支，无副作用）。
- **[统计口径与 CLI 不一致]** → 本 change 只覆盖 core 采集（CLI 也会累计），但 CLI **不展示**统计，不影响其输出。
- **[并发并行 agent]** → 本 change 触碰 `AgentLoop`/`SseEvent`/`ChatPanel` 等并行改动面，实施时拉取最新、只 stage 相关文件。

## Migration Plan

- 纯增量：新增事件/端点/侧车字段/前端组件；无数据迁移。
- 回滚：移除 `turn_stats` 推送与 `StatsBar` 即回退；旧侧车忽略 `stats` 字段。
- 部署顺序：core（Usage + SessionStats + 采集）→ web（事件 + API + 落盘）→ 前端。

## Open Questions

- `reasoningTokens` 是否在状态栏单列？→ 本 change 只累计与落盘，**状态栏 v1 不单列**（DSH 也没单列）。
- 状态栏是否显示 `步骤/轮` 比值等衍生？→ 不做，保持与 DSH 一致的分段。
- 会话删除/归档后 stats 是否随侧车搬移？→ 复用 `SessionStore.archive/restore` 的侧车搬移逻辑（已有），无需额外处理。
