# Change: Add /resume Command

## Why
v0.1 SessionStore 只追加写不读取；用户重启 agent 后无法继续上次对话。
v0.2 第一个目标：让用户能 resume 最近一次 session。

## What Changes
- 新增 `/resume` slash 命令：扫描 `~/.agent-demo/sessions/` 最近 .jsonl 文件，反序列化为 MessageHistory，注入 AgentLoop
- SessionStore 加 `loadLatest()` 方法（v0.1 已加 `load(id)` 占位，实现完）
- SlashCommand 加 `/resume` 分支（v0.1 已有 /help /clear /quit /history 4 个）

## Impact
- 受影响 spec: `cli` (新 domain)
- 受影响代码: SessionStore / SlashCommand / AgentLoop / ChatCommand
- 估计: 1.5 天 (T1 SessionStore.loadLatest / T2 SlashCommand / T3 AgentLoop / T4 测试+doc)

## Out of Scope
- session 列表 UI（v0.3）
- 跨 session 关键词检索（v0.3 + sideQuery）
- session 导出 / 导入（v0.4）
