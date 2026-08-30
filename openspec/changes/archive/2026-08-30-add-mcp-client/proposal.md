## Why

v0.4 规划的 MCP 客户端集成尚未实现（design.md §15 记为 v0.4 待实现）。agent-demo 目前只有内置工具集（ReadFile/Shell/Ls 等），无法接入外部 MCP server 提供的工具（如数据库、浏览器、第三方 API 等）。MCP（Model Context Protocol）是标准协议，让 agent 能发现并调用 MCP server 暴露的任意工具。

## What Changes

- **MCP Streamable HTTP 客户端**：用 WebFlux WebClient 实现 JSON-RPC 2.0（POST），`initialize` 握手 + `tools/list` + `tools/call`。
- **工具发现与融合**：`tools/list` 返回的每个 MCP 工具注册为 agent 的 `Tool`（`McpTool`），`execute` 转发 `tools/call` 并把结果（含结构化 content 数组）回流给模型。
- **config `mcp.servers` 列表**：在 config.yaml 配置（name + url），启动时逐个连接并融合工具进 `ToolRegistry`。
- **流式 content**：`tools/call` 返回的 result.content 支持结构化数组（text/image/resource 块），`McpTool` 聚合为模型可读文本。SSE 通知通道预留（扩展点，本 change 提供连接建立，不深度处理通知）。

## Capabilities

### New Capabilities
- `mcp`：MCP 客户端集成（Streamable HTTP 连接、工具发现、工具调用融合）。

### Modified Capabilities
- （无：`openspec/specs/` 下没有既有 mcp spec；本次新增）

## Impact

- 受影响配置：`AgentConfig`/`ConfigLoader` 加 `mcp.servers`（name + url 列表）。
- 受影响装配：`agent-core/.../core/AgentLoopFactory.buildTools`（连接 MCP server → 融合工具）。
- 新增类：`agent-core/.../mcp/McpClient`、`McpTool`、`McpToolRegistry`（或并入 ToolRegistry）、`McpServerConfig`。
- 无外部依赖（用现有 WebFlux WebClient + Jackson；不引入官方 MCP SDK）。
- 测试：`McpClientTest`（WireMock 模拟 MCP endpoint 的 JSON-RPC 响应）、`McpToolRegistryTest`。
- 无破坏性 API 变更（新增模块；AgentConfig 加字段需同步 defaults/ConfigLoader）。
