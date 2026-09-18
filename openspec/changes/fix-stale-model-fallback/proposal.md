# fix-stale-model-fallback

## Why

`deepseek-chat` 已被 DeepSeek 于 2026-07-24 停用，`agent.chat.providers` 目录里也不存在这个 id，但代码里仍有 6 处把它当默认值/兜底值使用。后果是**服务端最终原样把已停用的 id 发给上游**：

1. 前端 `App.tsx` 兜底发出 `deepseek-chat`；
2. 服务端 `ChatController.resolveModel` 校验不通过，**又兜回同一个 `deepseek-chat`**；
3. 该 id 既非合法值、也未被拒绝，直接透传给 DeepSeek。

更根本的问题不是「常量写错了」，而是**存在两个互不相干的真源**：

| 用途 | 真源 | 载体 |
|------|------|------|
| 前端看到的合法模型列表 | `agent.chat.providers`（yaml） | `ModelCatalog` |
| 服务端校验的合法模型列表 | `agent.chat.supported-models` | `ModelRegistry.DEFAULT_MODELS` 常量 |

两者**目前只是碰巧一致**。yaml 里增删一个模型，前端就会列出服务端要拒的 id，然后被静默兜回另一个同样非法的值——只改常量不解决这个结构性问题。

这个 bug 能潜伏至今，还因为**成功回合不记 model 日志**：日志只在回合失败时打 `model=`，所以「前端显示什么 vs 实际请求什么」无从对照。

## What Changes

- **后端校验收敛到单一真源**：`ChatController` 改为以 `ModelCatalog`（即 yaml `agent.chat.providers`）校验 `model`，不再读 `agent.chat.supported-models`；删除 `ModelRegistry`。
- **非法模型 fail-closed**：`model` 非空但不在目录中 → `400 invalid_model`（附合法列表），**不再静默兜回**；`null`/空白才走配置默认值。这是本 bug 能潜伏的第二个原因——静默降级让调用方发错模型也无人知晓。
- **默认模型随目录走**：`ProviderCatalogService` 启动时校验 `default-provider` / `default-model` 必须存在于目录中（fail-fast），杜绝「默认值本身非法」再次发生。
- **前端不再硬编码模型 id**：`GET /api/chat/models` 响应新增 `defaultProvider` / `defaultModel` 字段，`App.tsx` 用它兜底，清掉 4 处 `deepseek-chat`。
- **微信通道同源**：`WecomMessageDispatcher` 的 `DEFAULT_MODEL` 常量改为读目录默认值（第 6 处）。
- **补成功回合的模型日志**：与失败路径对称，成功回合也记 `model=`，让「显示 vs 实际」可直接查日志定死。

## Impact

- 受影响能力：`web-ui`（模型端点、发送校验）、`observability`（成功/失败上报对称）、`wecom-channel`（默认模型）。
- API 行为变更（**有意的破坏性变更**）：`POST /api/chat/send` 对非法 `model` 由「200 + 静默兜底」改为「400 `invalid_model`」。前端已同步校验，本地 `localStorage` 里的历史 `deepseek-chat` 会被目录校验覆盖。
- 响应增量：`GET /api/chat/models` 新增 `defaultProvider` / `defaultModel` 两个字段（纯新增，老的 `models` 平铺字段保留）。
- 删除：`agent-web/src/main/java/com/example/agent/web/api/ModelRegistry.java`。
- 不改：`agent-core` 的 `AgentLoop.DEFAULT_MODEL` / `AgentConfig.defaults()` / `SlashCommand` 列表（属 CLI 路径，本次刻意不碰，见 design.md「Out of Scope」）。

## Out of Scope

- CLI 路径的同类残留（`AgentLoop.DEFAULT_MODEL`、`AgentConfig.defaults()`、`SlashCommand` 的 `deepseek-chat`）——用户明确选择「不带 CLI」。
- 多 provider 运行时路由（另由 `add-provider-catalog-abstract` 承载）。
- 前端两层菜单（provider / model）交互改造。
