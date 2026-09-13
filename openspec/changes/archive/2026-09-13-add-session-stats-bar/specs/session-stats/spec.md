## ADDED Requirements

### Requirement: 会话级统计指标

系统 SHALL 维护每个会话的累计统计 `SessionStats`，包含：轮次 `turns`、步数 `steps`（工具调用次数）、输入 token `tokensIn`、输出 token `tokensOut`、推理 token `reasoningTokens`、LLM 累计耗时 `llmMillis`、工具累计耗时 `toolMillis`、首 token 延迟累计 `ttftMillis` 与样本数 `ttftSamples`、缓存命中 `cacheHitTokens` 与未命中 `cacheMissTokens`。

#### Scenario: 统计随轮次累计

- **WHEN** 同一会话连续进行多轮对话
- **THEN** `turns` 等于已完成的回合数，`steps` 等于累计工具调用次数
- **AND** `tokensIn` / `tokensOut` 等于各轮 `usage` 之和

#### Scenario: 派生指标计算

- **WHEN** 客户端读取统计
- **THEN** `avgTtftMs = ttftMillis / ttftSamples`（`ttftSamples = 0` 时为空）
- **AND** `tokPerSec = tokensOut / ((llmMillis - ttftMillis) / 1000)`（纯生成耗时；分母 ≤ 0 时为空）
- **AND** `cacheHitRate = cacheHitTokens / (cacheHitTokens + cacheMissTokens)`（两者皆空时为空）

### Requirement: 指标采集

系统 SHALL 在 agent 回合执行过程中采集计时与用量：请求发出到首个文本 token 的延迟计入 `ttftMillis`，一次 `streamChat` 的起止计入 `llmMillis`，每次工具执行结束计入 `toolMillis` 且 `steps` 自增。

#### Scenario: 采集首 token 延迟与 LLM 耗时

- **WHEN** 一个回合发起 `streamChat` 并收到首个文本 chunk
- **THEN** 该回合的 `ttftMillis += (首个文本 chunk 时刻 - 请求发出时刻)`、`ttftSamples += 1`
- **AND** 流结束时 `llmMillis += (流结束时刻 - 请求发出时刻)`

#### Scenario: 采集工具耗时与步数

- **WHEN** 一个工具调用执行结束（成功或失败）
- **THEN** `steps += 1`
- **AND** `toolMillis +=` 该工具执行耗时

#### Scenario: 采集 token 用量与缓存字段

- **WHEN** provider 返回 usage（含 `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens`）
- **THEN** `tokensIn` / `tokensOut` / `reasoningTokens` / `cacheHitTokens` / `cacheMissTokens` 按该轮 usage 累加

### Requirement: 缓存命中降级

系统 SHALL 在 provider 未返回缓存字段时把缓存命中率表示为"不可用"，而不是 0%。

#### Scenario: provider 不返回缓存字段

- **WHEN** 某会话所有轮次的 usage 都不含缓存字段
- **THEN** `cacheHitTokens` 与 `cacheMissTokens` 均为空
- **AND** 派生 `cacheHitRate` 为空（前端显示 `N/A`）

#### Scenario: provider 返回缓存字段

- **WHEN** usage 含 `prompt_cache_hit_tokens` 与 `prompt_cache_miss_tokens`
- **THEN** 两者被累加，`cacheHitRate` 按 `hit/(hit+miss)` 计算

### Requirement: turn_stats SSE 事件

系统 SHALL 在每个回合结束时通过 SSE 推送 `turn_stats` 事件，携带该会话的累计统计。

#### Scenario: 回合结束推送累计统计

- **WHEN** 一个回合完成（正常结束 / 中断 / 出错）
- **THEN** 服务端推送 `data: {"type":"turn_stats","turns":<int>,"steps":<int>,"tokens_in":<int>,"tokens_out":<int>,"llm_ms":<int>,"tool_ms":<int>,"avg_ttft_ms":<number|null>,"tok_per_sec":<number|null>,"cache_hit_rate":<number|null>}`
- **AND** 数值为该会话截至本回合的累计值

#### Scenario: turn_stats 与既有事件顺序

- **WHEN** 回合正常结束
- **THEN** `turn_stats` 在 `message_stop` 之前推送
- **AND 不**改变既有事件（`message_start` / `message_delta` / `tool_call_*` / `permission_request` / `message_stop` / `error`）的形状

### Requirement: 会话统计查询接口

系统 SHALL 提供 `GET /api/sessions/{session_id}/stats` 返回某会话的累计统计，供首屏与 resume 回填。

#### Scenario: 查询已知会话统计

- **WHEN** 客户端发送 `GET /api/sessions/{session_id}/stats`，且该会话存在
- **THEN** 服务端返回 `200 OK`，响应体含上述累计指标字段（无数据的会话返回全 0 与空派生值）

#### Scenario: 查询未知会话统计

- **WHEN** 目标会话不存在
- **THEN** 服务端返回 `404 Not Found`，响应体 `{"error":"session_not_found"}`

### Requirement: 统计落盘与恢复

系统 SHALL 把会话累计统计持久化到会话侧车元数据，并在会话恢复时读回。

#### Scenario: 统计随会话落盘

- **WHEN** 一个回合结束并更新了累计统计
- **THEN** 该会话的侧车元数据 `<session_id>.meta.json` 中的统计字段被更新（与既有 `title` 同族）

#### Scenario: 恢复会话读回统计

- **WHEN** 服务端重启或客户端重新选中某会话
- **THEN** `GET /api/sessions/{session_id}/stats` 返回该会话落盘前的累计值（不归零）

### Requirement: 底部状态栏（web）

系统 SHALL 在 Web 输入框下方显示统计状态栏，分段展示轮次与步数、LLM 与工具耗时、首 token 平均与吞吐、缓存命中率、输入/输出 token。

#### Scenario: 状态栏渲染

- **WHEN** web UI 加载并选中某会话
- **THEN** 输入框下方显示状态栏，形如 `N 轮 · M 步 | LLM Xs · 工具调用 Ys | 首 token 平均 Zs · P tok/s | 缓存命中 Q% | 输入 R tok · 输出 S tok`
- **AND** 首屏数据来自 `GET /api/sessions/{id}/stats`

#### Scenario: 回合结束实时刷新

- **WHEN** 前端收到 `turn_stats` 事件
- **THEN** 状态栏立即用事件中的累计值刷新（无需轮询）

#### Scenario: 不可用指标展示

- **WHEN** 某派生指标为空（如缓存命中率 N/A、无 TTFT 样本）
- **THEN** 状态栏该段显示 `N/A`（或省略该段），不显示误导性的 0

#### Scenario: 超长省略

- **WHEN** 状态栏内容超出可用宽度
- **THEN** 以省略号截断，不换行撑高布局
