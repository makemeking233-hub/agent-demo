## Why

`SlashCommand.estimateCost()` 当前硬编码 DeepSeek-chat 定价（输入 2 ¥/M tokens、输出 8 ¥/M tokens）。`/history` 显示的估算费用是错的（用户切到 `deepseek-reasoner` 后还是按 deepseek-chat 价格算）。plan §"已知简化"明确要求 v0.2 改为读 `AgentConfig.cost()`。

## What Changes

- `SlashCommand` 接收 `AgentConfig.Cost cost` 字段（或构造时注入）
- `estimateCost(prompt, completion, model)` 用 `cost.inputPerMTokens` / `cost.outputPerMTokens` 替换硬编码
- `ChatCommand` 构造 `SlashCommand` 时传入 `cfg.cost()`
- model 名作为 `key` 预留（v0.2 只用 cost.prices 默认值；v0.3+ per-model 价格映射）

## Capabilities

### Modified Capabilities
- `cli`：/`history` 输出改为读 AgentConfig.cost()，不再硬编码

## Impact

- 受影响代码：`SlashCommand.java`（构造签名 + estimateCost）、`ChatCommand.java`（注入 cost）
- 行为变化：`/history` 输出费用随 config.yaml 的 `cost.inputPerMTokens`/`cost.outputPerMTokens` 变化
- 测试：SlashCommandTest 新增 / 更新 estimateCost 测试（不再断言 2/8 硬编码）
- 估计：< 1 天（1 个 task）
