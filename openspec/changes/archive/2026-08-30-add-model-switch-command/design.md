# Design: Add /model Switch Command

## Architecture

```
[User: /model deepseek-reasoner]
  ↓
ChatCommand.handleLine()
  ↓
SlashCommand.dispatch(/model ...) → /model 分支
  ↓
onModel.accept("deepseek-reasoner")   ← Consumer<String>
  ↓
ctx.loop().setModel("deepseek-reasoner")
  ↓
AgentLoop.model = "deepseek-reasoner"  ← 下一轮 toRequest() 用新 model
  ↓
[/model] 切换到 deepseek-reasoner
```

## Files Changed

- `agent-core/src/main/java/.../cli/SlashCommand.java`：加 `/model` 分支 + onModel 回调
- `agent-core/src/main/java/.../cli/ChatCommand.java`：ReplContext 加 onModel 字段（lambda 注入 setModel）
- `agent-core/src/main/java/.../core/AgentLoop.java`：加 `setModel(String)` 方法
- `agent-core/src/main/java/.../core/AgentLoop.java`：model 字段改为非 final

## Key Design Decisions

- **`/model` 无参数**：列出当前 model + 支持的 model 名（类似 `/help`）
- **`/model <name>`**：切换并打印"切换到 <name>"
- **未知 model**：不修改 state，仅打印错误
- **不在 ChatCommand 层做 model 白名单校验**：放在 SlashCommand 内部（更内聚）
- **model 校验只校验 DeepSeek 系 model**：v0.2 仅 DeepSeek 一个 provider；多 provider 后扩展

## OpenSpec-aligned decisions

- 不创建新的 spec domain（`model-management` 实际是 `cli` 域的子能力）—— 把 spec 写进 `specs/cli/spec.md` 的 ## ADDED Requirements
- 简化：1 个 cli spec 增量，不建独立 domain
