# 测试用例文档 — fix-agent-home-isolation

- 批次目录：`docs/test-agent-demo/2026-09-18-fix-agent-home-isolation/`
- 用例来源：`test-design.md` §6；本文件为可独立追溯的全量明细
- 执行结果见 `test-report.md`（本文件只列用例与预期，不含结论）

## 1. 编号规则

`AP` = AgentPaths，`LB` = logback 探针，`DIFF` = 真实目录差分测量。

## 2. 用例明细

### 2.1 AP — `AgentPathsTest`（优先级与派生路径）

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| AP-01 | 三者都给 | `agentHome("/tmp/from-prop","/tmp/from-env","/home/tester")` | `/tmp/from-prop/.agent-demo`——**属性必须赢过 env**，因为 `@SpringBootTest` 设不了 env，只能设属性 | P0 |
| AP-02 | 属性缺省 | `agentHome(null,"/tmp/from-env",userHome)` | `/tmp/from-env/.agent-demo` | P0 |
| AP-03 | 属性与 env 都缺省 | `agentHome(null,null,userHome)` | `<userHome>/.agent-demo` | P0 |
| AP-04 | 属性为空白串 | `agentHome("   ","/tmp/from-env",userHome)` | 落到 env 分支（空白视为未设置） | P0 |
| AP-05 | env 也为空白串 | `agentHome(" ","  ",userHome)` | 落到 `user.home` 分支 | P1 |
| AP-06 | 无 | `logsDir()/sessionsDir()/worktreesDir()` | 三者均以同一个 `agentHome()` 为前缀 | P0 |
| AP-07 | 无 | 断言常量字面量 | `HOME_PROPERTY == "agent.demo.home"`、`HOME_ENV == "AGENT_DEMO_HOME"`（改名会让所有既有测试的隔离静默失效） | P0 |
| AP-08 | 显式设属性 `target/prop-home` | `AgentConfig.defaults().logging().dir()` | 等于 `AgentPaths.logsDir()` 且含 `prop-home` | P0 |
| AP-09 | 显式设属性 `target/prop-home` | `AgentConfig.defaults().worktree().baseDir()` | 等于 `AgentPaths.worktreesDir()` 且含 `prop-home` | P1 |

> AP-08/AP-09 **必须显式设属性**：不设时新旧实现结果相同，那两条会假绿。

### 2.2 LB — logback 落点探针

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| LB-01 | 最小 JVM（只打一条 INFO），`-Dagent.demo.home=E:/tmp/lb-base` | 运行后检查两个候选路径 | `<base>/.agent-demo/logs/app.log` 存在；`<base>/logs/app.log` **不存在** | P0 |
| LB-02 | 同一 JVM，**不设**属性 | 运行后检查 | 真实 `~/.agent-demo/logs/app.log` 仍被写入（行为与改造前一致） | P0 |

> LB-01 存在的理由：第一版 `logback.xml` 把 `agent.demo.home` 当完整 home，设属性时落到 `<base>/logs`，与 Java 侧的 `<base>/.agent-demo/logs` 差一层。**该用例正是为抓这类「两边各自为政」而设**，且实测抓到了。

### 2.3 DIFF — 真实目录差分测量

| 编号 | 前置 | 步骤 | 预期 | 优先级 |
|:--:|------|------|------|:--:|
| DIFF-01 | 记录真实 `~/.agent-demo/logs/sessions` 目录数 | 跑完整 `mvn verify` | 跑完后目录数**不变** | P0 |
| DIFF-02 | 记录真实 `~/.agent-demo/logs/app.log` 字节数与 mtime | 同上 | 字节数与 mtime **均不变** | P0 |
| DIFF-03 | 同上 | 检查隔离目录 | `<module>/target/test-home/.agent-demo/logs/app.log` **存在**，且 `sessions/` 下有目录 | P0 |
| DIFF-04 | 同上 | 检查隔离目录结构 | per-session 日志落在 `<module>/target/test-home/.agent-demo/logs/sessions/` | P0 |

> DIFF-03/DIFF-04 是**区分「隔离对了」与「日志坏了」的关键**：两者的表象都是「真实目录不增长」。只看 DIFF-01/02 会把「FILE appender 崩了」误判为「隔离成功」。

## 3. 反向断言清单

| 用例 | 反向断言 |
|------|----------|
| AP-01 | 属性必须赢过 env（否则测试隔离被开发机的 `AGENT_DEMO_HOME` 反盖） |
| AP-07 | 常量字面量不许变（改名会让既有隔离静默失效） |
| LB-01 | `<base>/logs` **不得**存在（防两边差一层目录） |
| LB-02 | 不设属性时**必须**仍写真实目录（防「修隔离顺手改了默认路径」） |
| DIFF-01/02 | 真实目录读写**都不许**增长 |
| DIFF-03/04 | 隔离目录里**必须**真的有产物（防把「日志坏了」当成「隔离成功」） |
