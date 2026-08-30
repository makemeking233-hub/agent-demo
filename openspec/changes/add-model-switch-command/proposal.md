## Why

当前切 model 只能通过 `--model` CLI flag 启动时指定。v0.2 要求**运行时**切 model（如 deepseek-chat ↔ deepseek-reasoner），让用户无需重启 REPL 即可切换。AgentLoop 当前的 model 是构造时硬编码，slash 命令层需要新加 `/model` 触发 model 切换 + AgentLoop 支持运行时 setModel()。

## What Changes

- 新增 `/model <name>` slash 命令：运行时切换当前 REPL 的 model
- SlashCommand 接收 model setter 回调（类似 onClear）
- ChatCommand 注入 onModel 回调 → 调 AgentLoop.setModel(name)
- AgentLoop 加 setModel(String) 方法（已存在 `model` 字段，加 setter）
- 当前支持的 model 名：`deepseek-chat`（默认）、`deepseek-reasoner`（v0.2 启用）
- 无效 model 名 → 提示"未知 model: <name>（支持: deepseek-chat, deepseek-reasoner）"，不改 state

## Capabilities

### New Capabilities
- 无（model 切换归入 cli 域的子能力）

### Modified Capabilities
- `cli`：加 `/model <name>` 行为（与 `/help /clear /quit /history /resume` 并列）；spec 用 ## ADDED Requirements 增量

## Impact

- 受影响代码：`SlashCommand.java`、`ChatCommand.java`、`AgentLoop.java`
- API 兼容：AgentLoop 加新方法（向后兼容）
- 估计：1 天（3-4 task）
- Out of Scope：动态 provider 切换（v0.2 仅 DeepSeek 内 model 切换；AnthropicProvider 切换是独立 change）
