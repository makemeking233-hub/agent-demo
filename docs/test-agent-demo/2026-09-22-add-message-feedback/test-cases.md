# 测试用例：add-message-feedback（👍/👎 + sidecar + per-item CAS）

> 来源：用例矩阵见 `test-design.md` §5（本文件给出全量明细，编号与之一致）。
> 落地情况：全部用例已实现并纳入自动化套件。

---

## 1. 存储层（agent-core · `MessageFeedbackStoreTest`，13 用例）

| 编号 | 用例 | 前置 | 步骤 | 预期 |
|:----:|------|------|------|------|
| T-01 | PUT 创建 | `@TempDir` + 新 store | `put("s-1","u-a",UP,null)` | `version==1`、`rating==UP`、`updatedAt>0` |
| T-02 | 重复创建冲突 | 已有 v1 | 再 `put(...,null)` | 抛 `MessageFeedbackVersionConflict`，`current != null` |
| T-03 | ifVersion 匹配 | 已有 v1 | `put(...,DOWN,1)` | 成功，`version==2`、`rating==DOWN` |
| T-04 | ifVersion 不匹配 | 已有 v1 | `put(...,DOWN,99)` | 抛冲突，`current.version==1` |
| T-05 | DELETE 匹配 | 已有 v1 | `delete(...,1)` | `get(...)==null` |
| T-06 | DELETE 不存在 | 无记录 | `delete(...,null)` | 抛冲突，`current == null` |
| T-07 | getAll 排序 | a(v1)、b(v1)、a(v2) | `getAll("s-1")` | 含 a、b 两 key；a 在 b 前（version 降序） |
| T-08 | 非法 id | — | sessionId/messageId 传 `../escape`、`""`、`null` | 抛 `IllegalArgumentException` |
| T-09 | 非法 rating | — | `put(...,null,null)` | 抛 `IllegalArgumentException` |
| T-10 | 未知 session | — | `getAll("nope")` | `items` 为空 |
| T-11 | 并发同一 message | 4 线程同起跑 | 全部 `put("s-1","u-shared",UP,null)` | 1 个成功、3 个 409；终态 `version==1`；无意外异常 |
| T-12 | 并发不同 message | 8 线程同起跑 | 各 `put("s-1","u-msg-i",UP,null)` | 全部成功，`getAll` 有 8 项 |
| T-13 | 跨 session 隔离 | — | s-A 点 UP、s-B 点 DOWN | 各自读回自己的 rating |

## 2. REST 层（agent-web · `FeedbackControllerTest`，13 用例）

| 编号 | 用例 | 步骤 | 预期 |
|:----:|------|------|------|
| T-14 | GET 空 session | `get("s-1")` | 200，`sessionId=="s-1"`，`items` 空 |
| T-15 | GET 有数据 | 先 `store.put` a=UP、b=DOWN，再 `get` | 200，key 为 a/b，rating 为 `up`/`down` |
| T-16 | GET 非法 id | `get("../escape")` | 400 |
| T-17 | PUT happy | 写 session 文件后 `put("s-1","u-m",{up,null})` | 200，body `{rating:"up", version:1, uuid:"u-m"}` |
| T-18 | PUT 非法 rating | `put(...,{maybe,null})` | 400，`error=="rating_invalid"` |
| T-19 | PUT 未知 session | `put("nope",...)` | 404，`error=="session_not_found"` |
| T-20 | PUT 路径穿越 | `put("s-1","../escape",...)` | 400，`error=="message_invalid"` |
| T-21 | PUT 重复创建 | 连续两次 `{up,null}` | 第二次 409，body `current=={rating:"up",version:1}` |
| T-22 | PUT 版本错配 | 已有 v1，`{down,99}` | 409，body 含 `current` |
| T-23 | DELETE 匹配 | 先创建 v1，`delete(...,{1})` | 204，`store.get==null` |
| T-24 | DELETE 不存在 | `delete(...,{null})` | 409，body 含 key `current` 且值为 `null` |
| T-25 | DELETE 版本错配 | 已有 v1，`delete(...,{99})` | 409 |
| T-26 | 快照排序 | a→v2、b→v1 | `getAll` 首 key 为 a |

## 3. 前端 API（`src/api/feedback.test.ts`，14 用例）

| 编号 | 用例 | 预期 |
|:----:|------|------|
| T-27 | `nextRating` 首次 | `(null,"up")→"up"`；`(null,"down")→"down"` |
| T-28 | `nextRating` 取消 | `("up","up")→null`；`("down","down")→null` |
| T-29 | `nextRating` 切换 | `("up","down")→"down"`；`("down","up")→"up"` |
| T-30 | `getFeedback` 解析 | 返回 `items` 映射，`u-1.rating=="up"` |
| T-31 | `getFeedback` 非 2xx | 抛 `getFeedback 500` |
| T-32 | `putFeedback` 请求体 | `{rating:"down", ifVersion:1}`；首次时 `ifVersion:null` |
| T-33 | `putFeedback` 409 | 抛 `FeedbackConflictError`，`current=={rating:"up",version:3,...}` |
| T-34 | `putFeedback` 409 且 current=null | `error.current === null`（对方已删除） |
| T-35 | `putFeedback` 500 | 抛普通 `Error`（前端据此回滚） |
| T-36 | `deleteFeedback` 204 | 视为成功，不抛 |
| T-37 | `deleteFeedback` 409 | 抛 `FeedbackConflictError` |
| T-38 | URL 编码 | 请求 URL 含 `s%201` |

## 4. 前端组件（`MessageActionRow.test.tsx` P3 组，7 用例）

| 编号 | 用例 | 预期 |
|:----:|------|------|
| T-39 | 无 uuid（`rating=undefined`） | 不渲染 `msg-up` / `msg-down` |
| T-40 | 无 `onRate` | 不渲染按钮 |
| T-41 | `rating=null` | 两按钮 `aria-pressed=="false"` |
| T-42 | `rating=up` | `msg-up` pressed、label 为「取消点赞」 |
| T-43 | `rating=down` | `msg-down` pressed、`msg-up` 未 pressed |
| T-44 | 点击回调 | 点 up → `onRate("up")`；点 down → `onRate("down")` |
| T-45 | row 内顺序 | `["msg-copy","msg-up","msg-down","extra","msg-clock"]` |

## 5. 前端接线（`ChatPanel.test.tsx` P3 组，7 用例）

> 渲染方式：先以 `currentSessionId={null}` 挂载再 `rerender` 成 `"s-1"`，模拟真实侧边栏点击
> （直接挂载即传会命中 ChatPanel 的「首次挂载不入内」短路，见 `test-review.md` §3）。

| 编号 | 用例 | 预期 |
|:----:|------|------|
| T-46 | 渲染按钮 | 带 uuid 的 assistant 消息出现 `msg-up`，两钮均未选中 |
| T-47 | 首屏拉取 | GET 返回 `{"u-rate-1": down}` → `msg-down` 高亮 |
| T-48 | 首次点赞 | 点击后请求体 `{rating:"up", ifVersion:null}`，成功后保持高亮 |
| T-49 | 取消 | 已 up 再点 up → DELETE `{ifVersion:1}`，按钮恢复未选中 |
| T-50 | 切换 | 已 up 点 down → PUT `{rating:"down", ifVersion:1}`，down 高亮 |
| T-51 | 500 回滚 | PUT 500 → 最终 `aria-pressed=="false"`（不静默留错） |
| T-52 | 409 调和 | PUT 409 `current={down,v3}` → `msg-down` 高亮、`msg-up` 取消 |

## 6. 未落地 / 后续

| 项 | 原因 |
|----|------|
| 真实浏览器观感（按钮 hover / 暗色主题 / 触屏点按） | 本次只做单测；观感验收留给下一次 Web E2E 批次 |
| 多标签页真实并发（两个浏览器窗同时点赞） | 单测已覆盖「同 JVM 多线程」的等价并发语义；跨进程/跨浏览器留 E2E |
| session 删除时级联删 sidecar | follow-up（本 change 不做） |
