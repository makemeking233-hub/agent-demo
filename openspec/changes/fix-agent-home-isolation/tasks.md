# fix-agent-home-isolation — 任务清单

> 每项按 §2.2 TDD：测试先红 → 实现 → 转绿；完成后立即 commit + push 到本分支 `fix/fix-agent-home-isolation`。

## T1 `AgentPaths` 单一解析入口

- [ ] T1.1 先写 `AgentPathsTest`（红）：系统属性命中 → 用属性值；属性空白 → 视为未设；属性缺省 + env 命中 → 用 env（`System.getenv` 不可注入，故把「按给定 override 值解析」抽成可测重载，env 分支通过该重载覆盖）；两者都缺 → `user.home`；派生目录 `logs/sessions/worktrees` 均挂在返回的 home 下。
- [ ] T1.2 新建 `com.example.agent.config.AgentPaths`：`HOME_PROPERTY` / `HOME_ENV` 常量 + `agentHome()` + `logsDir()` / `sessionsDir()` / `worktreesDir()`。
- [ ] T1.3 转绿；commit + push。

## T2 收敛 11 处解析点

- [ ] T2.1 逐点改造：`WebAgentRuntime.resolveDataDir`（委托）、`AgentConfig`（logs + worktrees 默认值）、`AgentLoopFactory`（3 处）、`ChatCommand`（3 处）、`InitCommand`、`SettingsFile`、`SettingsConfig`、`WecomBeans`、`LogController`、`DiagnosticsController`、`SkillsPlugin`。
  - **改前逐个确认语义**：`HomePathGuard` 的界是「用户家目录」，**不改**；`DiagnosticsController` 若展示的是用户家目录而非 agent 数据目录则**不改**（design.md Open Question 1）。
- [ ] T2.2 断言「无遗漏」：全仓 grep `user.home` 与 `.agent-demo`，逐一说明保留或改造的理由（写进 test-report）。
- [ ] T2.3 跑相关测试转绿；commit + push。

## T3 logback 走同一属性（需实测）

- [ ] T3.1 改 `logback.xml`：`<property name="AGENT_LOGS_DIR" value="${agent.demo.home:-${user.home}/.agent-demo}/logs"/>`，file 与 rollingPattern 都用它。
- [ ] T3.2 **实测嵌套默认值是否被递归求值**：一个设了 `-Dagent.demo.home=<temp>` 的 JVM 里跑应用/测试，检查 app.log 是否落在 `<temp>/.agent-demo/logs/`。
  - 成立 → 记录证据；不成立 → 改为在 surefire 显式设 `agent.logs.dir` 并让 logback 直接读它（design.md D2 的回退方案）。
- [ ] T3.3 commit + push。

## T4 测试 JVM 默认隔离

- [ ] T4.1 `agent-core/pom.xml` + `agent-web/pom.xml` 的 surefire 加 `systemPropertyVariables: agent.demo.home=${project.build.directory}/test-home`。
- [ ] T4.2 检查副作用：`WebAgentRuntimeDataDirTest`（会 clearProperty 后断言回落）必须仍绿。
- [ ] T4.3 跑 `mvn -o -pl agent-core,agent-web test` 全绿；commit + push。

## T5 端到端证明隔离成立

- [ ] T5.1 先记录「跑测试前的真实 `~/.agent-demo/logs/sessions` 目录数」。
- [ ] T5.2 跑一次全量 `mvn -o -pl agent-core,agent-web verify -DskipNpm=true -Dsurefire.excludes=**/e2e/**`。
- [ ] T5.3 **断言跑完后真实目录数不变**（这是本 change 的核心验收：spec 里那条 Requirement 从「纸面」变为「成立」）。
- [ ] T5.4 写 `AgentPaths` 跟随覆盖的集成用例：真实 `POST /api/chat/send` 后 per-session 日志目录落在 `target/test-home/.agent-demo/logs/sessions/` 内。
- [ ] T5.5 commit + push。

## T6 收尾

- [ ] T6.1 全量门禁：`mvn verify` + 前端 `npx vitest run` + `npx tsc --noEmit`（≤ 基线）。
- [ ] T6.2 测试文档四件套 + `test-guide.md` 登记。
- [ ] T6.3 `openspec archive fix-agent-home-isolation --yes`；确认 delta spec 并入。
- [ ] T6.4 按 §2.7.5 合并回 `main`：同步 → 复验 → push → 清理。
- [ ] T6.5 报告真实 `~/.agent-demo/logs/sessions/` 下的历史遗留清单（属运维动作，不在代码范围，由用户决定是否清理）。
