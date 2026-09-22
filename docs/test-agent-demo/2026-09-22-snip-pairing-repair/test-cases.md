# snip-pairing-repair 测试用例

> 来源：`test-design.md` §6 用例矩阵的展开。本文件是可独立追溯的全量用例表。
> 执行日：2026-09-22　分支：`fix/snip-pairing-repair`

---

## 1. 用例总表

| 编号 | 用例名 | 层 | 优先级 | 落地 |
|:----:|--------|:--:|:------:|:----:|
| SR-01 | 裁剪点落在配对组中间不制造孤儿 | L1 | P0 | 已实现 |
| SR-02 | 组对齐后仍在上限内 | L1 | P1 | 已实现 |
| SR-03 | 未超限时不裁剪 | L1 | P1 | 已实现 |
| SR-04 | 裁剪后头部为 `[RESUMED]` 提示 | L1 | P2 | 已实现 |
| TCP-01 | 单个孤儿结果注入骨架 | L2 | P0 | 已实现 |
| TCP-02 | 连续孤儿结果合并为一条骨架 | L2 | P0 | 已实现 |
| TCP-03 | 已有前置调用的结果不受影响 | L2 | P0 | 已实现 |
| TCP-04 | 干净历史逐元素不变 | L2 | P1 | 已实现 |
| TCP-05 | 反向修复幂等 | L2 | P0 | 已实现 |
| TCP-06 | 不修改入参列表 | L2 | P1 | 已实现 |
| TCP-07 | 与正向 repair 组合后双向满足 | L2 | P0 | 已实现 |
| AL-01 | 请求路径同时修复正向与反向 | L4 | P0 | 已实现 |
| GATE-01 | `mvn verify` 全绿 + jacoco 零违规 | L5 | P0 | 已执行 |
| GATE-02 | 前端 vitest 全过 | L5 | P0 | 已执行 |
| GATE-03 | `tsc --noEmit` 不超过基线 | L5 | P1 | 已执行 |

---

## 2. L1：`snip` 裁剪点按配对组对齐

**公共夹具** `groupSplitFixture`（复刻真实会话形状，长度为 5）：

| 下标 | 消息 | 说明 |
|:----:|------|------|
| 0 | `User("历史很长的一轮 " + "x"×800)` | 头部长消息 |
| 1 | `Assistant("我先读这两个文件", toolCalls=[c1, c2])` | 内容刻意非空 |
| 2 | `ToolResult(c1, "y"×400, isError=false)` | 组内结果一 |
| 3 | `ToolResult(c2, "z"×400, isError=false)` | 组内结果二 |
| 4 | `User("最后一句")` | 尾部 |

**裁剪点的控制**：设 `maxTokens = tokens(fixture[2..4])`。逐条丢弃的过程为
`drop=0 → 1 → 2`（下标 2 处恰好等于上限而停止），即裁剪点精确落在**组中间**。
下标 1 的 assistant 内容非空是必要设计——否则 `drop` 会停在下标 1（组起点），边界不被触发。

### SR-01 裁剪点落在配对组中间不制造孤儿（P0）

| 项 | 内容 |
|----|------|
| 前置 | 夹具如上，`maxTokens = tokens(fixture[2..4])` |
| 步骤 | 调用 `SessionResumeLoader.snip(fixture, estimator, maxTokens)` |
| 预期 | 1. 结果首条为 `Message.System`<br/>2. 结果中**不存在**无前置 `tool_calls` 的 `ToolResult`<br/>3. `c1`、`c2` 的结果都不残留（整组丢弃） |
| 判据 | 断言 2 用「顺序扫描 + 已声明 id 集合」判定；断言 3 用 `noneMatch` 检查 id |
| 修复前 | **失败**：`expected: <false> but was: <true>`（存在孤儿） |

### SR-02 组对齐后仍在上限内（P1）

| 项 | 内容 |
|----|------|
| 前置 | 同 SR-01 |
| 步骤 | 取 `snipped.subList(1, size)`（剔除压缩提示）并累加 token |
| 预期 | 累加值 ≤ `maxTokens` |
| 理由 | 组对齐只让裁剪点后移（保留量只减不增），不得重新越界；压缩提示自身不计入上限 |
| 修复前 | 通过（此用例描述的是对齐后仍需保持的性质） |

### SR-03 未超限时不裁剪（P1）

| 项 | 内容 |
|----|------|
| 前置 | `[User("a"), Assistant("b", toolCalls=[]), User("c")]`，`maxTokens = 100000` |
| 步骤 | 调用 `snip` |
| 预期 | 返回值与入参列表**逐元素相等**，且不插入任何 `System` 消息 |

### SR-04 裁剪后头部为 `[RESUMED]` 提示（P2）

| 项 | 内容 |
|----|------|
| 前置 | `[User("x"×5000)]`，`maxTokens = 1` |
| 步骤 | 调用 `snip` |
| 预期 | 首条为 `Message.System` 且 `content` 以 `[RESUMED]` 开头 |

---

## 3. L2：`ToolCallPairing.repairOrphanResults`

### TCP-01 单个孤儿结果注入骨架（P0）

| 项 | 内容 |
|----|------|
| 前置 | `[User("u"), ToolResult(c1, isError=false)]` |
| 步骤 | 调用 `repairOrphanResults` |
| 预期 | 长度为 3；下标 1 为 `Assistant`，其 `toolCalls` 含恰好一个 id `c1`；下标 2 为原 `ToolResult` |
| 修复前 | **编译失败**（方法不存在） |

### TCP-02 连续孤儿结果合并为一条骨架（P0）

| 项 | 内容 |
|----|------|
| 前置 | `[User("u"), ToolResult(c1), ToolResult(c2), User("v")]` |
| 步骤 | 调用 `repairOrphanResults` |
| 预期 | 长度为 5；下标 1 为**唯一一条** `Assistant`，`toolCalls` id 依序为 `[c1, c2]`；下标 2/3 为原两条结果；下标 4 为 `User("v")` |
| 理由 | 还原「一次 assistant 的并行 `tool_calls`」语义，避免每个结果各插一条把历史切碎 |

### TCP-03 已有前置调用的结果不受影响（P0）

| 项 | 内容 |
|----|------|
| 前置 | `[Assistant(toolCalls=[c1]), ToolResult(c1), ToolResult(c2), User("v")]` |
| 步骤 | 调用 `repairOrphanResults` |
| 预期 | 长度为 5；下标 0/1 原样保留；下标 2 为只含 `c2` 的合成骨架；下标 3 为 `ToolResult(c2)` |
| 理由 | 孤儿段在「已有前置调用」的结果处结束，那属于前一组 |

### TCP-04 干净历史逐元素不变（P1）

| 项 | 内容 |
|----|------|
| 前置 | `[Assistant(toolCalls=[c1]), ToolResult(c1)]` |
| 步骤 | 调用 `repairOrphanResults` |
| 预期 | 返回值与入参**逐元素相等** |

### TCP-05 反向修复幂等（P0）

| 项 | 内容 |
|----|------|
| 前置 | 对 `[User("u"), ToolResult(c1)]` 先修复一次得到 `once` |
| 步骤 | 再对 `once` 调用一次 |
| 预期 | 与 `once` 逐元素相等（不新增消息） |
| 理由 | 合成骨架携带结果 id，二次扫描 `hasMatchingCall` 命中 |

### TCP-06 不修改入参列表（P1）

| 项 | 内容 |
|----|------|
| 前置 | 可变 `ArrayList`：`[User("u"), ToolResult(c1)]` |
| 步骤 | 调用 `repairOrphanResults` |
| 预期 | 入参仍为 2 条（纯函数契约） |

### TCP-07 与正向 repair 组合后双向满足（P0）

| 项 | 内容 |
|----|------|
| 前置 | `[ToolResult(c8), Assistant(toolCalls=[c9]), User("u")]` —— 同时含反向孤儿与正向悬挂 |
| 步骤 | `repairOrphanResults(repair(input))` |
| 预期 | 1. `ToolCallPairing.danglingCallIds(结果)` 为空<br/>2. 存在某条 `Assistant` 的 `toolCalls` 含 `c8` |
| 理由 | 直接验证请求路径使用的组合顺序可同时解决两个方向 |

---

## 4. L3：恢复路径回归（既有用例，未新增）

| 编号 | 用例名 | 断言要点 |
|:----:|--------|---------|
| REG-01 | `injectsOrphanSkeletonForOrphanToolResult` | 存档 `[user, tool_result(orphan_1)]` 恢复后下标 1 为合成 `Assistant`，其 `toolCalls[0].id == orphan_1`、`name == resumed_tool` |
| REG-02 | `repairsDanglingToolCallsFromInterruptedTurn` | 正向悬挂仍被补合成错误结果，且插在 assistant 与下一条 user 之间 |
| REG-03 | 其余 7 例 `SessionResumeLoaderTest` | 恢复内容、token、无会话、按 id 装载等 |
| REG-04 | 其余 8 例 `ToolCallPairingTest` | 正向 repair 的注入位置、顺序、幂等、不改入参 |

> REG-01 是本批重构的直接回归目标：`injectOrphanSkeletons` 被删除并改调
> `ToolCallPairing.repairOrphanResults` 后，其可观测行为必须一字不差。

---

## 5. L4：请求路径双向自愈

### AL-01 请求路径同时修复正向与反向（P0）

| 项 | 内容 |
|----|------|
| 前置 | `MessageHistory` 依次 `append`：`User("上一轮")`、`ToolResult(c0, "孤儿结果")`、`Assistant("", toolCalls=[c9])` —— `c0` 是反向孤儿，`c9` 是正向悬挂 |
| 步骤 | 用包装 `LlmProvider` 捕获 `req.messages()`，执行 `processTurn(User("go"))` |
| 预期 | 1. `danglingCallIds(捕获列表)` 为空（正向已补）<br/>2. 捕获列表**无**反向孤儿（已补骨架）<br/>3. 捕获列表是修复不动点：`repairOrphanResults(repair(msgs))` 逐元素等于 `msgs`<br/>4. 内存历史条数为 `原条数 + 2`（本轮 user + assistant），不被修复改写<br/>5. 内存历史中仍保留原始孤儿结果 `c0` |
| 修复前 | **失败**：反向孤儿断言 `expected: <false> but was: <true>` |
| 说明 | 预期 4/5 共同锁定「修复只作用于待发送副本，不写回 history」这一设计选择 |

---

## 6. L5：门禁用例

| 编号 | 命令 | 通过判据 |
|:----:|------|---------|
| GATE-01 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` | `BUILD SUCCESS`；`agent-core` 与 `agent-web` 均 `Failures: 0, Errors: 0`；jacoco check 无违规 |
| GATE-02 | `npx vitest run`（`agent-web/frontend`） | `Test Files` 全过、`Tests` 全过 |
| GATE-03 | `npx tsc --noEmit` | `error TS` 行数 ≤ 基线 7 |

---

## 7. 未落地 / 未覆盖项

| 项 | 原因 | 影响 |
|----|------|------|
| 真实 DeepSeek 400 的端到端复现 | 需真实 key + 网络；且要构造 20 万 token 级历史 | 用探针复现链路 + 离线断言协议形态替代，见 `test-report.md` §4 |
| CLI 侧 `ChatCommand` 的 `snip` 调用点 | 与 `WebAgentRuntime` 共用同一个 `SessionResumeLoader.snip` | 纯函数层修复自然覆盖两个调用点 |
| 前端相关断言 | 本 change 零前端改动 | 不适用 |
