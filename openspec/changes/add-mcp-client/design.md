## Context

v0.4 规划的 MCP 客户端集成。agent-demo 用 WebFlux WebClient（provider 已用）与 Jackson；MCP Streamable HTTP 协议是 JSON-RPC 2.0 over HTTP（POST），可通过现有 WebClient 实现，无需官方 SDK。目标：连接 MCP server → 发现工具 → 融合进 agent 工具池 → 转发调用。

## Goals / Non-Goals

**Goals:**
- MCP Streamable HTTP 客户端：`initialize` 握手 + `tools/list` + `tools/call`。
- 每个 MCP 工具注册为 agent 的 `Tool`（`McpTool`），`execute` 转发 `tools/call`。
- `tools/call` 返回的 `result.content`（text/image/resource 块）聚合为模型可读文本。
- `config mcp.servers` 列表配置，启动时逐个连接融合。
- server 不可达时优雅降级（跳过该 server，不阻断）。

**Non-Goals:**
- 不处理复杂的 MCP 通知语义（`notifications/*`——本 change 仅建立/忽略，不深度消费）。SSE 通知通道预留为扩展。
- 不做 MCP server 端的 list/call（本项目是**客户端**，只连别人的 server）。
- 不做工具 schema 到 agent `Tool.inputSchema()` 的 1:1 完整转换（MCP schema→agent JDK map 做基本映射，复杂 schema 简化）。
- 不引入官方 MCP SDK（用 WebClient + Jackson 实现协议层）。

## Decisions

**D1: `McpClient` 负责单 server 的 JSON-RPC 交互。**
- 构造 `McpClient(WebClient client, String serverName)`。
- `initialize()`：POST JSON-RPC `initialize`，交换协议版本；`mcp-session-id` header 保存。
- `listTools()`：POST `tools/list`，返回工具列表（name/description/inputSchema）。
- `callTool(name, args)`：POST `tools/call`，返回 `content` 数组。
- 备选：把所有 MCP 逻辑塞进一个静态类。否决——需维护每 server 的会话状态，实例化更清晰。

**D2: `McpTool implements Tool<String, String>`：MCP 工具透传。**
- name = MCP 工具名；description = MCP 工具描述（前置「MCP 工具」前缀）。
- `inputSchema` 用 MCP inputSchema 简化映射（type/object, properties, required）。
- `execute` 调用 `McpClient.callTool(name, args)`，把 content 数组聚合为文本；错误返回 `isError` 结果。
- 备选：MCP 工具直接改造内置 Tool。否决——MCP 工具是动态的，应动态注册 `McpTool`。

**D3: `McpToolRegistry`（或并入 `ToolRegistry`）融合。**
- 静态方法 `registerMcpTools(ToolRegistry registry, List<McpClient> clients)`：对每个 client `listTools()`（成功则逐个注册 `McpTool`），失败跳过。
- `AgentLoopFactory.buildTools` 调用它（从 `cfg.mcp().servers()` 构造 clients）。

**D4: config `mcp.server` 配置。**
- `AgentConfig` 加 `Mcp(List<McpServer>)`，`McpServer(String name, String url)`。
- `ConfigLoader` 解析 `mcp.servers[].name/url`；defaults 默认为空列表。
- 与现有 `AgentLoopFactory` 的 config 装配一致。

**D5: content 数组聚合。**
- `callTool` 返回 result.content（可能为 `List<Map>`，含 `{type: "text", text: "..."}` 等）。
- `McpTool` 聚合：遍历 content，`text` 直接拼接，`image`/`resource` 用占位描述。

## Risks / Trade-offs

- [MCP 协议版本差异] → initialize 带 `protocolVersion`（用 2025-03-26），server 不匹配时报 WARN 跳过。
- [工具 schema 简化映射] → 复杂 JSON Schema 简化；模型仍可传参，复杂用例 v0.2 增强。
- [无真实 MCP server 测试] → 用 WireMock 模拟 JSON-RPC 响应，验证协议层。
- [通知/SSE 流式深处理缺失] → design 明确为扩展；核心是 tools/call 单次结果回流。

## Migration Plan

1. `AgentConfig`/`ConfigLoader` 加 `mcp.servers`。
2. 新增 `McpClient`（initialize/listTools/callTool）。
3. 新增 `McpTool`（implements Tool）。
4. `ToolRegistry` 加 `registerMcpTools`（或独立 registry）。
5. `AgentLoopFactory.buildTools` 接入。
6. 新增 `McpClientTest`/`McpToolRegistryTest`（WireMock）。
7. `mvn -pl agent-core verify` 全绿。

## Open Questions

- MCP 传输头（`Accept: application/json, text/event-stream`、`MCP-Protocol-Version`）：本 change 用 JSON-RPC POST 单次响应（SSE 通知通道预留），是否需要建立 SSE GET 连接——v0.4 预留接口，实际通知消费留 v0.2。
