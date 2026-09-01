# memory Specification (delta)

> 本文件是 `add-plugin-system` 的 delta spec。archive 时合并到 `openspec/specs/memory/spec.md`。

## ADDED Requirements

### Requirement: MemoryRecall 作为 Plugin

系统 SHALL 把 `MemoryRecall` 包装为 `MemoryPlugin`（implements `Plugin` + `SystemPromptFragment`），通过 `SystemPromptFragment.fragment()` 提供 USER / PROJECT / LOCAL 三 scope 的记忆说明，行为与直接调用 MemoryRecall 保持一致。

#### Scenario: 三 scope 记忆说明注入

- **WHEN** 系统装配 system prompt
- **THEN** MemoryPlugin.fragment() 返回包含 USER / PROJECT / LOCAL 三 scope 的记忆说明，并注入 system prompt

#### Scenario: MemoryPlugin 不注册记忆工具

- **WHEN** 系统装配工具
- **THEN** 记忆相关工具不由 MemoryPlugin 重复注册，仍由 buildLoop 的 registerMemoryTools 注册一次

### Requirement: 记忆工具单一注册路径

系统 SHALL 保持记忆工具的单一注册路径：记忆工具由 `buildLoop` 的 `registerMemoryTools` 注册一次，MemoryPlugin 仅作为 SystemPromptFragment 提供记忆说明，不通过 Plugin 框架重复注册。

#### Scenario: buildLoop 保留 registerMemoryTools

- **WHEN** buildLoop 装配 agent
- **THEN** 系统先调用 registerMemoryTools 注册记忆工具，再通过 PluginManager 初始化 MemoryPlugin

#### Scenario: 不重复注册记忆工具

- **WHEN** MemoryPlugin 已作为 SystemPromptFragment 生效
- **THEN** 记忆工具仍只注册一次，不存在重复注册
