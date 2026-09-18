# 测试报告 — fix-agent-home-isolation

- 批次目录：`docs/test-agent-demo/2026-09-18-fix-agent-home-isolation/`
- 执行日期：2026-09-18
- 分支：`fix/fix-agent-home-isolation`（worktree `.worktrees/fix-agent-home-isolation`）
- 用例来源：`test-cases.md`；设计依据：`test-design.md`

## 1. 执行结果总览

| 项 | 命令 | 结果 | 判定 |
|----|------|------|:--:|
| agent-core 测试 | `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**` | **528 / 0 失败 / 0 错误** | ✅ |
| agent-web 测试 | 同上 | **373 / 0 失败 / 0 错误** | ✅ |
| jacoco 包级门禁 | 同上 | 仅 `security` branches 0.63 < 0.70（既有，见 §4） | ⚠️ 记录放行 |
| `AgentPathsTest` | `-Dtest=AgentPathsTest` | **9 / 0** | ✅ |
| logback 探针 | 最小 JVM（见 §3） | 2/2 符合预期 | ✅ |
| 真实目录差分 | 门禁跑前后各测一次 | 见 §2 | ✅ |

## 2. 核心验收：跑完测试真实目录是否增长

| 指标 | 跑前 | 跑后 | 判定 |
|------|:--:|:--:|:--:|
| `~/.agent-demo/logs/sessions` 目录数 | 7 | **7** | ✅ 不变 |
| `~/.agent-demo/logs/app.log` 字节数 | 165535 | **165535** | ✅ 不变 |

> 注：`app.log` 在两次测量之间曾从 165488 涨到 165535——那是**用户自己在 IntelliJ 里运行的应用**在写（该实例 21:36 启动，会话文件同期从 313KB 涨到 565KB）。故该指标不能单独作为判据，必须辅以内容归属，见 §2.1。

### 2.1 内容归属（判据可复核）

`logs/sessions/<uuid>/session.jsonl` 首行含 `"cwd"` 字段，直接标明产生它的工作区。用它区分「我这次测试产生的」与「用户应用产生的」：

| 目录 | cwd | 归属 |
|------|-----|------|
| 真实 `~/.agent-demo/logs/sessions/` 下 7 个 | 主工作区 `E:\claude-projects\agent-demo` | 用户应用（**未动**） |
| `agent-web/target/test-data/.agent-demo/logs/sessions/` 下 8 个 | 本 worktree | 本次测试（**已隔离**） |
| `agent-web/target/test-data-model-http/.agent-demo/logs/sessions/` 下 4 个 | 本 worktree | 本次测试（**已隔离**） |

改造前，这 12 个目录会落在真实 `~/.agent-demo/logs/sessions/` 下——正是 2026-09-18 修复任务期间需手工清理的那 22 个目录的来源（分 3 批清理，每批都按 `cwd` 判定归属并导出审计 CSV）。

## 3. logback 落点探针（决定性证据）

单独编译一个只打一条 INFO 日志的最小类，用 agent-core 的 classpath（含 `logback.xml`）直接跑：

| 用例 | 命令 | 期望 | 实测 |
|------|------|------|:--:|
| LB-01 | `java -Dagent.demo.home=E:/tmp/lb-base LbProbe` | `<base>/.agent-demo/logs/app.log` 存在、`<base>/logs/app.log` 不存在 | ✅ 符合 |
| LB-02 | `java LbProbe`（不设属性） | 真实 `~/.agent-demo/logs/app.log` 仍被写入 | ✅ 符合 |

### 3.1 该探针抓到了本次的一个自造缺陷

第一版 `logback.xml` 写的是 `${agent.demo.home:-${user.home}/.agent-demo}/logs`。**设了属性时**它解析为 `<base>/logs`，而 Java 侧 `AgentPaths` 给的是 `<base>/.agent-demo/logs`——**两边差一层目录**。等于把刚要收敛掉的分叉又造了一个。

实测：修正前 `<base>/logs/app.log` 存在、`<base>/.agent-demo/logs/app.log` 不存在；改为 `${agent.demo.home:-${user.home}}/.agent-demo/logs` 后两者位置反转，符合预期。

**这是本次唯一由实测（而非推理）抓出的实现缺陷。**若只看「真实目录没增长」，这个 bug 会完全隐形——因为它同样满足「没写真实目录」。

## 4. jacoco 门禁归因

| 包 | 合并前 main（`f8578a1`） | 本分支 | 说明 |
|----|:--:|:--:|------|
| `com.example.agent.web.security` | branches 0.63 | branches 0.63 | **逐位一致** → 既有；本次未触碰该包 |

`docs/test-agent-demo/test-guide.md` 登记表中 `2026-09-13-improve-voice-accuracy` 一行记录的正是同一条违规（当时 0.62，在 main HEAD 可复现 → 记录放行）。

## 5. 缺陷清单

### 5.1 被测缺陷

| # | 缺陷 | 严重度 | 证据 |
|:--:|------|:--:|------|
| D-1 | 「agent home 在哪」有 **14 处独立解析**，覆盖链各不相同 | 🔴 高 | 见 `test-design.md` §1.1 的两张表 |
| D-2 | 唯一的测试隔离开关（系统属性 `agent.demo.home`）**只被 1 处认** | 🔴 高 | `@SpringBootTest` 设不了环境变量，只能设系统属性；其余 13 处只认 env / `user.home` |
| D-3 | 后果：测试运行持续向**用户真实** `~/.agent-demo/logs/sessions/` 写目录 | 🔴 高 | 2026-09-18 实测需手工清理 22 个，分 3 批 |
| D-4 | `AgentConfig` 的日志根与工作树基目录写死 `user.home` | 🟡 中 | 直接导致 D-3 |
| D-5 | `AGENT_DEMO_HOME` 有两种语义 | 🟡 中 | 多数位置当「基目录」（其下拼 `.agent-demo`），`SettingsFile.resolveDefaultHome` 当「完整路径」→ settings.yaml 与 sessions 会落在不同层级 |
| D-6 | web 侧两处加载 `config.yaml` 的路径漏网 | 🟡 中 | `WebRuntimeConfig:42`、`WebConfig:35` 直接拼 `user.home` |
| D-7 | 诊断页 `LogController` / `DiagnosticsController` 读的日志根写死 `user.home` | 🟡 中 | 与写入侧不同源，测试隔离下会去扫用户真实目录 |

### 5.2 测试过程中发现的问题（非产品缺陷）

| # | 问题 | 处置 |
|:--:|------|------|
| T-1 | 我第一版 logback 表达式把属性当完整 home，与 Java 侧差一层目录 | 探针实测抓到，已修正（见 §3.1） |
| T-2 | 我写的两条断言用 `startsWith("target/prop-home")`，Windows 上 `Path.toString()` 用反斜杠 → 假失败 | 改为 `contains("prop-home")` |
| T-3 | 我初版 `AgentConfig` 那条断言**不设属性**时会与旧实现结果相同 → 假绿 | 改为显式设属性再断言 |
| T-4 | `app.log` 是否被测试写入，受用户实时运行的应用干扰 | 改用 `cwd` 字段做内容归属，并明确 §6 的未验证项 |

## 6. 未完成验证（如实记录）

| 项 | 状态 |
|----|------|
| surefire JVM 内 logback FILE appender 是否真的落盘 | **未能证明落盘**。隔离目录下找到了 `sessions/` 但没有 `app.log`；真实 `app.log` 也未收到测试写入。探针证明**普通 JVM** 下路径逻辑正确（生产侧无问题），但 surefire 环境下 appender 似乎未触发。 |
| 该现象的归属 | **既有**：在本次任何 logback 改动之前（第一次门禁跑）真实 `app.log` 的字节数与 mtime 同样全程未变，说明测试本来就不往它写。 |
| 对本 change 结论的影响 | 不影响「测试不污染真实 app.log」这一 Requirement 的成立；但**不能用它来主张隔离机制生效**——隔离机制的有效性由 §2.1 的 per-session 目录归属证明。 |
| 建议 | 若要让 `app.log` 的隔离也可验证，需单独立项排查 surefire 下 appender 未触发的原因。 |

## 7. 数据清理（全局规则 §10）

| 对象 | 处理 |
|------|------|
| 本 worktree 的隔离产物（`target/test-home`、`target/test-data*`、`target/test-logs`） | 位于 `target/`（gitignore），随 worktree 移除一并消失 |
| 用户真实 `~/.agent-demo/logs/sessions/` | **未写入**（§2 已验证） |
| 用户真实 `~/.agent-demo/sessions/` | **未写入**（65 个文件，最新写入早于本次全部测试） |
| 探针临时目录 `E:/tmp/lb-base` | 已删除 |
| 先前 3 批手工清理 | 审计 CSV：`~/.agent-demo/test-log-dirs-removed-20260918*.csv`（共 3 个文件，22 个目录） |

## 8. 退出标准核对（DoD）

| DoD 项 | 状态 |
|--------|:--:|
| `AgentPathsTest` 全绿且用例数不为 0 | ✅ 9/0 |
| 探针证明两种属性状态下的落点 | ✅ §3 |
| 门禁跑完真实目录数不变 | ✅ §2 |
| 隔离目录内确实有产物（区分「隔离对了」与「日志坏了」） | ✅ per-session 12 个目录；`app.log` 例外见 §6 |
| jacoco 违规不超基线 | ✅ 同为 1 条 |
| 前端 vitest / tsc | 本次零前端改动，未重跑（前端 `static/` 为构建产物，不属源码） |
| 测试数据清理并说明判据 | ✅ §7 |
