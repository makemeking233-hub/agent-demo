## Why

agent-demo 的 agent 目前直接在项目工作目录（`user.dir`）里读写文件、执行命令，会污染用户正在开发的真实工作区，且无法安全并行多会话。v1.0 规划 Worktree 模式：为每个 agent 会话提供独立的 git worktree 作为隔离工作目录，避免污染主仓库、支持多会话并行。

## What Changes

- **WorktreeManager**：用 JGit（本地仓库已有的 `org.eclipse.jgit`，离线可用）封装 git worktree 的创建 / 列表 / 移除。
- **/worktree 子命令**：`/worktree create [branch]`（创建并进入）、`/worktree list`（列出）、`/worktree remove [name]`（移除）。
- **会话启动自动创建**：当 `worktree.enabled=true`（默认关）时，会话启动自动为该会话创建一个 worktree 并作为 workingDir；`/worktree` 命令手动切换/管理。
- **config `worktree`**：`enabled`（默认 false）、`baseDir`（worktree 放置目录，默认 `<user home>/.agent-demo/worktrees`）。
- **AgentLoop workingDir 注入**：worktree 模式开启时，`AgentLoopFactory.buildLoop` 用当前 worktree 路径作为 workingDirectory（而非 `user.dir`）。

## Capabilities

### New Capabilities
- `worktree`: git worktree 隔离工作区（创建/列出/移除 + 作为 agent 工作目录）。

### Modified Capabilities
- （无：`openspec/specs/` 下没有既有 worktree spec；本次新增）

## Impact

- 受影响装配：`agent-core/.../core/AgentLoopFactory.buildLoop`（worktree 模式时用 worktree 路径作 workingDir）。
- 受影响 CLI：`agent-core/.../cli/SlashCommand`（新增 `/worktree` 分支）。
- 配置：`AgentConfig`/`ConfigLoader` 加 `worktree` 段（enabled / baseDir）；`AgentLoopFactory` 读。
- 新增类：`agent-core/.../worktree/WorktreeManager`、`WorktreeManagerTest`。
- 依赖：`pom.xml` 加 `org.eclipse.jgit`（本地 Maven 仓库已有 6.7.0）。
- 无破坏性 API 变更（新增模块；SessionStore/工具不变）。

## Out of Scope

- 不自动清理 worktree（用户 `/worktree remove` 手动，避免误删）；
- 不做 worktree 的跨分支 merge/rebase 辅助；
- 不改变会话存档/日志位置（仍独立）。
