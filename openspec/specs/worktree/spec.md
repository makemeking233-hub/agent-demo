# worktree Specification

## Purpose
TBD - created by archiving change add-worktree-mode. Update Purpose after archive.
## Requirements
### Requirement: Worktree 创建与隔离

系统 SHALL 用 JGit 为会话创建独立的 git worktree，作为 agent 的隔离工作目录；开启 `worktree.enabled` 时，会话启动自动创建，且 agent 的文件读写/命令执行以该 worktree 路径为基准。

#### Scenario: 会话启动自动创建 worktree

- **WHEN** `worktree.enabled=true` 且会话启动
- **THEN** 系统为会话创建独立 worktree，并把该 worktree 路径作为 agent 的 workingDirectory

#### Scenario: worktree 默认不启用

- **WHEN** `worktree.enabled` 未显式开启（默认 false）
- **THEN** 会话仍以项目工作目录（`user.dir`）为工作目录，不创建 worktree

### Requirement: /worktree 命令

系统 SHALL 提供 `/worktree` 子命令管理隔离工作区：`create`（创建并进入）、`list`（列出）、`remove`（移除）。

#### Scenario: /worktree create

- **WHEN** 用户输入 `/worktree create [branch]`
- **THEN** 系统用 JGit 创建对应 worktree 并作为当前会话工作目录，打印其路径

#### Scenario: /worktree list

- **WHEN** 用户输入 `/worktree list`
- **THEN** 系统列出 `baseDir` 下所有 worktree（路径 + 当前分支）

#### Scenario: /worktree remove

- **WHEN** 用户输入 `/worktree remove [name]`
- **THEN** 系统移除该 worktree（JGit remove），并清理其工作区

#### Scenario: /worktree 不可用时优雅降级

- **WHEN** 当前目录不是 git 仓库，或 JGit 操作失败
- **THEN** 命令输出明确错误提示，不抛未捕获异常；主流程继续

### Requirement: Worktree 作为工作目录

系统 SHALL 在 worktree 模式开启时，把当前 worktree 路径注入 `AgentLoop` 的 workingDirectory（替代 `user.dir`），使文件工具（ReadFile/WriteFile/Ls/Shell）的相对路径均相对该 worktree 解析。

#### Scenario: 文件工具相对 worktree 解析

- **WHEN** worktree 模式开启且会话使用某 worktree
- **THEN** 文件工具的相对路径相对该 worktree 解析，而非项目根

#### Scenario: 未开 worktree 模式时行为不变

- **WHEN** worktree 模式未开启
- **THEN** 文件工具相对路径仍相对 `user.dir` 解析（与旧行为一致）

