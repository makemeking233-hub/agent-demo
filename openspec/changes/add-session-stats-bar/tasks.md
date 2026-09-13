## 1. Core：Usage 缓存字段

- [ ] 1.1 先写 `StreamChunkTest`/`OpenAiCompatibleMapperTest`（红）：usage 含 `prompt_cache_hit_tokens`/`prompt_cache_miss_tokens` 时解析出 `cacheHitTokens`/`cacheMissTokens`；缺失时为 `null`。
- [ ] 1.2 `StreamChunk.Usage` 增加可空 `cacheHitTokens`/`cacheMissTokens` 字段（保留既有 3 参构造兼容），`OpenAiCompatibleMapper.parseUsage` 读取并填充，测试转绿。

## 2. Core：SessionStats 模型

- [ ] 2.1 先写 `SessionStatsTest`（红）：`plus(TurnDelta)` 累加 turns/steps/tokens/耗时/TTFT 样本/缓存；派生 `avgTtftMs`/`tokPerSec`（纯生成耗时分母）/`cacheHitRate`；边界（分母≤0、无样本、无缓存）返回 `null`。
- [ ] 2.2 实现 `agent-core/.../stats/SessionStats.java`（record + `empty()` + `plus()` + 派生方法）与 `TurnDelta`，测试转绿。

## 3. Core：AgentLoop 采集

- [ ] 3.1 先扩展 `AgentLoopTest`（红）：一轮含 1 次工具调用后，传给 `onTurnEnd` 的统计 `turns=1`、`steps=1`、`llmMillis>0`、`tokPerSec` 可计算；`ttftSamples=1`。
- [ ] 3.2 在 `AgentLoop` 内加计时采集：`streamChat` 前记 t0、首个文本 chunk 记 TTFT、流结束累计 `llmMillis`；工具执行前后累计 `toolMillis` + `steps++`；usage 累加 token 与缓存。`TurnResult` 携带本轮 `TurnDelta`，测试转绿。

## 4. Core：统计落盘与恢复

- [ ] 4.1 先扩展 `SessionStoreTest`（红）：`writeStats/readStats` 读写 `<id>.meta.json{stats}`，且**不破坏**既有 `title`；旧文件（仅 title）读 stats 返回空。
- [ ] 4.2 `SessionStore` 增加 `readStats/writeStats`（与 `readTitle/writeTitle` 同族，复用 id 白名单；读写时合并现有 meta 字段），测试转绿。

## 5. Web：turn_stats 事件与推送

- [ ] 5.1 先扩展 `SseEventTest`（红）：`TurnStats` 事件 `type()=="turn_stats"` 且 JSON 含全部字段（含可空派生值序列化为 null）。
- [ ] 5.2 `SseEvent` 新增 `TurnStats` record；`ChatStreamService` 在回合结束（正常/中断/出错）于 `message_stop` **之前**推送该事件，补 `ChatStreamServiceTest`。

## 6. Web：stats API

- [ ] 6.1 先扩展 `SessionControllerTest`（红）：`GET /api/sessions/{id}/stats` 已知会话返 200（含累计值/空派生）、未知会话返 404 `session_not_found`。
- [ ] 6.2 `SessionController` 新增 stats 端点（复用 `WebAgentRuntime.sessionsDirFor` 工作区路由 + `SessionStore.readStats`），`WebAgentRuntime` 暴露会话级 stats 存取，测试转绿。
- [ ] 6.3 `WebIntegrationTest` 覆盖：一轮 send 后 `turn_stats` 到达 SSE，且 `GET .../stats` 返回非零 turns。

## 7. 前端：底部状态栏

- [ ] 7.1 `api/chat.ts` 加 `sessionStats(sessionId)` 与 `TurnStats` 事件类型；补类型测试。
- [ ] 7.2 新增 `components/StatsBar.tsx`（分段渲染 + 超长省略 + 空值 `N/A`），补 `StatsBar.test.tsx`（渲染、N/A、省略）。
- [ ] 7.3 `ChatPanel.tsx` 在 `Composer` 下方渲染 `StatsBar`：首屏拉 `sessionStats`，`handleEvent` 收到 `turn_stats` 时刷新；`npm run build` 成功、`vitest run` 全绿。

## 8. 文档与验证

- [ ] 8.1 更新 `docs/design/design.md` §6.5 或 §11（用量/统计）补会话统计与 `turn_stats`；`README.md` §10 Web UI 补状态栏说明。
- [ ] 8.2 后端验证：`mvn -o verify -DskipNpm=true -Dtest=!*E2ETest`（非 e2e 全绿 + jacoco）。
- [ ] 8.3 `check-md.sh` 校验本 change 下 md 无 Mermaid 违规。
- [ ] 8.4 提交并推送：中文 `feat(session-stats): ...` 拆分，逐一 commit 即 push。
