## Why

agent-demo 现有的三类外挂点（Memory 记忆、Skills 技能、MCP 客户端）都硬编码在 `AgentLoopFactory.buildLoop` 的 `registerXxxTools` 静态方法里。v1.0 规划的 Team Memory 远程同步、Prompt Cache 复用等也要以扩展点形式落地，继续在 `buildLoop` 里逐项硬编码会重复造轮子、让装配逻辑不断膨胀。本次抽取统一的 Plugin 插件框架，把外挂点收敛为可插拔的 Plugin。

## What Changes

- **Plugin 接口**：新增单一 `Plugin` 接口（`init(PluginContext)` / `close()`，default 空实现），一个 Plugin 可实现多个扩展点（tool / provider / slash / system-prompt-fragment / chat-request-mapper）。
- **PluginContext 注入**：新增 `PluginContext`（非 Spring 的显式 DI 容器），向 Plugin 注入 `AgentConfig`、`ToolRegistry`、providers、slashCommands、system prompt fragment、chat request mapper 等上下文。
- **5 个 ExtensionPoint**：`ToolProvider` / `LlmProviderExtension` / `SlashCommandProvider` / `SystemPromptFragment` / `ChatRequestMapper` 五个 marker interface，default 方法返空，Plugin 按需实现。
- **AgentConfig.plugins**：`AgentConfig` 新增 `List<PluginConfig>`（yaml 列表，含 className 等），`PluginManager` 按列表顺序 init、反序 close。
- **现有外挂迁移**：`McpClient` → `McpPlugin`、`SkillCatalog` → `SkillsPlugin`、`MemoryRecall` → `MemoryPlugin`（均 `implements Plugin`），行为不变、注册路径变更。
- **向后兼容**：旧 `registerXxxTools` 静态方法标 `@Deprecated(since="v1.0", forRemoval=true)` 保留，内部逻辑不变。
- **装配接入**：`buildLoop` 改为手动 `registerMemoryTools` 一次 → `new PluginManager(cfg.plugins).init(ctx)`；shutdown hook 调 `PluginManager.close()`。
- **文档**：`docs/guides/plugins.md`（hello-world + 多扩展点范例），`design.md §5.2` 指向该文档。

## Capabilities

### New Capabilities
- `plugin-system`: Plugin 插件框架（Plugin 生命周期、PluginContext 注入、5 个扩展点、失败隔离）。

### Modified Capabilities
- `mcp`: `McpClient` 抽为 `McpPlugin`（implements Plugin + ToolProvider），init 读 `cfg.mcp.servers` 握手，tools 返回 `serverName.toolName` 唯一化的 `McpTool`。
- `skills`: `SkillCatalog` 抽为 `SkillsPlugin`（ToolProvider + SystemPromptFragment），扫技能目录、注册 `SkillTool`、注入技能列表摘要。
- `memory`: `MemoryRecall` 抽为 `MemoryPlugin`（SystemPromptFragment），提供三 scope 记忆说明；记忆工具仍由 buildLoop 的 `registerMemoryTools` 注册。

## Impact

- 受影响装配：`agent-core/.../core/AgentLoopFactory.buildLoop`（改为 PluginManager.init + shutdown hook close）。
- 受影响配置：`AgentConfig` 新增 `PluginConfig` + `plugins` 列表（yaml 解析）。
- 新增包：`agent-core/.../plugin/`（Plugin / PluginContext / ExtensionPoints / PluginManager 等 7 文件）。
- 迁移类：`McpClient` → `plugin/mcp/McpPlugin`、`SkillCatalog` → `plugin/skill/SkillsPlugin`、`MemoryRecall` → `plugin/memory/MemoryPlugin`；旧类位置不动。
- 向后兼容：`ToolRegistry.registerMcpTools/registerSkillTools/registerMemoryTools` 标 `@Deprecated`，内部逻辑不变。
- 测试：新增 `PluginManagerTest` / `McpPluginTest` / `SkillsPluginTest`（init/close 顺序、close 异常隔离、重复名拒绝、完整链路）。
- 无破坏性 API 变更（新增 plugin 包；旧静态方法保留；整个 change 局限 agent-core，不干扰 agent-web）。
