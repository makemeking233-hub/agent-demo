# fix-agent-home-isolation

## Why

`openspec/specs/observability` 里已有两条 Requirement 明确要求：日志根固定为 `~/.agent-demo/logs`、且**测试运行不得向运行时日志写入**。实际情况是后者长期不成立——maven 测试每次都会在真实 `~/.agent-demo/logs/` 下创建 `sessions/<uuid>/` 目录，2026-09-18 一次修复任务期间实测需手工清理 22 个。

根因不是「某处路径写错了」，而是**「agent home 在哪」有 11 处各自独立解析**：

| 解析点 | 覆盖链 |
|--------|--------|
| `WebAgentRuntime.resolveDataDir` | 系统属性 `agent.demo.home` → env `AGENT_DEMO_HOME` → `user.home` |
| `AgentLoopFactory`（2 处）、`ChatCommand`、`SkillsPlugin` | env → `user.home`（**无系统属性**） |
| `InitCommand`、`SettingsFile` | env → `user.home`（**无系统属性**） |
| `AgentConfig`（logs / worktrees 两处默认值） | 仅 `user.home` |
| `SettingsConfig`、`WecomBeans`、`LogController`、`DiagnosticsController` | 仅 `user.home` |
| `logback.xml` | 仅 `${user.home}` |

唯一的测试隔离开关（系统属性 `agent.demo.home`，`@SpringBootTest` 无法设环境变量，只能用它）**只被 11 处中的 1 处认**。所以隔离只盖住了会话存档，盖不住：日志根、`/api/logs` 读取路径、诊断页、`AgentConfig` 的日志/工作树默认值。

## What Changes

- **新增单一解析入口** `com.example.agent.config.AgentPaths`：`agentHome()` 按「系统属性 `agent.demo.home` → env `AGENT_DEMO_HOME` → `user.home`」解析，其下派生 `logsDir()` / `sessionsDir()` / `worktreesDir()`。
- **11 处解析点全部改走该入口**，消除分叉。
- **`logback.xml` 的 app.log 路径支持同一覆盖**，使测试运行不再往真实 `app.log` 写。
- **测试 JVM 默认隔离**：两个模块的 surefire 设 `agent.demo.home=${project.build.directory}/test-home`，让「跑测试不污染运行时数据」成为默认行为而非各测试自觉（此前只有 3 个测试自己设）。
- **补测试**：优先链三档 + 空白值处理 + `AgentConfig.logging().dir()` 跟随覆盖 + 一次真实回合的 per-session 日志落在隔离目录内。
- 顺带把 `AgentPaths` 的常量对齐 `WebAgentRuntime.AGENT_DEMO_HOME_PROPERTY`（保留旧常量作 `@Deprecated` 别名，不动既有测试）。

## Impact

- 受影响能力：`observability`（两条既有 Requirement 由「纸面」变为「成立」）。
- 行为变更：`agent.demo.home` 从「只影响会话存档」变为「影响全部 agent 数据与日志路径」——这正是它名字的语义；未设该属性时行为与现在**完全一致**（仍回落 `~/.agent-demo`）。
- 测试环境变更：两个模块的 surefire 默认设 `agent.demo.home`，此前写入真实 `~/.agent-demo` 的测试产物将改为写入 `target/test-home/`。
- 不改：`E2EBase` 的 Selenium 辅助路径（与 agent home 无关）。

## Out of Scope

- 把 9 处重复的 `firstNonBlank(env, user.home)` 逻辑全部改造成依赖注入（本次只做路径解析收敛，不改调用形态）。
- CLI 的 `--home` flag 语义调整。
- 历史遗留的真实 `~/.agent-demo/logs/sessions/` 目录清理（属运维动作，不在代码范围）。
