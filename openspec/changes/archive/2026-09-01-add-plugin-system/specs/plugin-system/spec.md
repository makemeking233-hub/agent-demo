# plugin-system Specification (delta)

> 本文件是 `add-plugin-system` 的 delta spec。archive 时合并到 `openspec/specs/plugin-system/spec.md`。

## ADDED Requirements

### Requirement: Plugin 生命周期

系统 SHALL 通过 `PluginManager` 管理 Plugin 生命周期：`init(PluginContext)` 按 `AgentConfig.plugins` 列表顺序依次调用，`close()` 按相反顺序依次调用。

#### Scenario: 按列表顺序初始化

- **WHEN** `AgentConfig.plugins` 配置了多个 plugin
- **THEN** 系统按列表顺序逐个调用 `init(PluginContext)`，先配置的 plugin 先初始化

#### Scenario: 反序关闭

- **WHEN** 所有 plugin 已初始化并触发 shutdown
- **THEN** 系统按初始化顺序的相反顺序调用 `close()`

### Requirement: 失败隔离

系统 SHALL 隔离单个 Plugin 的初始化/关闭异常：任一 `init` 或 `close` 抛异常时记录 WARN 并跳过该 Plugin，不影响其余 Plugin 的初始化与关闭，也不阻断 agent 主流程。

#### Scenario: 单个 init 抛异常跳过继续

- **WHEN** 某个 plugin 的 `init` 抛异常
- **THEN** 系统记录 WARN，跳过该 plugin，继续初始化列表中的后续 plugin，agent 正常启动

#### Scenario: 单个 close 抛异常继续

- **WHEN** 某个 plugin 的 `close` 抛异常
- **THEN** 系统记录 WARN，继续关闭其余 plugin，shutdown 不被中断

### Requirement: 重复 name 拒绝

系统 SHALL 拒绝重复的 plugin name：当同一 `PluginManager` 生命周期内出现相同 name 的 Plugin 时，仅初始化第一次出现的实例，后续重复的实例跳过。

#### Scenario: 重复 name 第二次 init 跳过

- **WHEN** 两个 plugin 的 `name()` 返回值相同
- **THEN** 系统只初始化第一个，跳过第二个（记录 WARN），不重复注册

#### Scenario: 不同 name 全部初始化

- **WHEN** 各 plugin 的 `name()` 互不相同
- **THEN** 系统初始化所有 plugin

### Requirement: PluginContext 注入

系统 SHALL 在调用 `init` 时注入一个 `PluginContext`，向 Plugin 暴露 `AgentConfig` 配置、`ToolRegistry` 工具注册表、providers 列表、slashCommands 列表、system prompt fragment、chat request mapper 等上下文，并 SHALL 提供 `tools()` 便捷方法访问 ToolRegistry。

#### Scenario: init 收到 PluginContext

- **WHEN** 系统初始化一个 plugin
- **THEN** 该 plugin 的 `init` 收到非空 PluginContext，可读取 cfg、通过 tools() 访问 ToolRegistry

#### Scenario: 上下文修改可见

- **WHEN** plugin 通过 PluginContext 注册工具或追加 system prompt fragment
- **THEN** 注册的工具与 fragment 在 agent 装配中被采用

### Requirement: 扩展点注册路径

系统 SHALL 支持五个扩展点 marker interface，并按各自路径装配：`ToolProvider.tools()` 注册工具、`LlmProviderExtension.provider()` 注册 LLM provider、`SlashCommandProvider.commands()` 注册 slash 命令、`SystemPromptFragment.fragment()` 追加 system prompt 片段、`ChatRequestMapper.map()` 转换 chat 请求。

#### Scenario: ToolProvider 注册工具

- **WHEN** 一个 plugin 实现 `ToolProvider` 且 `tools()` 返回非空列表
- **THEN** 系统把返回的工具注册进 ToolRegistry

#### Scenario: SystemPromptFragment 追加片段

- **WHEN** 一个 plugin 实现 `SystemPromptFragment` 且 `fragment()` 返回非空文本
- **THEN** 系统把该文本追加进 system prompt

#### Scenario: 扩展点缺省实现为空

- **WHEN** 一个 plugin 未实现某扩展点（或使用 default 空实现）
- **THEN** 该扩展点不产生任何副作用，其余扩展点正常工作
