# 测试设计：add-message-actions（P1 copy + P2 per-message clock）

> 批次目录：`docs/test-agent-demo/2026-09-22-add-message-actions/`
> 对应 change：`openspec/changes/add-message-actions/`（已归档）
> 执行日期：2026-09-22
> 规范：`AGENTS.md §2.6`（四件套）、`§2.7.5`（合并门禁）

---

## 1. 测试范围

| 层 | 被测对象 | 是否本批次新增 |
|----|---------|--------------|
| 后端 SSE | `SseEvent.MessageMeta`、`SseSessionLogSink` per-turn 时序、`ChatStreamService.emitMessageMeta` | 新增 |
| 后端存储 | `SessionRecorder` 的 `lastAssistantUuid` + `meta(key="message_meta")` 落盘 | 新增 |
| 后端 REST | `GET /api/sessions/{id}/messages` 回填 `meta` / `uuid` | 新增 |
| 前端渲染 | `MessageActionRow`（copy + clock）、`MessageBubble` 透传 | copy 为 P1 既有、clock 新增 |
| 前端逻辑 | `message-clock.ts` 格式化、`ChatPanel.attachMetaToTimeline` / `mapHistoryToItems` | 新增 |

**不在范围**：regenerate（独立 change）、赞踩 + feedback sidecar（拆为 `add-message-feedback`）、真实浏览器 E2E。

---

## 2. 测试目标

1. `message_meta` 在 `message_stop` **之前**推送，且事件顺序为 `message_meta → turn_stats → message_stop`。
2. 读数口径正确：`duration_ms` 为 turn wall time；`ttft_ms` / `tok_per_sec` 复用 `SessionStats` 派生口径；
   provider 无 usage 时为 `null`（不是 0，也不是 `NaN`）。
3. clock 文本在**任一段缺失**时只跳过该段，不产生 `NaN` / `undefined` / 多余分隔符。
4. 读数能跨刷新存活：落盘 → 历史端点 → 前端 item。
5. 读数不会**贴错消息**：uuid 找不到对应 assistant 时整体放弃。
6. P1 的 copy 行为无回归。

---

## 3. 测试环境

| 项 | 值 |
|----|----|
| JDK / 构建 | JDK 17 + Maven（`mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**`） |
| 前端 | Node + Vitest（`cd agent-web/frontend && npx vitest run`）+ `npx tsc --noEmit` |
| 隔离要求 | 所有落盘用例使用 JUnit `@TempDir`；**不得写真实 `~/.agent-demo/`**（全局规则 §10） |
| 基线对照 | 干净 HEAD `4c4df4d`（本 change 的 main 前身） |

---

## 4. 测试策略

- **后端**：`SseSessionLogSink` 用 Mockito mock `ChatStreamService` 验证「调用顺序」；再用**真实**
  `ChatStreamService` + replay sink 验证「事件顺序与 JSON 字段」——前者证明顺序契约，后者证明真实序列化结果。
- **落盘**：`SessionRecorder` 直接写 `@TempDir` 下的 `SessionStore`，回读 JSONL 断言 `meta` 条目内容。
- **REST**：直接调用 `SessionController.messages(...)`（沿用 `SessionControllerTest` 既有惯例，不起 Spring 容器）。
- **前端**：纯函数（`formatClock` / `attachMetaToTimeline` / `mapHistoryToItems`）单测为主；
  React 组件用 `@testing-library/react` 断言 `data-testid="msg-clock"` 的文本。
- **既有失败归因**：vitest 有 9 条 `EventSource is not defined` unhandled error（`settings-sse` 在 jsdom 下无
  `EventSource`），**必须先在干净 HEAD 复现**再放行，不得直接归为「环境问题」。

---

## 5. 用例矩阵

| 编号 | 层 | 覆盖点 | 优先级 |
|:----:|----|--------|:------:|
| T-01 | 后端 SSE | `message_meta` 先于 `onTurnEnd`（`InOrder`） | P0 |
| T-02 | 后端 SSE | `duration_ms` ≥ 0（无 `onUser` 时也不为负） | P1 |
| T-03 | 后端 SSE | 真实事件流顺序 + 字段（`ttft_ms=500.0` / `tok_per_sec=200.0` / `uuid=null`） | P0 |
| T-04 | 后端 SSE | 无 usage → `ttft_ms` / `tok_per_sec` 为 `null` | P0 |
| T-05 | 后端存储 | 落盘 `meta(message_meta)` 含 uuid / 读数；uuid 与 assistant 条目一致 | P0 |
| T-06 | 后端存储 | 本轮无 assistant → 不写「无主读数」 | P1 |
| T-07 | 后端 REST | assistant 消息带 `uuid` + `meta`，user/tool 两者为 null | P0 |
| T-08 | 后端 REST | 读数 uuid 不在存档里 → 丢弃而非误贴 | P0 |
| T-09 | 后端 REST | 老会话（无 `message_meta`）照常返回、`meta=null` | P1 |
| T-10 | 前端纯函数 | clock 全字段拼装 | P0 |
| T-11 | 前端纯函数 | 缺 `ttft_ms` / 缺 `tok_per_sec` / 缺全部 → 逐段跳过 | P0 |
| T-12 | 前端纯函数 | 时长 / TTFT / tok/s / 时间的边界与非法值 | P1 |
| T-13 | 前端组件 | `MessageActionRow` 渲染 clock（全字段 / 缺段 / 无 meta） | P0 |
| T-14 | 前端组件 | action row 内顺序 copy → children → clock | P1 |
| T-15 | 前端逻辑 | `attachMetaToTimeline` 贴最后一条 assistant（含工具拆条场景） | P0 |
| T-16 | 前端逻辑 | `mapHistoryToItems` 透传 `meta` / `uuid` | P0 |
| T-17 | 前端组件 | P1 copy 回归（成功 / ✓ 1s / 防重入 / 两条降级路径） | P0 |
| T-18 | 类型 | `npx tsc --noEmit` 错误数不超过基线 | P1 |

---

## 6. 退出标准（DoD）

- [x] 上表 P0 用例全部通过，且能给出命令输出为证
- [x] `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` → BUILD SUCCESS（含 jacoco）
- [x] `npx vitest run` 无**新增**失败（既有 9 条 unhandled error 已在干净 HEAD 复现）
- [x] `npx tsc --noEmit` 错误数 ≤ 基线 7
- [x] 落盘用例全部使用 `@TempDir`，且跑后真实 `~/.agent-demo/` 无新增测试残留
- [x] 四件套齐全并登记进 `test-guide.md`
