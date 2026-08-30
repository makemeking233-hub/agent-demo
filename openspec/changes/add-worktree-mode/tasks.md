# Tasks: Worktree 模式

## 1. 配置：AgentConfig 加 worktree

- [x] 1.1 新增 `AgentConfig.Worktree(boolean enabled, String baseDir)` record + defaults（enabled=false, baseDir=~/.agent-demo/worktrees）
- [x] 1.2 `ConfigLoader` 解析 `worktree.enabled`/`worktree.baseDir`

## 2. WorktreeManager（git CLI）

- [x] 2.1 新增 `WorktreeManager`：`create(name, branch)` / `list()` / `remove(name)`，用 ProcessBuilder 调 `git worktree`；失败降级（WARN，不抛未捕获异常）
- [x] 2.2 新增 `WorktreeInfo(path, branch)` record；`list()` 解析 `--porcelain`

## 3. /worktree 子命令

- [x] 3.1 `SlashCommand` 加 `/worktree create` / `/worktree list` / `/worktree remove` 分支（注入 WorktreeManager）

## 4. AgentLoop 集成

- [x] 4.1 `AgentLoopFactory.buildLoop` 读 `cfg.worktree()`；enabled 时用 WorktreeManager 创建 worktree 并作为 workingDir，否则用 user.dir

## 5. 测试与验证

- [x] 5.1 新增 `WorktreeManagerTest`（临时 git 仓库 + 真实 git worktree）
- [x] 5.2 `mvn -pl agent-core verify` 全绿（237 测试，jacoco 门禁达标）
- [x] 5.3 commit + push（中文 Conventional Commits）
