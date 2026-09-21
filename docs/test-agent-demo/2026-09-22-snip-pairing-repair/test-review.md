# snip-pairing-repair 测试复盘

> 批次：`2026-09-22-snip-pairing-repair`　执行日：2026-09-22
> 关联交付：`test-design.md` / `test-cases.md` / `test-report.md`

---

## 1. 目标与结果对照

| 目标 | 计划判定 | 实际结果 | 达成 |
|------|---------|---------|:----:|
| G1 `snip` 不再制造反向孤儿 | SR-01 先红后绿 | 红：`expected: <false> but was: <true>`；绿：13/13 | 是 |
| G2 反向修复可复用、纯函数、幂等 | TCP-01~07 | 15/15（含 7 个新例 + 8 个回归） | 是 |
| G3 请求路径双向自愈 | AL-01 先红后绿 | 红：反向孤儿断言失败；绿：2/2 | 是 |
| G4 无回退 | 门禁全绿 | 分支与合并后各一次 `BUILD SUCCESS`；前端 302 全过；tsc 6 ≤ 7 | 是 |
| — | 附带 | 发现 agent-core 覆盖率门禁空转（D-04） | 超范围，已记录未修 |

---

## 2. 流程回顾

### 2.1 四阶段执行

```mermaid
flowchart LR
    A["explore<br/>已在前序会话完成<br/>探针定位根因"] --> B["propose<br/>proposal + design<br/>+ delta spec + tasks"]
    B --> C["apply<br/>T1 / T2 / T3<br/>各自先红后绿"]
    C --> D["archive<br/>delta 并入 testability spec"]
    D --> E["合并 main + 复验<br/>+ 清理 worktree"]
```

- **explore 阶段便宜且关键**：上一轮会话用探针读取真实存档并逐步执行恢复链路，把「猜测」变成
  「204 条 → 反向孤儿 0 → 裁剪后 1」的硬数字。没有这一步，后面的修复就是盲改。
- **propose 阶段先查已有 spec**：`openspec/specs/testability/spec.md` 里已存在
  `悬挂 tool_calls 自愈` 与 `恢复与请求两条路径都自愈` 两条需求，本 change 因此使用
  `MODIFIED`（扩展既有需求）+ `ADDED`（新增裁剪不变式），而不是另立 capability。

### 2.2 TDD 节奏

三次「先红 → 实现 → 转绿」，每次都留了红的原文：

| 步骤 | 红的形态 | 说明 |
|------|---------|------|
| ① `snip` 组对齐 | 断言失败（13 run / 1 fail） | 行为型红：功能确实不对 |
| ②a `repairOrphanResults` | 编译失败（找不到符号） | 存在型红：方法还没写 |
| ②b `toRequest` 双向修复 | 断言失败（2 run / 1 fail） | 行为型红：孤儿确实被发出去了 |

三种红里，②b 的红最有价值——它直接证明「孤儿 tool 消息真的进入了发往上游的列表」，
而不只是证明「某个函数返回值不符合预期」。

---

## 3. 根因分析复盘

### 3.1 为什么这个缺陷能长期潜伏

缺陷的本质是**修复时机的不对称**，而不是某段代码写错了：

| 方向 | 修复函数 | 执行时机 |
|------|---------|---------|
| 正向 | `ToolCallPairing.repair` | 恢复路径 + **请求路径每轮** |
| 反向 | `SessionResumeLoader.injectOrphanSkeletons` | **仅**恢复路径一次 |

正向每轮重跑 → 内存历史被弄脏也能自愈，于是「历史配对」这件事看起来已经被解决了；
反向只在装载时跑一次 → 任何在装载**之后**破坏反向不变式的操作都没有兜底。
`snip` 恰好是装载之后执行的第一步（`restoreHistory` 里 `toMessages` 之后紧接着 `snip`），
所以它造出的孤儿一路畅通。

之前修 `repair-dangling-tool-calls` 时补了正向，`fix-resume-link` 时补了反向，
两次都只补了「自己发现的那条路径」，没人问「这条修复在整个生命周期里什么时候会再被破坏」。

### 3.2 可推广的教训

> **配对类不变式的修复，必须落在「每一次对外发送」的路径上，而不是落在「装载」的路径上。**
> 装载是一次性的，破坏是持续的。

本次修复同时也把反向修复提到了请求路径，正是这条教训的直接应用。

---

## 4. 做得好的

| 项 | 说明 |
|----|------|
| 先量化再动手 | 探针给出消息数/孤儿数/token 数三个硬数字，根因不是猜的 |
| 夹具不依赖真实数据 | 用 `groupSplitFixture` 复刻形状（5 条消息），符合全局规则 §10，也避免测试依赖用户文件 |
| 上限反算控制边界 | 「先算后设」让裁剪点必然落在指定下标，而不是靠试参数；并用 SR-02 从另一侧夹住，防止 SR-01 因边界未触发而假绿 |
| 断言不变式而非条数 | 断言「不存在无前置 tool_calls 的 tool_result」直接对应协议约束，实现换策略也不会误报 |
| 幂等用不动点断言 | `repairOrphanResults(repair(x)) == x` 同时覆盖「不产生冗余」与「判定稳定」，比调两次比长度更强 |
| 归属判定给证据 | 对 `_axe-debug.test.tsx` 的失败给出三条独立依据（未入库 / 复跑消失 / 零前端 diff），没有含糊归因 |
| 顺带验证了门禁本身 | 对覆盖率门禁做阈值抬升实验，发现 agent-core 门禁空转（见 §6） |

---

## 5. 可改进的

| 项 | 问题 | 下次怎么做 |
|----|------|-----------|
| 用例的「红」比例不高 | 12 个新例里只有 3 个在修复前失败，其余 9 个描述的是既有正确行为 | 这类「性质保持型」用例仍有价值（防回退），但应在报告里显式区分「行为型红」与「保持型」，避免读者高估测试强度——本批已在 §3.1 说明 |
| worktree 缺构建产物 | `static/` 缺失会让 `WebIntegrationTest` 假失败，属于每个新 worktree 都要踩一次的坑 | 考虑把「拷 `static/`」写进项目脚本或 README，减少重复劳动 |
| 前端测试在另一个工作区跑 | 本次因「零前端改动」而在主工作区跑 vitest/tsc | 更严谨的做法是在 worktree 里建 `node_modules` junction 后跑；本次以 `git diff main...HEAD -- agent-web/frontend` 为空作为等价性依据，属可接受的取舍 |
| 覆盖率门禁未纳入本批验证 | 门禁是空转的，等于本批没有真正的覆盖率约束 | 见 §6 建议 |

---

## 6. 意外发现：agent-core 覆盖率门禁空转

这是本批最有价值的副产品，详见 `test-report.md` §6。

**发现路径**：
1. 门禁打印 `All coverage checks have been met`，但独立按 pom 的 include 口径复算 BRANCH 只有 0.6456~0.6601，低于配置的 0.70；
2. 抬阈值实验：把 `0.70` 改成 `0.99` → 仍然 `met` + `BUILD SUCCESS`；
3. 对照实验：agent-web 的 `0.80` 改成 `0.99` → 9 条 `Rule violated` + `BUILD FAILURE`；
4. 定位差异：`agent-core` 用 `<element>BUNDLE</element>` 配类名前缀 includes，而 `includes` 过滤的是被考核元素名（BUNDLE 元素下即 bundle 名），因此匹配不到任何对象。

**为什么值得单独提**：项目文档（`AGENTS.md` §2.5.3）把「jacoco LINE≥80% / BRANCH≥70%」列为强制门禁，
实际对 agent-core 从未生效。**且这不是新问题**——2026-08-29 的首次全面测试就已记录
「jacoco 覆盖率门禁引用已废弃包 `com.example.agent.agent.*` 导致门禁失守」，但此后一直未修复。
本次的价值在于把归因**修正得更准**：当时以为是某一个 include 写错了包名，
实际整条 BUNDLE 规则都匹配不到对象。一个被记录却未被解决的问题，比一个未知问题更容易被忽略。

**为什么本次不修**：修好会让构建立刻变红（agent-core 现状不达标），需要一次专门的覆盖率补测 change。
把「修门禁」和「修 bug」混在一起，会让一个已明确的 P0 修复被覆盖率工作拖住。

---

## 7. 交付物清单

| 类型 | 路径 |
|------|------|
| 代码 | `agent-core/src/main/java/com/example/agent/session/SessionResumeLoader.java` |
| 代码 | `agent-core/src/main/java/com/example/agent/core/ToolCallPairing.java` |
| 代码 | `agent-core/src/main/java/com/example/agent/core/AgentLoop.java` |
| 测试 | `agent-core/src/test/java/com/example/agent/session/SessionResumeLoaderTest.java` |
| 测试 | `agent-core/src/test/java/com/example/agent/core/ToolCallPairingTest.java` |
| 测试 | `agent-core/src/test/java/com/example/agent/core/AgentLoopToolPairingTest.java` |
| Spec | `openspec/specs/testability/spec.md`（+1 新需求 `裁剪不破坏配对不变式`；~1 修改 `恢复与请求两条路径都自愈`） |
| 归档 | `openspec/changes/archive/2026-09-21-snip-pairing-repair/` |
| 文档 | `docs/test-agent-demo/2026-09-22-snip-pairing-repair/`（本四件套） |
| 提交 | `2d4153e`（snip 组对齐）、`a94ad13`（请求路径双向修复），提案提交 `13f6b45` |

---

## 8. 后续建议

| 优先级 | 建议 | 理由 |
|:------:|------|------|
| P0 | 单开一个 change 修 agent-core 的 jacoco 规则（改 `element` 或去掉 includes + 补覆盖率） | 强制门禁空转是流程性风险，比单个 bug 影响面更大 |
| P1 | 排查 `snip` 之外还有谁会破坏反向不变式（`closePendingToolCalls`、手工改档、并发恢复） | 本次已在请求路径兜底，但源头清单尚未穷举 |
| P1 | 给 `ChatCommand`（CLI）侧补一条与 AL-01 对等的请求路径用例 | 目前只在 web 侧的 `AgentLoop` 验证；CLI 若走不同装配路径需单独确认 |
| P2 | 统一 jacoco 配置里 includes/excludes 的路径写法（点号 vs 斜杠） | 两种写法混用是同类配置错误的温床 |
| P2 | 把「拷 `static/` 到新 worktree」写进脚本或文档 | 减少每个新 worktree 的重复踩坑 |
