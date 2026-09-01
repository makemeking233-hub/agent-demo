# Plugin 插拔扩展框架（add-plugin-system v1.0）

## Context

agent-demo 现有三类外挂点（Memory/Skills/MCP）硬编码在 `AgentLoopFactory.buildLoop` 的 `registerXxxTools` 静态方法中；v1.0 还有 Team Memory 远程同步 / Prompt Cache 复用也要以扩展点落地，重复造轮子，本次抽 Plugin 框架。

约束：JDK17 / SpringBoot 3.2（CLI profile 不用 Spring，Plugin 不能依赖 Spring Bean）；现有 ToolRegistry / LlmProvider / SlashCommand / system prompt 入口要继续工作。

## Goals / Non-Goals

**Goals:**

- 单一 Plugin interface（`init(PluginContext)` / `close()`，多扩展点 tool/provider/slash/system-prompt-fragment/chat-request-mapper）。
- `AgentConfig.plugins: List<PluginConfig>` yaml 列表。
- 把 McpClient/SkillCatalog/MemoryRecall 抽成 McpPlugin/SkillsPlugin/MemoryPlugin（implements Plugin），行为不变、注册路径变。
- 写测试（init/close 顺序 / close 异常隔离 / 重复名拒绝）。

**Non-Goals:**

- 不做外部 jar 接入（ServiceLoader 双轨）。
- 不做 Spring 容器集成。
- 不做热重载 / 动态加载。
- 不做远程 plugin。
- 不改 Spring 启动顺序 / 不动 agent-web。

## Decisions

**D1: Plugin interface 用 default 空实现。**

```java
default String name() { return getClass().getSimpleName(); }
default void init(PluginContext ctx) throws Exception {}
default void close() throws Exception {}
```

否决 AbstractPlugin 基类（单继承）与 Spring InitializingBean/Closeable（污染 CLI）。

**D2: PluginContext 显式 DI 容器（非 Spring）。**

```java
record PluginContext(
    AgentConfig cfg,
    ToolRegistry tools,
    AtomicReference<List<LlmProvider>> providers,
    AtomicReference<List<SlashCommand>> slashCommands,
    AtomicReference<String> fragments,
    UnaryOperator<ChatRequest> requestMappers
)
```

有 `tools()` 便捷方法。

**D3: 5 个 ExtensionPoint marker interface。**

- `ToolProvider { default List<Tool<?,?>> tools() { return List.of(); } }`
- `LlmProviderExtension { default LlmProvider provider() { return null; } }`
- `SlashCommandProvider { default List<SlashCommand> commands() { return List.of(); } }`
- `SystemPromptFragment { default String fragment() { return ""; } }`
- `ChatRequestMapper { default ChatRequest map(ChatRequest req, AgentConfig cfg) { return req; } }`

**D4: init/close 顺序与失败容忍。**

init 顺序 = `AgentConfig.plugins` 列表序，close 反序；单个 plugin init 抛异常 → WARN 跳过继续；close 抛异常 → WARN 继续。失败容忍不阻断 agent。

**D5: McpClient 抽成 McpPlugin。**

`McpPlugin implements Plugin + ToolProvider`，包装 `List<McpClient>`，init 时 `client.initialize()` for each，`tools()` 调 `client.listTools()` 包装 McpTool 注册，工具名唯一化 `serverName.toolName`。

**D6: 老 registerXxxTools 静态方法。**

加 `@Deprecated(since="v1.0", forRemoval=true)` 保留（内部逻辑不变，deprecated wrapper 无 PluginContext 不能转发 Plugin.init）。

## Risks / Trade-offs

- [init 抛异常 close 顺序不完整] → 失败容忍 + 全局 try/catch。
- [className typo] → Class.forName 失败记 ERROR 跳过。
- [Plugin 隐式依赖] → docs 写明顺序约定。
- [资源泄漏] → close 反序测试。
- [agent-web 不干扰] → 纯 agent-core。

## Migration Plan

1. `plugin/` 包新建 7 文件。
2. `AgentConfig` 加 PluginConfig + plugins。
3. 三个 Plugin 迁到 `plugin/{mcp,skill,memory}/`；旧类位置不动，旧静态方法标 deprecated。
4. `buildLoop` 改为手动 registerMemory 一次 → `new PluginManager(cfg.plugins).init(ctx)`。
5. 写 PluginManagerTest + McpPluginTest + SkillsPluginTest。
6. `docs/guides/plugins.md` 新增；`design.md §5.2` 替换为指向新 doc。

回滚：删 `plugin/` 包 + `AgentConfig.plugins` 字段 + 移除 `@Deprecated`，整个 change 局限 agent-core，1 commit revert。

## Open Questions

- 外部 jar plugin 的 ServiceLoader 双轨接入时机（本 change 明确为 Non-Goal，后续 change 再议）。
