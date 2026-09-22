# 测试设计：add-message-feedback（👍/👎 + sidecar + per-item CAS）

> 批次目录：`docs/test-agent-demo/2026-09-22-add-message-feedback/`
> 对应 change：`openspec/changes/add-message-feedback/`
> 执行日期：2026-09-22
> 规范：`AGENTS.md §2.6`（四件套）、`§2.7.5`（合并门禁）

---

## 1. 测试范围

| 层 | 被测对象 | 是否新增 |
|----|---------|---------|
| 存储（agent-core） | `MessageFeedbackStore`：sidecar 读写 / 0600-0700 权限 / 文件锁 / per-item version CAS | 新增 |
| 存储（agent-core） | `AgentPaths.feedbackDir()` | 新增 |
| REST（agent-web） | `FeedbackController` GET/PUT/DELETE + 错误码 + 4 个 DTO | 新增 |
| 装配（agent-web） | `WebRuntimeConfig.webMessageFeedbackStore()` bean | 新增 |
| 前端 API | `api/feedback.ts`：3 个 fetch 封装 / `FeedbackConflictError` / `nextRating` | 新增 |
| 前端组件 | `MessageActionRow` 👍/👎 按钮（两态互斥 / 无 uuid 不渲染） | 新增（P1/P2 回归） |
| 前端接线 | `ChatPanel`：首屏拉取 / 乐观更新 / 失败回滚 / 409 调和 | 新增 |

**不在范围**：regenerate（独立 change）、反馈备注文本（用户明确只要两态）、session 删除时级联删 sidecar（follow-up）、真实浏览器 E2E。

---

## 2. 测试目标

1. **CAS 语义精确**：`ifVersion=null` = 必须不存在；`ifVersion=N` = 必须等于 N；冲突必须带 `current`
   （且 `current=null` 能表达「对方已删除」）。
2. **并发不损坏 sidecar**：同 JVM 多线程并发 PUT 同一 message → 串行化，只有一个赢、其余拿到 409；
   并发 PUT 不同 message → 全部成功不丢数据。
3. **对模型不可见**：sidecar 不进 `session.jsonl`；不参与 `AgentLoop.toRequest()`。
4. **路径穿越被挡**：`sessionId` / `messageId` 白名单校验，`../escape` 一律 400。
5. **前端两态互斥 + 可取消**：无 → 👍 → 👍（取消）；👍 → 👎（切换）。
6. **前端不静默留错**：500 回滚；409 以服务端 `current` 为准。
7. **按钮只在能落地时出现**：无 `uuid` / 无会话 → 不渲染赞踩（copy 与 clock 不受影响）。
8. **测试不污染用户数据**：`~/.agent-demo/feedback/` 必须零写入。

---

## 3. 测试环境

| 项 | 值 |
|----|----|
| JDK / 构建 | JDK 17 + Maven（`mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**`） |
| 前端 | Node + Vitest（`cd agent-web/frontend && npx vitest run`）+ `npx tsc --noEmit` |
| 隔离要求 | 后端一律 `@TempDir`；前端一律 `vi.stubGlobal("fetch", ...)`（不发真实请求） |
| 基线对照 | 干净 HEAD `9ad08c1`（本 change 的 main 前身） |
| 注意 | 跑 Maven 必须带 `-DskipNpm=true`（否则 frontend-maven-plugin 的 `npm ci` 会删坏 `node_modules`）；主工作区若有 `java -jar agent-web.jar` 在跑，需加 `-Dspring-boot.repackage.skip=true` |

---

## 4. 测试策略

- **存储层**：`@TempDir` + 真实文件系统跑真锁真写。并发用 `CountDownLatch` 同时起跑 + `Thread.join`，
  断言「赢家数 = 1、冲突数 = 线程数 - 1」，避免只测单线程 happy path。
- **REST 层**：直接调控制器方法（沿用 `SessionControllerTest` 惯例，不起 Spring 容器），
  `@TempDir` 建 `sessions/` 让 `runtime.hasSession` 成立。
- **前端 API 层**：`nextRating` 是纯函数，四态穷举；fetch 封装按状态码断言错误类型与请求体。
- **前端接线层**：路由式 fetch 桩（按 URL + method 分派），模拟「用户点侧边栏切到 s-1」的真实流程
  （先 null 挂载再 rerender），避免踩到「挂载即传 sessionId 会短路」的既有行为。
- **既有失败归因**：vitest 的 9 条 `EventSource is not defined` 为既存，**必须先在干净 HEAD 复现**再放行。

---

## 5. 用例矩阵

| 编号 | 层 | 覆盖点 | 优先级 |
|:----:|----|--------|:------:|
| T-01 | 存储 | PUT 创建 → version=1、rating 正确 | P0 |
| T-02 | 存储 | 重复 `ifVersion=null` → 409 且 current 非空 | P0 |
| T-03 | 存储 | `ifVersion` 匹配 → 成功且 version 自增 | P0 |
| T-04 | 存储 | `ifVersion` 不匹配 → 409 且 current.version 为真值 | P0 |
| T-05 | 存储 | DELETE 版本匹配 → 记录消失 | P0 |
| T-06 | 存储 | DELETE 不存在 → 409 且 `current == null` | P0 |
| T-07 | 存储 | `getAll` 含全部 key 且按 version 降序 | P1 |
| T-08 | 存储 | sessionId / messageId 非法（`../escape`、空、null）→ `IllegalArgumentException` | P0 |
| T-09 | 存储 | `rating == null` → `IllegalArgumentException` | P1 |
| T-10 | 存储 | 未知 session → 空 snapshot | P1 |
| T-11 | 存储 | **并发 PUT 同一 message**：1 赢 + (N-1) 冲突、终态 version=1 | P0 |
| T-12 | 存储 | 并发 PUT 不同 message：全部落盘不丢 | P0 |
| T-13 | 存储 | 跨 session sidecar 相互独立 | P1 |
| T-14 | REST | GET 空 session → 200 `{items:{}}` | P0 |
| T-15 | REST | GET 有数据 → 200 且 rating 正确 | P0 |
| T-16 | REST | GET 非法 sessionId → 400 | P0 |
| T-17 | REST | PUT happy path → 200 `{rating,version,uuid}` | P0 |
| T-18 | REST | PUT 非法 rating → 400 `rating_invalid` | P0 |
| T-19 | REST | PUT 未知 session → 404 `session_not_found` | P0 |
| T-20 | REST | PUT 路径穿越 messageId → 400 `message_invalid` | P0 |
| T-21 | REST | PUT 重复创建 → 409 且 body 含 `current{rating,version}` | P0 |
| T-22 | REST | PUT 版本错配 → 409 | P0 |
| T-23 | REST | DELETE 匹配 → 204 且已删除 | P0 |
| T-24 | REST | DELETE 不存在 → 409 且 `current == null` | P0 |
| T-25 | REST | DELETE 版本错配 → 409 | P0 |
| T-26 | 前端 API | `nextRating` 四态（首次 up / 首次 down / 取消 / 切换） | P0 |
| T-27 | 前端 API | `getFeedback` 解析 / 非 2xx 抛错 | P0 |
| T-28 | 前端 API | `putFeedback` 请求体（ifVersion null 与数字） | P0 |
| T-29 | 前端 API | `putFeedback` 409 → `FeedbackConflictError` 携带 current（含 current=null） | P0 |
| T-30 | 前端 API | `putFeedback` 500 → 普通 Error | P0 |
| T-31 | 前端 API | `deleteFeedback` 204 成功 / 409 冲突 | P0 |
| T-32 | 前端 API | 路径参数 URL 编码 | P1 |
| T-33 | 组件 | `rating=undefined` 或无 `onRate` → 不渲染按钮 | P0 |
| T-34 | 组件 | `rating=null` → 两按钮均未选中 | P0 |
| T-35 | 组件 | `rating=up` / `down` → 对应按钮 `aria-pressed=true`、label 变「取消…」 | P0 |
| T-36 | 组件 | 点击回调带正确 rating | P0 |
| T-37 | 组件 | row 内顺序：copy → up → down → children → clock | P1 |
| T-38 | 接线 | 带 uuid 的 assistant 消息渲染赞踩且初始未选中 | P0 |
| T-39 | 接线 | 首屏拉取已有 feedback 并高亮 | P0 |
| T-40 | 接线 | 点击 👍 首次创建 → PUT `ifVersion=null` | P0 |
| T-41 | 接线 | 已 👍 再点 👍 → DELETE `ifVersion=N` | P0 |
| T-42 | 接线 | 已 👍 点 👎 → PUT 切换（带当前 version） | P0 |
| T-43 | 接线 | PUT 500 → 回滚为未选中 | P0 |
| T-44 | 接线 | PUT 409 → 用 `current` 调和（对方 👎 后本地也变 👎） | P0 |
| T-45 | 类型 | `npx tsc --noEmit` 错误数 ≤ 基线 | P1 |
| T-46 | 隔离 | `~/.agent-demo/feedback/` 不存在（零污染） | P0 |

---

## 6. 退出标准（DoD）

- [x] 上表 P0 用例全部通过，且能给出命令输出为证
- [x] `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` → BUILD SUCCESS（含 jacoco）
- [x] `npx vitest run` 无**新增**失败（既有 9 条 unhandled error 已归因）
- [x] `npx tsc --noEmit` 错误数 ≤ 基线
- [x] 隔离审计：`~/.agent-demo/feedback/` 不存在；无任何用户数据需要删除
- [x] 四件套齐全并登记进 `test-guide.md`
