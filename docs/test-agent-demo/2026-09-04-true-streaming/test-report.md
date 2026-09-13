# test-report — add-true-streaming

> 批次：`2026-09-04-true-streaming`
> 执行日期：2026-09-04
> 环境：JDK 17 / Maven 3.9（离线 `mvn -o`）/ JUnit 5 + WireMock + Selenium 4.25 / Node + vitest

---

## 1. 执行结果总览

| 编号 | 范围 | 命令 | 用例数 | 通过 | 失败 | 跳过 | 结论 |
|:----:|------|------|:------:|:----:|:----:|:----:|:----:|
| TS-R-01 | agent-core 全量 + jacoco | `mvn -o -pl agent-core verify` | 353 | 353 | 0 | 0 | ✅ BUILD SUCCESS |
| TS-R-02 | agent-web（排除 e2e 包） | `mvn -o -pl agent-web verify -Dsurefire.excludes=**/e2e/**` | 158 | 158 | 0 | 1 | ⚠️ 用例全绿，jacoco 包级门禁 3 处违规（既有欠账） |
| TS-R-02b | agent-web（含 e2e 包） | `mvn -o -pl agent-web verify` | 176 | 172 | 4 | 2 | ⚠️ 4 条失败全部为既有 Selenium 用例 |
| TS-R-03 | 前端 | `npx vitest run` | 104（16 文件） | 104 | 0 | 0 | ✅ |

本次 change 新增/改动的 20 条用例（`SseLineBufferTest` 7 + `OpenAiCompatibleProviderE2ETest` 3 + `ChatStreamServiceStreamingTest` 4 + `ChatStreamServiceConcurrencyTest` 1 + `CompositeSessionLogSinkTest` 新增 1 + 既有回归 4）**全部通过**。

---

## 2. 核心指标：TTFT 实测

### 2.1 改造前（`bodyToMono`）

用 `withFixedDelay(800)` 模拟「上游 800ms 后才把响应体送达」：

```text
TTFT (first emit after request): 837ms
Chunks: [TextDelta[text=first]]
```

首 emit 发生在 837ms，等于上游延迟加开销——证明 `bodyToMono` 期间没有任何数据向下游流动。

### 2.2 改造后（`bodyToFlux` 加行重组）

用 `withChunkedDribbleDelay(5, 500)` 模拟「上游分 5 段、共 500ms 持续吐字」：

```text
TTFT=250ms, total=577ms, chunks=[TextDelta[text=你], TextDelta[text=好], TextDelta[text=！]]
```

- TTFT 250ms，小于 300ms 阈值 ✅
- 整体耗时 577ms，首 emit 比完成早 327ms（大于 200ms 阈值）✅
- 3 个事件全部解析正确，说明分 5 段造成的跨帧切行没有破坏事件 ✅

> 注意：两次测量用了不同的 stub，不可直接横向比较（`withFixedDelay` 下真流式也只能等到 800ms）。判断依据是「首 emit 是否显著早于整体完成」。

### 2.3 编排层增量验证

`ChatStreamServiceStreamingTest` 用每 150ms 一个 chunk 的假 provider 驱动真实 `ChatStreamService`：

```text
正文载荷 = ["A", "B", "C"]      ← 逐 token；若 onAssistant 整段重发会多出 "ABC"
首个正文到达 ≪ message_stop     ← 增量推送，非攒批
```

---

## 3. 缺陷清单

### 3.1 本次修复

| 编号 | 级别 | 描述 | 根因 | 修复 |
|:----:|:----:|------|------|------|
| D1 | 🔴 | HTTP 层假流式：TTFT 等于总响应时长 | `streamChat` 用 `bodyToMono(String.class)`，必须等整个响应体收完 | 改 `bodyToFlux(DataBuffer.class)` |
| D2 | 🔴 | 跨 TCP 帧的 SSE 行未重组 | 直接对单个 `DataBuffer` 切行，一行被切开时产生半截 JSON | 新增 `SseLineBuffer` 跨帧累积行缓冲 |
| D3 | 🔴 | 正文不逐 token 推送，前端「一坨出来」 | `SseSessionLogSink` 只在 `collectList()` 后的 `onAssistant` 整段 emit | `SessionLogSink.onTextDelta` + `AgentLoop` 逐 chunk 转发 + `SseSessionLogSink` 立即 emit |
| D4 | 🔴 | `CompositeSessionLogSink` 未转发 `onThinkingDelta` | 增量回调是接口 default 空方法，复合 sink 不显式转发就被吞掉；web 正常路径（有落盘录制器）返回的正是复合 sink | 补转发 `onThinkingDelta` 与 `onTextDelta` |
| D5 | 🟡 | 整段兜底会把已流式推送的正文再发一遍 | 增量化后 `onAssistant` 仍无条件 emit 全文，前端会渲染两遍 | `AtomicBoolean textStreamed` 逐轮判重 |

### 3.2 既有问题（本次未处理，已登记）

| 编号 | 级别 | 描述 | 归属 | 证据 |
|:----:|:----:|------|------|------|
| E1 | 🟡 | `UiLayoutE2ETest` 4 条 Selenium 用例失败（`topBarElementsShow` / `newSessionAddsToSidebar` / `sidebarShowsGroupedPlaceholders` / `emptyInputDisablesSend`） | `add-workspaces-and-rename` | 用例查找 `button[aria-label='新建会话']`，而该字符串在**整个前端源码中已不存在**（改用 `...` 菜单 + 头部 `＋`）；测试文件自 `cecc42d`（PWA，早于工作区改版）起未再修改 |
| E2 | 🟡 | agent-web jacoco 包级门禁 3 处违规 | 其他 change 的测试欠账 | 见 §4 |

---

## 4. 覆盖率

### 4.1 agent-core

`All coverage checks have been met`（LINE 不低于 80% / BRANCH 不低于 70%）。本次新增的 `SseLineBuffer` 由 `SseLineBufferTest` 7 例覆盖。

### 4.2 agent-web

`web.stream` 包（本次改动所在）用例从 18 增至 42，全部通过。门禁违规集中在三个**与本次改动无关**的包：

| 包 | 违规项 | 实际 | 要求 | 根因（逐类定位） |
|----|--------|:----:|:----:|------------------|
| `com.example.agent.web.config` | lines / branches | 0.40 / 0.46 | 0.80 / 0.70 | **仅由 `SslCertificateGenerator` 造成**：67 行未覆盖、20 分支未覆盖、覆盖率 0（PWA 自签证书生成，来自 `add-pwa-support`）；同包其余类覆盖率良好 |
| `com.example.agent.web.security` | branches | 0.62 | 0.70 | `TrustedHostFilter` 33 分支未覆盖；`HomePathGuard` 8 分支未覆盖 |
| `com.example.agent.web.api` | branches | 0.65 | 0.70 | `ModelsController` 与 `ModelRegistry` 覆盖率均为 **0**（模型注册表改动）；另有 `FsController` 14 分支、`ChatController` 13 分支、`SessionController` 11 分支未覆盖 |

结论：门禁失败**不是本次 change 引入的**，本次改动所在包（`web.stream`）无违规，且新增测试提升了该包覆盖。建议另开 change「补 jacoco 门禁欠账」专门处理。

---

## 5. 环境适配与踩坑

| 问题 | 现象 | 处置 |
|------|------|------|
| Maven 增量编译残留 | 删除临时诊断测试类后重跑，报 `NoClassDefFoundError: DeepSeekProvider` | 加 `clean` 重跑 |
| PowerShell 解析 `-D` 参数 | `-Dtest=!...` 的 `!` 被吞、`-Dsurefire.failIfNoSpecifiedTests=false` 被当成 lifecycle phase | 改用 `cmd.exe /c "mvn ..."` 包裹 |
| WireMock 无法测 TTFT | `withFixedDelay(N)` 把整个响应体延迟 N 毫秒才发，真流式也只能等到那一刻 | 改用 `withChunkedDribbleDelay(n, totalMs)` 造真实分段响应 |
| 排除 e2e 包 | `-Dtest=!com.example.agent.web.e2e.*` 在 cmd 下失效 | 改用 `-Dsurefire.excludes=**/e2e/**` |

---

## 6. 退出标准核对

| 标准 | 状态 |
|------|:----:|
| 上表新增/改动用例全部通过 | ✅ |
| agent-core `verify` BUILD SUCCESS 且 jacoco 达标 | ✅ |
| agent-web 用例全绿（排除既有 e2e 失败） | ✅ |
| 前端 `vitest run` 全绿 | ✅ |
| 无新增 🔴 缺陷 | ✅ |
| jacoco 门禁全绿 | ❌ 既有欠账，非本次引入（见 §4.2） |
| 四件套齐备并登记 `test-guide.md` | ✅ |
