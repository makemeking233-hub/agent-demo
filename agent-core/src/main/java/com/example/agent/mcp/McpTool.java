package com.example.agent.mcp;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolCategory;
import com.example.agent.tools.ToolResult;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具（add-mcp-client change）：把一个 MCP server 的工具暴露为 agent 可调用的 {@link Tool}。
 *
 * <p>工具名 = MCP 工具名，描述 = MCP 工具描述；模型调用时 {@link #execute} 转发
 * {@link McpClient#callTool} 并把结果回流。只读视角（是否真正只读取决于 MCP server）。
 */
public class McpTool implements Tool<String, String> {
    private final McpClient client;
    private final McpClient.ToolDescriptor desc;

    /**
     * 构造 MCP 工具。
     *
     * @param client MCP server 客户端
     * @param desc   工具描述
     */
    public McpTool(McpClient client, McpClient.ToolDescriptor desc) {
        this.client = client;
        this.desc = desc;
    }

    @Override
    public String name() {
        return desc.name();
    }

    @Override
    public String description() {
        return "MCP 工具。" + desc.description();
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> s = desc.inputSchema();
        return s != null && !s.isEmpty() ? s : Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public boolean isReadOnly(String input) {
        return false;
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.OTHER;
    }

    @Override
    public PermissionDecision checkPermissions(String input, Tool.ToolContext ctx) {
        return PermissionDecision.ask();
    }

    @Override
    public String renderUse(String input) {
        return "MCP(" + desc.name() + ")";
    }

    @Override
    public String renderResult(String output) {
        return output;
    }

    @Override
    public String parseArguments(String argumentsJson) {
        return argumentsJson;
    }

    @Override
    public Mono<ToolResult<String>> execute(String input, Tool.ToolContext ctx) {
        Map<String, Object> args = parseArgs(input);
        McpClient.CallResult result = client.callTool(desc.name(), args);
        if (result.isError()) {
            return Mono.just(ToolResult.error(result.text()));
        }
        return Mono.just(ToolResult.ok(result.text()));
    }

    /** 解析模型传入的参数 JSON 为 Map（失败回退空 Map，交由 MCP server 报错）。 */
    private Map<String, Object> parseArgs(String input) {
        if (input == null || input.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(input, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
