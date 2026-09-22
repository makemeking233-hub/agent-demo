# 测试用例：add-message-actions（P1 copy + P2 per-message clock）

> 来源：用例矩阵见 `test-design.md` §5（本文件给出全量明细，编号与之一致）。
> 落地情况：全部用例已实现并纳入自动化套件。

---

## 1. 后端 SSE / 存储

| 编号 | 用例 | 前置 | 步骤 | 预期 | 落地位置 |
|:----:|------|------|------|------|---------|
| T-01 | `message_meta` 先于 `turn_stats`/`message_stop` | mock `ChatStreamService` | `onUser` → `onAssistant` → `onTurnEnd` | `InOrder`：`emitMessageMeta` 未被 `onTurnEnd` 抢先 | `SseSessionLogSinkTest.onTurnEndEmitsMessageMetaBeforeTurnStatsAndStop` |
| T-02 | duration 非负 | 同上 | 不调 `onUser` 直接 `onTurnEnd` | `durationMs >= 0` | `SseSessionLogSinkTest.onTurnEndPassesNonNegativeDuration` |
| T-03 | 真实事件顺序 + 字段 | 真实 `ChatStreamService` | delta(`llm=2000, ttft=500, out=300`) → `onTurnEnd` | 事件序 `message_meta → turn_stats → message_stop`；JSON 含 `"ttft_ms":500.0`、`"tok_per_sec":200.0`、`"uuid":null` | `SseSessionLogSinkTest.messageMetaEventPrecedesMessageStopWithPerTurnFields` |
| T-04 | 无 usage → null | 同上 | delta 全 0 | JSON 含 `"ttft_ms":null` 与 `"tok_per_sec":null` | `SseSessionLogSinkTest.messageMetaDerivedFieldsAreNullWithoutUsage` |
| T-05 | 落盘读数 | `@TempDir` + 真实 `SessionStore` | `onUser` → `onAssistant` → `onTurnEnd(2000/500/300)` | 存档出现 `meta(key=message_meta)`，`uuid` == assistant 条目 uuid，`ttft_ms=500.0`、`tok_per_sec=200.0`、`duration_ms>=0` | `SessionRecorderTest.turnEndPersistsMessageMetaWithAssistantUuid` |
| T-06 | 无 assistant 不写无主读数 | 同上 | `onUser` → `onTurnEnd`（不调 `onAssistant`） | `lastAssistantUuid()==null`；存档无 `message_meta` 条目 | `SessionRecorderTest.turnEndSkipsMessageMetaWhenNoAssistant` |

## 2. 后端 REST 历史回填

| 编号 | 用例 | 前置 | 步骤 | 预期 | 落地位置 |
|:----:|------|------|------|------|---------|
| T-07 | assistant 带 uuid + meta | 存档含 user / assistant / `meta(message_meta)` | `controller.messages("s-p2")` | 200；`messages[0]`（user）`uuid`/`meta` 均 null；`messages[1]`（assistant）`uuid`==条目 uuid、`meta` 含 `duration_ms`/`ttft_ms`/`tok_per_sec` | `SessionControllerTest.messagesAttachPerMessageMetaToAssistant` |
| T-08 | uuid 找不到 → 丢弃 | `meta` 的 uuid 不在存档 | 同上 | `messages[1].meta == null`（不误贴） | `SessionControllerTest.messagesIgnoreMetaWhenUuidUnknown` |
| T-09 | 老会话无读数 | 存档只有 user/assistant | 同上 | 200，2 条消息，`messages[1].meta == null` | `SessionControllerTest.messagesWithoutMessageMetaStillWork` |

## 3. 前端 clock 格式化（纯函数）

`agent-web/frontend/src/lib/message-clock.test.ts`（18 用例）

| 编号 | 用例 | 输入 | 预期 |
|:----:|------|------|------|
| T-10 | 全字段拼装 | 15s / 1.2s TTFT / 34 tok/s / 16:23 | `16:23 · Ran for 15s · TTFT 1.2s · 34 tok/s` |
| T-11a | 缺 TTFT | `ttft_ms: null` | `16:23 · Ran for 15s · 34 tok/s` |
| T-11b | 缺 tok/s | `tok_per_sec: null` | `16:23 · Ran for 15s · TTFT 1.2s` |
| T-11c | 缺全部派生指标 | 只有 duration | `16:23 · Ran for 2.0s` |
| T-11d | meta 为 null | `formatClock(null)` | `""`（调用方据此不渲染） |
| T-12a | 时长分档 | 450 / 1500 / 9999 / 15000 / 59400 / 120000 / 123000 ms | `450ms` / `1.5s` / `10.0s` / `15s` / `59s` / `2m` / `2m3s` |
| T-12b | 时长非法值 | null / undefined / -1 / NaN | `null` |
| T-12c | TTFT 分档 | 820 / 1200 ms | `820ms` / `1.2s` |
| T-12d | tok/s 分档 | 34.4 / 8.53 / 0 / -3 / null | `34 tok/s` / `8.5 tok/s` / null / null / null |
| T-12e | 时间 | 本地 16:23:45 / 0 / null | `16:23` / null / null |

## 4. 前端组件与时间线

| 编号 | 用例 | 前置 | 步骤 | 预期 | 落地位置 |
|:----:|------|------|------|------|---------|
| T-13a | clock 全字段渲染 | — | 渲染 `<MessageActionRow meta={...}>` | `msg-clock` 文本 == `16:23 · Ran for 15s · TTFT 1.2s · 34 tok/s` | `MessageActionRow.test.tsx` |
| T-13b | 缺 TTFT 段 | — | 同上（`ttft_ms: null`） | 文本为 `16:23 · Ran for 15s · 34 tok/s` | 同上 |
| T-13c | 缺 tok/s 段 | — | 同上（`tok_per_sec: null`） | 文本为 `16:23 · Ran for 2.0s · TTFT 800ms` | 同上 |
| T-13d | 无 meta | — | 不传 / 传 null | 不渲染 `msg-clock` 元素 | 同上 |
| T-14 | row 内顺序 | — | 传 children + meta | `[data-testid]` 顺序为 `msg-copy → up → msg-clock` | `MessageActionRow.test.tsx`（P1 用例已改） |
| T-15a | 贴到最后一条 assistant | user + assistant | `attachMetaToTimeline` | assistant item 的 `meta` 与 `uuid` 被写入 | `ChatPanel.test.tsx` |
| T-15b | 工具拆条场景 | user + (assistant+tool) + assistant | 同上 | 只有**最后一条** assistant 带 `meta`，前一条不带 | 同上 |
| T-15c | 无 assistant 文本项 | 只有 user | 同上 | 原样返回（同一引用），不抛错 | 同上 |
| T-16a | 历史透传 | `mapHistoryToItems` 输入带 `uuid`/`meta` | 调用 | assistant item 的 `uuid`/`meta` 与输入一致 | 同上 |
| T-16b | 历史无读数 | 输入不含 `meta` | 调用 | item 的 `meta` 为 `undefined` | 同上 |
| T-17 | P1 copy 回归 | — | 点击 copy / ✓ 1s / 防重入 / 两条降级 | 7 条既有用例继续通过 | `MessageActionRow.test.tsx` |

## 5. 未落地 / 后续

| 项 | 原因 |
|----|------|
| clock 的真实浏览器观感（hover 透明度、换行、暗色主题） | 本次只做单测；观感验收留给下一次 Web E2E 批次 |
| `message_meta` 在多迭代（工具调用）回合下的 per-iteration 读数 | 当前设计为**按轮**读数；per-iteration 拆分属独立需求 |
| 赞踩 / regenerate | 不在本 change 范围（`add-message-feedback` / `add-message-regenerate`） |
