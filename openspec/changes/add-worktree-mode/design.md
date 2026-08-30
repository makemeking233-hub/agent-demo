## Context

v1.0 规划 Worktree 模式：为 agent 会话提供独立 git worktree 隔离工作目录。设计意图（design.md §15）为清单级。调研发现：本地 JGit 6.7.0 **不提供** `git worktree add/list/remove` API（无完整多工作树管理接口），故本 change 用**系统 `git` CLI**（`ProcessBuilder`）实现，跨平台、无额外 maven 依赖；后续若 JGit 升级支持再迁移。

## Goals / Non-Goals

**Goals:**
- `WorktreeManager` 用 git CLI 封装 `create / list / remove`。
- `/worktree` 子命令：`create [branch]` / `list` / `remove [name]`。
- `worktree.enabled=true` 时会话启动自动创建并作为 workingDir。
- config `worktree.{enabled, baseDir}`。

**Non-Goals:**
- 不自动清理 worktree（用户 `/worktree remove` 手动）。
- 不做跨分支 merge/rebase 辅助。
- 不改会话存档/日志位置。

## Decisions

**D1: `WorktreeManager` 封装 git CLI。**
- `create(name, branch)`：在 `baseDir` 下 `git worktree add <baseDir>/<name> [-b <branch>]`。
- `list()`：`git worktree list --porcelain` 解析为 `List<WorktreeInfo(path, branch)>`；错误返回空列表。
- `remove(name)`：`git worktree remove <baseDir>/<name> [--force]`。
- 每个方法用 `ProcessBuilder` 执行，在工作目录（仓库根）运行；失败记录 WARN 返回空/错误标记，不抛未捕获异常。
- 备选：JGit——否决（无 worktree API）。

**D2: `/worktree` 命令（SlashCommand）。**
- `dispatch` 增加 `/worktree create <branch>`、`/worktree list`、`/worktree remove <name>` 分支。
- `WorktreeManager` 注入 SlashCommand（更准确的构造器），或经 `ReplContext` 传递。
- 备选：独立 `WorktreeCommand`。否决——与现有 slash 分发一致，并入 SlashCommand 更贴合。

**D3: config `worktree`。**
- `AgentConfig.Worktree(boolean enabled, String baseDir)`；defaults 默认 enabled=false、baseDir=`<user.home>/.agent-demo/worktrees`。
- `ConfigLoader` 解析 `worktree.enabled`/`worktree.baseDir`。

**D4: AgentLoop workingDir 注入 worktree 路径。**
- `AgentLoopFactory.buildLoop` 读 `cfg.worktree()`；若 enabled，用 `WorktreeManager.create(...)` 创建并以其路径作为 workingDir；否则用 `user.dir`。
- `ToolContext.workingDirectory` 随之指向 worktree。

## Risks / Trade-offs

- [git CLI 非仓库目录 / git 不可用] → WorktreeManager 判空返回；`/worktree` 打印明确错误；主流程继续（用 user.dir）。
- [worktree 累积不清理] → 用户手动 remove；design 明确不自动清理（避免误删）。
- [跨平台 git 路径] → 用仓库根为 cwd 运行 git；git 需在 PATH（项目环境已确认可用）。
- [并发多 worktree 名称冲突] → name 用时间戳 + 短 ID 唯一化。

## Migration Plan

1. `AgentConfig`/`ConfigLoader` 加 `worktree` 配置。
2. 新增 `WorktreeManager`（git CLI）与 `WorktreeInfo`。
3. `SlashCommand` 加 `/worktree` 分支。
4. `AgentLoopFactory.buildLoop` worktree 模式注入 workingDir。
5. 新增 `WorktreeManagerTest`（用临时 git 仓库 + 真实 `git worktree`）。
6. `mvn -pl agent-core verify` 全绿。

## Open Questions

- `/worktree create` 分支名默认：用 `agent-sess-<shortId>`；后续扩展。
