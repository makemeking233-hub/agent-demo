# fix-cli-residue

## Why

CLI 路径仍残留 `deepseek-chat`（已停用、web-ui 已清掉）。具体三处：

| 位置 | 影响 |
|------|------|
| `AgentLoop.DEFAULT_MODEL` | CLI 单 AgentLoop 缺 model 时的回退 |
| `AgentConfig.defaults().provider().model` | CLI 配置覆盖链的「最末默认」 |
| `ChatCommand` 错误消息示例 / `ChatRequest` javadoc | 误导后继 |

不动 `SlashCommand` 的 `/model chat` 别名（`List.of("deepseek-chat", "deepseek-reasoner")` 和 `{"deepseek","deepseek-chat"}`）——那是被 spec 固定的向后兼容语义（cli/spec.md / web-ui/spec.md 都有 Scenario 约束），本次清「残留」是清**默认值**而非清**别名**。

## What Changes

- `AgentLoop.DEFAULT_MODEL`: `deepseek-chat` → `deepseek-v4-flash`（与 web profile `agent.chat.default-model` 一致）。
- `AgentConfig.defaults()` Provider.model: 同上。
- `ChatCommand:432` 错误消息示例的 fallback 模型名：`deepseek-chat` → `deepseek-v4-flash`。
- `ChatRequest` 类注释里的 `@param model` 示例：同上。
- `ConfigLoaderTest.defaultsWhenNoFile`：断言改为 `deepseek-v4-flash`。

## Impact

- 行为变更：CLI 启动且未配 model/env 时，回退模型从 `deepseek-chat`（已停用）→ `deepseek-v4-flash`（当前合法）。与 web profile 行为对齐。
- 不变：`/model chat` 别名行为、`/model reasoning` 别名行为、所有 `/api/*` HTTP 校验（仍按目录拒绝 `deepseek-chat`）。
- 测试：`ConfigLoaderTest.defaultsWhenNoFile` 需改断言（不在 CLI 行为契约里，纯粹的 fixture 同步）。

## Out of Scope

- `SlashCommand` 的 `/model chat` / `/model reasoning` 别名（spec 锁死，本次不动）。
- agent-core `application-local.yml`（gitignored，仅本地示例）。
