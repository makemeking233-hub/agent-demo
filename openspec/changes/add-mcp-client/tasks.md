# Tasks: MCP 客户端集成

## 1. 配置：AgentConfig 加 mcp.servers

- [x] 1.1 新增 `AgentConfig.Mcp`/`McpServer(name, url)` record + defaults 默认空列表
- [x] 1.2 `ConfigLoader` 解析 `mcp.servers[].name/url`

## 2. MCP 客户端：McpClient

- [x] 2.1 新增 `McpClient`（WebClient JSON-RPC POST）：`initialize()` 握手、`listTools()`、`callTool(name,args)`；保存 mcp-session-id
- [x] 2.2 握手/调用失败时优雅降级（WARN，不抛未捕获异常）

## 3. MCP 工具：McpTool + 融合

- [x] 3.1 新增 `McpTool implements Tool<String,String>`：name=MCP工具名，description=MCP描述，inputSchema 简化映射，execute 转发 callTool 并聚合 content
- [x] 3.2 `ToolRegistry.registerMcpTools(registry, List<McpClient>)`：逐 client listTools 成功后注册（失败跳过）

## 4. 装配：AgentLoopFactory

- [x] 4.1 `AgentLoopFactory.buildTools` 从 `cfg.mcp().servers()` 构造 McpClient 并融合工具

## 5. 测试与验证

- [x] 5.1 新增 `McpClientTest`（WireMock 模拟 initialize/tools/list/callTool）与 `McpToolRegistryTest`/`McpToolTest`
- [x] 5.2 `mvn -pl agent-core verify` 全绿（230 测试，jacoco 门禁达标）
- [x] 5.3 commit + push（中文 Conventional Commits）
