# mcp Specification

## Purpose
TBD - created by archiving change add-mcp-client. Update Purpose after archive.
## Requirements
### Requirement: MCP server 连接与握手

系统 SHALL 通过 MCP Streamable HTTP 传输（JSON-RPC 2.0 over POST，WebClient）连接一个或多个 MCP server，并在启动时执行 `initialize` 握手交换协议版本与能力。

#### Scenario: 连接并握手成功

- **WHEN** `mcp.servers` 配置了一个可访问的 MCP server URL
- **THEN** 系统对每个 server 发起 `initialize` 握手，成功建立会话（携带 `protocolVersion`、`capabilities`、`clientInfo`）

#### Scenario: server 不可达时不阻断

- **WHEN** 某 MCP server URL 不可达或握手失败
- **THEN** 该 server 跳过（记录 WARN），agent 其余工具正常，主流程不中断

### Requirement: MCP 工具发现与融合

系统 SHALL 用 `tools/list` 列出每个已连接 MCP server 的工具，并把每个工具注册为 agent 可调用的 `Tool`（工具名 = MCP 工具名，描述 = MCP 工具描述，schema 来自 MCP 输入 schema）。

#### Scenario: MCP 工具注册为 agent 工具

- **WHEN** 一个 MCP server 的 `tools/list` 返回若干工具
- **THEN** 这些工具出现在 agent 的工具列表中，模型可发起调用

#### Scenario: 无工具时不注册

- **WHEN** `tools/list` 返回空或 server 无工具
- **THEN** 不注册任何 MCP 工具，agent 其它工具正常

#### Scenario: server 不可达时不注册该 server 工具

- **WHEN** 某 MCP server 连接/握手失败
- **THEN** 该 server 的工具全部不注册，其余 server 正常

### Requirement: MCP 工具调用

系统 SHALL 在模型调用一个 MCP 工具时，用 `tools/call` 把工具名与参数转发到对应 server，并把返回的 `result.content`（结构化数组，含 text / image / resource 块）聚合为模型可读文本回流。

#### Scenario: 调用成功返回结果

- **WHEN** 模型调用某 MCP 工具
- **THEN** 系统转发 `tools/call`，把返回的 content 块聚合为文本作为工具结果回流给模型

#### Scenario: 调用失败返回错误

- **WHEN** `tools/call` 返回错误或 server 调用异常
- **THEN** 系统返回错误工具结果（isError=true），不阻断整轮

#### Scenario: 工具不可用兜底

- **WHEN** 模型调用一个已在工具列表但 server 已断开/不存在
- **THEN** 系统返回错误工具结果提示工具不可用，不抛未捕获异常

