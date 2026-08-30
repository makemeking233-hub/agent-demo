# Design: History Cost Read Config

## Architecture

```
[User: /history]
  ↓
SlashCommand.dispatch → /history 分支
  ↓
estimateCost(prompt, completion, model)
  - input:  prompt / 1_000_000 * this.cost.inputPerMTokens
  - output: completion / 1_000_000 * this.cost.outputPerMTokens
  ↓
println("估算费用: ¥X")
```

## Files Changed

- `agent-core/.../cli/SlashCommand.java`：
  - 构造加 `AgentConfig.Cost cost` 参数
  - `estimateCost` 读 `this.cost` 字段（不再硬编码 2.0/8.0）
- `agent-core/.../cli/ChatCommand.java`：构造 `SlashCommand` 时传 `cfg.cost()`
- `agent-core/.../cli/SlashCommandTest.java`：estimateCost 测试改用 mock cost 或 0 成本

## Key Design Decisions

- **Cost 由构造时注入**（不变更 dispatch 签名，SlashCommand 是无状态 bean）
- **model 字段参数保留但不立即 per-model 映射**（v0.2 仅 per-provider 默认值；deepseek-chat 和 deepseek-reasoner 暂用同一 cost 块）
- **不创建 `cost/` 子包**（Cost 已经是 AgentConfig 的 record，足够）
