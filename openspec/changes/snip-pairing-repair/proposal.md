## Why

会话 `2026-09-18T13-34-27-b1569b39` 每轮都被 DeepSeek 400：

```text
Messages with role 'tool' must be a response to a preceding message with 'tool_calls'
```

探针复现（读取该会话真实存档 204 条消息 → 走 `toMessages` → 走 `snip`）定位到根因：

```text
toMessages 后：204 条   正向悬挂=0  反向孤儿=0   估算 201855 token（上限 100000）
snip 后：       75 条   正向悬挂=0  反向孤儿=1   ← 裁剪制造了孤儿
```

`SessionResumeLoader.snip` **逐条**从头部丢弃消息，裁剪边界可能落在 `assistant(tool_calls)` 与其
`tool_result` **之间**；而反向孤儿修复（`injectOrphanSkeletons`）只在 `toMessages` 内部执行，即
**在 `snip` 之前**，此后无人再修。孤儿 tool 消息进入内存历史，该会话永久不可用（除非重建会话）。

## What Changes

- **修复 ①（治源头）**：`snip` 的裁剪点按**配对组**对齐——若保留列表以 `ToolResult` 开头（说明切在了
  `assistant(tool_calls)` 与结果之间），继续向后丢到第一条非 tool 结果为止，保证「要么整组保留、要么整组丢弃」。
- **修复 ②（防复发）**：把反向孤儿修复从 `SessionResumeLoader` 的私有方法提取为
  `ToolCallPairing.repairOrphanResults`（纯函数），并在 `AgentLoop.toRequest` 对**每次将要发送的**
  消息列表同时应用正向 `repair` + 反向 `repairOrphanResults`。任何来源的孤儿（snip、异常中断、
  手工改档）都在下一轮自愈，不必等重启或重建会话。
- 连续的孤儿结果合并为**一个**合成 assistant（还原「一次 assistant 的并行 `tool_calls`」语义），
  而非每个结果各插一条骨架。

## Capabilities

### New Capabilities

无新增 capability。

### Modified Capabilities

- `testability`: `恢复与请求两条路径都自愈` 由「只覆盖正向」扩展为「正向 + 反向」；新增
  `裁剪不破坏配对不变式`。

## Impact

| 项 | 内容 |
|----|------|
| 代码 | `agent-core`：`SessionResumeLoader`、`ToolCallPairing`、`AgentLoop#toRequest` |
| 行为 | 恢复路径与请求路径产出的消息列表都满足**双向**配对约束；上述 400 不再出现 |
| API / 依赖 / 配置 | 无变更 |
| 向前兼容 | 已配对的干净历史逐元素不变（幂等）；存档文件只读不改写 |
| 不做 | 已落盘的坏存档不做迁移改写；不做「按轮」而非「按组」的重构 |
