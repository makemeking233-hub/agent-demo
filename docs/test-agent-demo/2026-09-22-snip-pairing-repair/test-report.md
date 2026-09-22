# snip-pairing-repair 测试报告

> 执行日：2026-09-22　分支：`fix/snip-pairing-repair`　worktree：`.worktrees/snip-pairing-repair`
> 设计依据：`test-design.md`　用例依据：`test-cases.md`

---

## 1. 结论摘要

| 项 | 结果 |
|----|------|
| P0 用例 | 全部通过（SR-01、TCP-01/02/03/05/07、AL-01） |
| 既有回归 | 全部通过（`SessionResumeLoaderTest` 13、`ToolCallPairingTest` 15、`AgentLoopToolPairingTest` 2） |
| 每个实现步骤先红后绿 | 3 / 3 均有实测红输出留痕（见 §3） |
| 质量门禁命令 | `BUILD SUCCESS`（分支上与合并 `main` 后各一次） |
| 前端 vitest | `40 files / 302 tests` 全部通过 |
| `tsc --noEmit` | 6 个错误 ≤ 基线 7 |
| 用户真实数据 | 未读写（surefire 注入隔离根；`snip` 用例全在内存构造） |
| 真实会话复验 | 修复后在**同一条真实坏会话**上重跑探针：`snip` 后反向孤儿 **1 → 0**，保留列表从配对组起点开始（见 §4.1） |
| 结论 | **本 change 的 3 个目标（G1/G2/G3）达成；G4 达成但有附带发现（见 §6）** |

---

## 2. 执行环境

| 项 | 值 |
|----|-----|
| 分支起点 | `a5f1088` |
| 合并 `main` 后 | `8b60312`（无冲突） |
| 门禁命令 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` |
| 前端 | `npx vitest run` / `npx tsc --noEmit`（`agent-web/frontend`） |
| JDK | 编译目标 17，运行时 24.0.1 |
| 数据隔离 | surefire 注入 `agent.demo.home=<module>/target/test-home`；本批新增用例不使用文件系统 |

**worktree 前置准备**：`agent-web/src/main/resources/static/` 是 gitignored 构建产物，新 worktree 里不存在，
会让 `WebIntegrationTest.rootServesIndexHtml` 假失败。跑门禁前从主工作区拷贝该目录（206 个文件）到 worktree；
合并 `main` 后（main 的前端被并行 agent 重构过）重新拷贝一次。

---

## 3. 执行结果（TDD 三步，先红后绿）

### 3.1 步骤 ①：`snip` 裁剪点按配对组对齐

| 阶段 | 命令 | 结果 |
|------|------|------|
| 红 | `mvn -o -pl agent-core test -Dtest=SessionResumeLoaderTest` | `Tests run: 13, Failures: 1` |
| 绿 | 同上（实现后） | `Tests run: 13, Failures: 0` |

红的原文（唯一失败）：

```text
[ERROR] SessionResumeLoaderTest.snipDoesNotSplitToolCallGroup:249
裁剪不得制造无前置 tool_calls 的 tool_result（否则发出去必被上游 400）
  ==> expected: <false> but was: <true>
```

说明：新增的 4 个用例中只有 SR-01 在修复前失败——这是**预期**的，因为 SR-02/03/04 描述的是
「对齐后仍需保持的性质」与既有正确行为。这一点被显式记录，避免把它误读成「新用例大部分无效」。

### 3.2 步骤 ②a：`ToolCallPairing.repairOrphanResults`

| 阶段 | 命令 | 结果 |
|------|------|------|
| 红 | `mvn -o -pl agent-core test-compile` | 编译失败：`找不到符号 方法 repairOrphanResults(java.util.List<...Message>)`（6 处） |
| 绿 | `mvn -o -pl agent-core test "-Dtest=ToolCallPairingTest,SessionResumeLoaderTest"` | `ToolCallPairingTest 15/15`、`SessionResumeLoaderTest 13/13`，合计 `Tests run: 28, Failures: 0` |

`SessionResumeLoaderTest` 13/13 是本步最关键的回归信号：`injectOrphanSkeletons` 被删除并改调
`ToolCallPairing.repairOrphanResults` 后，REG-01（`resumed_tool` 骨架名与 id）等既有断言一字未改地通过。

### 3.3 步骤 ②b：`AgentLoop.toRequest` 每请求双向自愈

| 阶段 | 命令 | 结果 |
|------|------|------|
| 红 | `mvn -o -pl agent-core test -Dtest=AgentLoopToolPairingTest` | `Tests run: 2, Failures: 1` |
| 绿 | 同上（实现后） | `Tests run: 2, Failures: 0` |

红的原文：

```text
[ERROR] AgentLoopToolPairingTest.requestRepairsBothPairingDirectionsOnDirtyHistory
反向孤儿应已补齐骨架，否则发出去必被上游 400
  ==> expected: <false> but was: <true>
```

这一条直接证明了缺陷的真实性：**修复前，孤儿 tool 消息确实原样进入了发往上游的消息列表**。

### 3.4 门禁

| 阶段 | agent-core | agent-web | jacoco check | 结论 |
|------|:----------:|:---------:|:------------:|------|
| 分支上（基线 `a5f1088`） | `545 / 0` | `379 / 0` | 无 FAILURE | `BUILD SUCCESS`，日志 `[ERROR]` 行数 = 0 |
| 合并 `main`（`8b60312`）后 | `545 / 0` | `379 / 0` | 无 FAILURE | `BUILD SUCCESS`，日志 `[ERROR]` 行数 = 0 |

两次门禁的命令与参数完全一致，仅有 `main` 基线不同（符合 §2.7.5.1 门禁 4 的要求）。

---

## 4. 根因复现（真实会话，不是推断）

缺陷不是从日志猜出来的，而是用探针逐步执行真实恢复链路得到的。目标会话
`2026-09-18T13-34-27-b1569b39`（存档 687194 字节 / 215 行）的实测过程：

| 阶段 | 消息数 | 正向悬挂 | 反向孤儿 | 估算 token |
|------|:------:|:--------:|:--------:|:----------:|
| 盘上存档 → `toMessages` | 204 | 0 | 0 | 201855 |
| → `snip`（上限 100000） | 75 | 0 | **1** | — |

判别式很清晰：**存档干净，是 `snip` 制造了孤儿**。裁剪丢弃 129 条，边界落在
`assistant(tool_calls)` 与其 `tool_result` 之间，父 assistant 被丢、结果被留。

与用户日志的对应关系：

| 日志 | 含义 |
|------|------|
| `[400 /v1/chat/completions] Messages with role 'tool' must be a response to a preceding message with 'tool_calls'` | 反向不变式被破坏（本次修复目标） |
| `03:27:07 WARN ToolCallPairing - 历史修复：为 2 个未配对的 tool_call 补了合成错误结果` | **正向**不配对，是 400 失败后下一轮的连带产物，与本次 400 是两回事 |

> 探针本身是临时产物，已在 §8 说明归宿；测试用例不依赖该会话文件，改用等价构造序列
> （`groupSplitFixture`），符合全局规则 §10「不得用真实用户数据做夹具」。

### 4.1 修复后在**同一条真实会话**上复验（最强证据）

修复合并进 `main`（`fcd1104`）后，把同一份探针编译到**修复后的** `agent-core/target/classes`，
对**同一个真实存档**重跑：

```text
== 盘上归档经 toMessages 后 ==
  消息数            = 206
  正向悬挂          = 0
  反向孤儿          = 0
  估算总 token      = 201937（上限 100000）
== snip 之后（= 实际进 history 的列表）==
  消息数            = 76（丢掉 130 条）
  正向悬挂          = 0
  反向孤儿          = 0
  前 6 条类型：
    [0] System
    [1] Assistant toolCalls=2
    [2] ToolResult toolCallId=call_00_r0sCChJ1qHPbQWQvh34i3685
    [3] ToolResult toolCallId=call_01_tECZjtR4qSaMMMTLcptz6468
    [4] Assistant toolCalls=1
    [5] ToolResult toolCallId=call_00_1VPW3GLvd4vbPxhHXO545574
```

修复前后对照：

| 指标 | 修复前 | 修复后 |
|------|:------:|:------:|
| `snip` 后反向孤儿 | **1** | **0** |
| 保留列表首条（System 之后） | `ToolResult`（孤儿） | `Assistant(toolCalls=2)`，后面紧跟其两条结果 |
| `snip` 后消息数 | 75 | 76 |

**结论**：裁剪点已落在配对组的边界上——保留列表从一个 assistant 及其完整结果块开始，
不再出现「结果在、父 assistant 不在」的形态。这正是上游 400 的直接诱因，现已消除。

两点如实说明：

| 项 | 说明 |
|----|------|
| 两次运行的输入不完全相同 | 存档从 687194 字节长到 688421 字节（消息数 204 → 206）——用户的应用仍在向该会话写入；因此不宜逐下标对比裁剪位置，上表只对比**不变式指标** |
| 探针的边界行号有 off-by-one | 探针用 `msgs.size() - snipped.size()` 推算裁剪下标，未扣除注入的 `[RESUMED]` System 消息，故打印的下标偏小 1。这是探针的展示缺陷，不影响 `反向孤儿` 的计数（该计数直接扫描消息列表） |

---

## 5. 覆盖率（实测）

数据源：`agent-core/target/site/jacoco/jacoco.csv`（门禁运行产物）。

### 5.1 本批改动的三个类

| 类 | LINE | BRANCH |
|----|:----:|:------:|
| `ToolCallPairing`（新增 `repairOrphanResults`） | 67/69 = **0.971** | 59/72 = **0.819** |
| `SessionResumeLoader`（`snip` 组对齐 + 删私有方法） | 68/77 = **0.883** | 40/60 = 0.667 |
| `AgentLoop`（`toRequest` 双向修复） | 286/306 = **0.935** | 91/122 = 0.746 |

`SessionResumeLoader` 的 BRANCH 0.667 主要落在本批未触及的分支（`parseToolCalls` 的
非法输入分支、`intOf`/`boolVal` 的类型分支）。

### 5.2 项目整体

| 口径 | LINE | BRANCH |
|------|:----:|:------:|
| agent-core 全 bundle | 3427/4434 = 0.7729 | 1369/2201 = 0.6220 |
| agent-core 按 pom 里 6 个 include 前缀（含子包） | 1522/1897 = 0.8023 | 703/1065 = 0.6601 |
| agent-core 按 pom 里 6 个 include 前缀（不含子包） | 957/1176 = 0.8138 | 450/697 = 0.6456 |

---

## 6. 附带发现（超出本 change 范围，**未修复**）

### 6.1 现象

门禁命令打印：

```text
--- jacoco-maven-plugin:0.8.11:check (check-coverage) @ agent-core ---
[INFO] Analyzed bundle 'agent-core' with 185 classes
[INFO] All coverage checks have been met.
```

但按 pom 中配置的 include 集合独立复算，BRANCH 只有 0.6456 ~ 0.6601，**低于**配置的 `0.70`。
即：**规则声称通过，而按它的口径算并不达标**。

### 6.2 决定性实验

把 `agent-core/pom.xml` 里 BRANCH 阈值从 `0.70` 临时改为 `0.99`（一个必然失败的阈值），
重跑 `mvn -o -pl agent-core jacoco:check@check-coverage`：

```text
[INFO] Analyzed bundle 'agent-core' with 185 classes
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

阈值抬到 0.99 仍然通过 → 该规则**匹配不到任何被考核对象，是空转的**。

对 `agent-web` 做同样实验（LINE 阈值 `0.80` → `0.99`）：

```text
[WARNING] Rule violated for package com.example.agent.web.security: lines covered ratio is 0.86, but expected minimum is 0.99
[WARNING] Rule violated for package com.example.agent.web.api: lines covered ratio is 0.81, but expected minimum is 0.99
...（共 9 条）
[INFO] BUILD FAILURE
```

agent-web **会**拦。

### 6.3 根因推断

| 模块 | `element` | `includes` 模式 | 实际过滤对象 | 结果 |
|------|-----------|----------------|-------------|------|
| agent-core | `BUNDLE` | `com.example.agent.provider.*` 等 6 个**类/包名前缀** | **bundle 名**（本例为 `agent-core`） | 模式匹配不到 → 0 个考核对象 → 空转通过 |
| agent-web | `PACKAGE` | `com.example.agent.web.*` | **包名** | 正确匹配 → 真实考核 |

即 `includes` 过滤的是「被考核元素的名称」，`BUNDLE` 元素下它过滤的是 bundle 名，而不是类名。
agent-core 想做的「按 6 个包聚合考核」在当前写法下从未生效。

佐证：同文件中 agent-web 的 `<excludes>` 用的是斜杠形式 `com/example/agent/web/WebApplication.*`，
而规则内的 `<includes>` 用点号形式，两种写法混用本身也是易错点。

### 6.4 影响与处置

| 项 | 说明 |
|----|------|
| 影响 | agent-core 的「LINE ≥ 0.80 / BRANCH ≥ 0.70」门禁**从未真正执行**；agent-core 当前实际水平（bundle LINE 0.7729 / BRANCH 0.6220）低于该标准 |
| 历史记录 | **2026-08-29 的首次全面测试就已记录**「jacoco 覆盖率门禁引用已废弃包 `com.example.agent.agent.*` 导致门禁失守」（见 `test-guide.md` §2.1），但此后一直未修复 |
| 本次修正 | 当时把原因归为「其中一个 include 引用了已废弃包」。本次阈值抬升实验表明**范围比那更大**：整条 BUNDLE 规则匹配不到任何对象，不只是 `agent.agent.*` 那一条——6 个 include 全都无效 |
| 本次处置 | **不修复**。修好规则会让构建立刻变红（现有覆盖率不达标），需要一次专门的覆盖率补测 change，属独立议题，不应混进本次 bugfix |
| 两处 pom 状态 | 实验改动已 `git checkout --` 还原，`git diff HEAD` 为空（已核验） |
| 建议 | 单开 change：把 agent-core 规则改为 `<element>PACKAGE</element>` + 逐包阈值，或去掉 `includes` 改用整 bundle 口径并同步下调/补足覆盖率；同时统一 includes/excludes 的路径写法 |

> 本 findings 不改变本次结论：本 change 的正确性由 §3 的先红后绿与全量单测通过保证，
> 而不是由这个空转的覆盖率门禁保证。

---

## 7. 缺陷清单

| 编号 | 描述 | 严重度 | 状态 |
|:----:|------|:------:|------|
| D-01 | `snip` 逐条裁剪会把 `assistant(tool_calls)` 与其结果切断，制造反向孤儿 → 上游 400 → 会话永久不可用 | **P0** | 已修复（步骤 ①） |
| D-02 | 反向配对修复只在恢复路径执行一次，请求路径没有兜底，孤儿可长期存活 | **P1** | 已修复（步骤 ②） |
| D-03 | 连续孤儿结果各插一条骨架会把历史切碎（实现质量） | P2 | 已改善（合并为一条骨架） |
| D-04 | `agent-core` 覆盖率门禁空转（§6） | **P1** | **未修复**（超范围，建议独立 change） |
| D-05 | 前端 `9 unhandled errors`（EventSource）导致 vitest 退出码 1 | P2 | 既有问题，非本批引入 |

---

## 8. 归属判定（failure attribution）

本批跑门禁时遇到过一次失败与若干噪声，逐条给出判定依据：

| 现象 | 判定 | 依据 |
|------|------|------|
| 首轮前端 `FAIL src/components/_axe-debug.test.tsx`（`Test Files 1 failed`） | **非本 change** | 1) `git ls-files` 查无此文件（未入库）；2) 复跑同路径报 `No test files found`——已在两次运行之间被其他 agent 删除；3) `git diff main...HEAD -- agent-web/frontend` 为空 |
| 重跑前端 `40 passed / 302 passed` | 通过 | 该失败随临时文件消失而消失，与我的改动无因果关系 |
| `Errors 9 errors`（unhandled） | 既有问题 | 本分支零前端改动 |
| agent-core `545/0` 与合并前一致 | 正常 | `main` 的并行改动是前端重构（shadcn/Tailwind），未新增 Java 测试 |
| 门禁日志 `[ERROR]` 行数 = 0 | 通过 | 逐行统计，无编译错误、无测试失败 |

---

## 9. 未覆盖项

| 项 | 原因 | 风险 |
|----|------|------|
| 真实 DeepSeek 端到端 400 复现 | 需真实 key、网络与 20 万 token 级历史 | 已用探针复现链路（并在 §4.1 对真实会话做了修复后复验）+ 离线断言协议形态替代；残留风险是「上游还有别的拒绝形态」 |
| `ChatCommand`（CLI）侧的 `snip` 调用点 | 与 web 共用同一个纯函数 | 无（纯函数层修复自然覆盖） |
| 已落盘坏存档的迁移改写 | 设计选择不写回存档 | 旧会话每次恢复仍需现场修复一次，但不再 400 |
| agent-core 覆盖率门禁有效性 | 见 §6，独立议题 | 本批改动的覆盖数字已逐类给出，不依赖该门禁背书 |

---

## 10. 数据隔离与清理（全局规则 §10）

| 项 | 说明 |
|----|------|
| 是否读写用户真实数据目录 | **否**。本批新增的 12 个用例全部在内存中构造消息列表，不落盘；`snip` 与 `repairOrphanResults` 都是纯函数，无 IO |
| surefire 隔离 | `agent-core`/`agent-web` 的 surefire 配置注入 `agent.demo.home=<module>/target/test-home`，测试产生的日志与会话落在各模块 `target/` 下 |
| 探针产物 | 上一轮定位用的 `SnipProbe.java` / `SnipProbe.class` / `cp-core.txt` 位于 `%TEMP%`；本轮未再产生新的探针文件 |
| 门禁日志 | `gate.log` / `gate2.log` 位于 worktree 根，属构建期诊断日志（已被 `.gitignore` 覆盖），随 worktree 一并删除 |
| 用户真实会话 | 仅**只读**读取 `2026-09-18T13-34-27-b1569b39.jsonl` 做根因分析，未修改、未删除 |
| worktree | 合并复验通过后按 §2.7.5.3 删除，其 `target/` 与日志一并消失 |
