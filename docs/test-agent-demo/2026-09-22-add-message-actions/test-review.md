# 测试复盘：add-message-actions（P1 copy + P2 per-message clock）

> 批次：`docs/test-agent-demo/2026-09-22-add-message-actions/`
> 复盘时间：2026-09-22（测试执行当日）
> 四件套：`test-design.md` / `test-cases.md` / `test-report.md` / 本文件

---

## 1. 流程回顾

| 阶段 | 动作 | 耗时（估） |
|------|------|:---------:|
| 侦察 | 读 `SseEvent` / `SseSessionLogSink` / `ChatStreamService` / `SessionRecorder` / `SessionStore` / `SessionController` / `ChatPanel` / `MessageActionRow`，确认 uuid 与 timing 的可得性 | ~35min |
| 设计收敛 | 发现原设计三处不可照做（见 §3），改为「采集拆两处 + 按轮贴最后一条 + 缺段省略」 | ~15min |
| 后端实现 | `MessageMeta` 事件、`SessionRecorder` uuid + 落盘、`SseSessionLogSink` 时序、`ChatStreamService.emitMessageMeta`、`WebAgentRuntime.recorderIfPresent`、`SessionController` 历史回填 | ~60min |
| 前端实现 | `message-clock.ts`、`event-types`、`MessageActionRow`、`MessageBubble`、`ChatPanel` | ~40min |
| 测试 | 后端 9 条 + 前端 27 条；两次踩坑修复 | ~45min |
| 门禁与文档 | mvn verify / vitest / tsc、openspec 校验与归档拆分、四件套 | ~50min |

---

## 2. 做得好的

1. **先确认数据可得性再动手**：一上来就追 `ttft` / `tok_per_sec` 到底在哪产生（`TurnDelta` → `SessionStats`），
   避免了「照设计书写代码、写完发现拿不到数据」的返工。
2. **顺序类断言用了两套手段**：Mockito `InOrder`（证明调用顺序契约）+ 真实 `ChatStreamService` replay sink
   （证明序列化后的真实事件序与字段）。前者快、后者真，互补。
3. **既有失败先归因再放行**：vitest 的 `Errors 9` 不是靠「看着像环境问题」放过的，而是 `git stash` 回干净
   HEAD 跑出**同样的 9 条**才放行——这正是 §2.7.5 门禁 5 要求的证据形态。
4. **顺手把 change 边界切干净**：P3（赞踩 + sidecar）存储/并发语义独立，归档时拆成
   `add-message-feedback`，既满足「已完成才归档」，又给下次 session 留了可直接 apply 的 change。

## 3. 问题与根因

| # | 问题 | 根因 | 修复 |
|:-:|------|------|------|
| P-1 | 原设计「`onAssistant` 采集并发送 `message_meta`」实现不了 | `ttft_ms` / `tok_per_sec` 来自 `TurnResult.delta()`，只有 `onTurnEnd` 才有 | 采集拆到 `onUser`（起点）+ `onAssistant`（定稿时刻），发送移到 `onTurnEnd`；契约（在 `message_stop` 之前）不变 |
| P-2 | 原设计「前端按 uuid 存 `Map<uuid, MessageMeta>`」对不上渲染模型 | `ChatPanel` 的一轮可能因工具调用拆成多条 item，而事件是**按轮**下发的 | 改为 `attachMetaToTimeline` 贴到本轮最后一条 assistant；uuid 仍写入 item 供后续赞踩用 |
| P-3 | 单测 `SessionRecorderTest` 挂 `No value present` | `SessionEntry.assistant("答", null, null)` → `Map.of("toolCalls", null)` 抛 NPE，被 `safeStore` 吞掉 → 该条不落盘 | 测试改用 `List.of()`；**生产侧潜在缺陷 D-1 记录在案未修**（超范围，已写入 Follow-up） |
| P-4 | `mvn test` 触发 `npm ci` 失败并删坏 `node_modules` | frontend-maven-plugin 默认执行 `npm ci`，原生模块 `.node` 被占用 → `EPERM -4048`，且 npm 先删后装 | 门禁命令必须带 `-DskipNpm=true`；已破坏的目录用 `npm install` 就地修复 |
| P-5 | `edit` 工具两次报「old_string 未找到」 | 文件**末行无换行**，而 `old_string` 以换行结尾 | 改用不含末尾换行的锚点，或直接用 `write` 整体重写 |
| P-6 | `git stash pop` 后文件变成 CRLF，多行 `edit` 匹配失败 | 本仓库 `core.autocrlf=true`，checkout 会把 LF 转 CRLF | 批量把改动文件规范回 LF（脚本 `CRLF→LF`），后续 edit 恢复正常 |

## 4. 可改进

1. **前端 E2E 缺口**：本批次 clock 只有单测，真实浏览器里的 hover 透明度、暗色主题、窄屏换行没验。
   下次 Web E2E 批次应把「发一条消息 → 看底部 clock 文本」纳入。
2. **`@TempDir` 之外的隔离**：`SessionControllerTest` 用 `cfgNoLogging()` 关日志，但 `WebAgentRuntime`
   仍会按需建 sessions 目录——本次靠注入 `tmp` 保证，属既有惯例；后续可在 `@BeforeEach` 断言
   `AgentPaths.agentHome()` 指向临时目录，把「没污染」变成可失败的门禁。
3. **test-guide 登记滞后**：09-19/09-22 有若干批次未登记（本批次登记时才发现）。建议在 §2.7.5 门禁里
   加一条「test-guide 已登记」。
4. **tsc 基线口径**：AGENTS.md §2.7.7 写 7，实测 3。文档与实现漂移，应在下次改 §2.7.7 时改为「实测值 +
   波动原因」而不是固定数字。

## 5. 交付物

| 类型 | 路径 |
|------|------|
| 后端（4 处） | `SseEvent.java`（`MessageMeta`）、`SseSessionLogSink.java`、`ChatStreamService.java`、`WebAgentRuntime.java` |
| 后端（存储/REST） | `SessionRecorder.java`、`SessionController.java`、`SessionMessageDto.java` |
| 前端（新增） | `src/lib/message-clock.ts`、`src/lib/message-clock.test.ts` |
| 前端（修改） | `MessageActionRow.tsx`、`MessageBubble.tsx`、`ChatPanel.tsx`、`api/chat.ts`、`lib/event-types.ts` |
| 测试（后端） | `SseSessionLogSinkTest`（+4）、`SessionRecorderTest`（+2）、`SessionControllerTest`（+3） |
| 测试（前端） | `message-clock.test.ts`（18）、`MessageActionRow.test.tsx`（+4）、`ChatPanel.test.tsx`（+5） |
| OpenSpec | `add-message-actions`（已归档）、`add-message-feedback`（新建，承接原 P3） |
| 四件套 | 本目录 |
