# mcp Specification (delta)

> 本文件是 `add-plugin-system` 的 delta spec。archive 时合并到 `openspec/specs/mcp/spec.md`。

## ADDED Requirements

### Requirement: McpClient 作为 Plugin

系统 SHALL 把 `McpClient` 包装为 `McpPlugin`（implements `Plugin` + `ToolProvider`），内部持有 `List<McpClient>`，使 MCP 客户端通过 Plugin 框架装配，行为与直接注册保持一致。

#### Scenario: McpPlugin 包装多个 McpClient

- **WHEN** 配置了多个 MCP server
- **THEN** McpPlugin 持有对应的 McpClient 列表，并在 init 时对每个 client 执行 `initialize()` 握手

#### Scenario: 握手失败隔离

- **WHEN** 某个 MCP server 的 `initialize()` 握手失败
- **THEN** 该 server 跳过（记录 WARN），其余 server 正常握手，agent 主流程不中断

### Requirement: MCP 工具名唯一化

系统 SHALL 在 `McpPlugin.tools()` 中调用各 client 的 `listTools()`，把返回的工具包装为 `McpTool` 并注册，工具名唯一化为 `serverName.toolName`，避免不同 server 的同名工具冲突。

#### Scenario: 工具名加 server 前缀

- **WHEN** 两个 server 各有一个同名工具
- **THEN** 系统以 `serverName.toolName` 分别注册，两个工具可区分、无冲突

#### Scenario: 无工具不注册

- **WHEN** 某 server 的 `listTools()` 返回空
- **THEN** 该 server 不注册任何 MCP 工具，其余 server 正常
