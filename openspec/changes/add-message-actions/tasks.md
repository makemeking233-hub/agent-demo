# Tasks: add-message-actions

> scope：P1 copy + P2 clock（赞踩拆到 `add-message-feedback`；regenerate 拆到 `add-message-regenerate`）
> 状态：**P1 ✅ + P2 ✅ 全部完成，已归档**

## P1: copy 按钮（前端，~1h）✅ 已完成

- [x] 1.1 新增 `MessageActionRow.tsx`：`[copy] [children] [clock]` row 布局（flex + gap + 半透明 hover）
- [x] 1.2 copy 按钮：`navigator.clipboard.writeText` + 1s ✓ 反馈（`copyPending` ref 防重入 + `copyEpoch` ref 防 unmount setState）
- [x] 1.3 降级路径：`navigator.clipboard` 抛错 → 隐藏 textarea + `document.execCommand('copy')`
- [x] 1.4 `MessageBubble.tsx` 集成：assistant 消息底部渲染 `MessageActionRow`（user 消息只渲染 copy）
- [x] 1.5 `MessageActionRow.module.css`：图标按钮 + hover 态 + ✓/📋 图标切换
- [x] 1.6 `MessageActionRow.test.tsx`：复制成功 / ✓ 1s 后恢复 / 防重入 / 两条降级路径（7 用例）

## P2: per-message clock（全栈，~1.5h）✅ 已完成

### 后端

- [x] 2.1 `SseEvent.java` 加 `MessageMeta` record（`{type:"message_meta", uuid, duration_ms, ttft_ms, tok_per_sec, timestamp}`）
- [x] 2.2 采集 per-turn 时序：`SseSessionLogSink.onUser` 记 wall time 起点、`onAssistant` 记「本轮最后一条 assistant 定稿」时刻；`SessionRecorder` 同源记 uuid 并落盘读数
- [x] 2.3 在 `message_stop` **之前**推送 `message_meta`（实测顺序 `message_meta → turn_stats → message_stop`）
- [x] 2.4 `SseSessionLogSinkTest`：顺序（`InOrder`）/ duration ≥ 0 / 真实事件流字段正确 / 无 usage 时派生指标为 null（4 用例）；`SessionRecorderTest` 补 2 用例验证落盘与「无 assistant 不写无主读数」

### 前端

- [x] 2.5 `event-types.ts` 加 `MessageMeta` 事件；`ChatPanel.handleEvent` 订阅 `message_meta`，经 `attachMetaToTimeline` 把读数（含 uuid）贴到刚定稿的 assistant 消息项
- [x] 2.6 新增 `src/lib/message-clock.ts`：`formatClock` 产出 `{HH:MM} · Ran for {N}s · TTFT {N.N}s · {N} tok/s`（null 段跳过）；`MessageActionRow` 经 `meta` prop 渲染（`data-testid="msg-clock"`）
- [x] 2.7 `MessageBubble` 加 `meta` prop 并透传给 action row（user 消息不传）
- [x] 2.8 历史加载：`SessionRecorder` 回合结束追加 `meta(key="message_meta")` → `SessionController.messages` 按 assistant 序号贴回 `SessionMessageDto.meta` / `uuid` → `mapHistoryToItems` 透传（刷新后 clock 仍在）
- [x] 2.9 测试：`message-clock.test.ts`（18 用例）+ `MessageActionRow.test.tsx` clock 4 用例 + `ChatPanel.test.tsx` 时间线装配 5 用例 + `SessionControllerTest` 3 用例

## 验证与归档

- [x] 4.1 `npx vitest run`：328 passed + 1 skipped / 41 文件（9 个 `EventSource is not defined` 为**基线既有**，已在干净 HEAD `4c4df4d` 上复现同样 9 个）
- [x] 4.2 `npx tsc --noEmit` 错误数 **3** ≤ 基线 7
- [x] 4.3 `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` 全绿（agent-web 386 tests、jacoco 全达标）
- [x] 4.4 中文 Conventional Commits → push `feat/add-message-actions-p2`
- [x] 4.5 `openspec validate add-message-actions --type change --strict` 通过
- [x] 4.6 `openspec archive add-message-actions --yes` 合并 delta spec
- [x] 4.7 合并回 main + 在 main 上复验 + push main + cleanup

## Follow-up（不在本 change 范围，各自独立 change）

| 项 | 去向 |
|----|------|
| 赞踩 + feedback sidecar + per-item CAS（原 P3） | 已拆为 `openspec/changes/add-message-feedback/` |
| regenerate（surface replacement：log 保留 + replacement-origin events + UI + REST，~8h） | 计划 change `add-message-regenerate` |
| 分叉会话（DSH branch 语义，~4h） | 计划 change `add-session-branch` |
| sidecar 清理：session 删除时级联删 `feedback/<sid>.json` | 归 `add-message-feedback` 的 follow-up |

**实施期发现（未修，留给后续 change）**：`SessionEntry.assistant(content, null, parent)` 在 `toolCalls == null`
时因 `Map.of("toolCalls", null)` 抛 NPE，被 `SessionRecorder.safeStore` 静默吞掉 → 该条 assistant 不落盘。
生产路径目前恒传非 null（空 list），故未暴露；本 change 的单测踩到过，已在测试里规避。
建议后续补 `toolCalls == null ? List.of() : toolCalls` 防御 + 回归用例。
