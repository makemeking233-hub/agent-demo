package com.example.agent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Streamable HTTP 客户端（add-mcp-client change）。
 *
 * <p>用 WebFlux WebClient + JSON-RPC 2.0（POST）实现对单个 MCP server 的协议交互：
 * {@link #initialize()}（握手）、{@link #listTools()}（工具发现）、{@link #callTool(String, Map)}
 * （工具调用）。握手失败/调用失败时优雅降级（WARN / 错误结果），不阻断主流程。
 *
 * <p>依赖现有 WebFlux WebClient + Jackson，不引入官方 MCP SDK。
 */
public class McpClient {
    private static final Logger log = LoggerFactory.getLogger(McpClient.class);

    /** JSON 序列化器 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** MCP 协议版本（2025-03-26） */
    private static final String PROTOCOL_VERSION = "2025-03-26";

    private final WebClient client;
    private final String name;

    private volatile String sessionId;

    /**
     * 构造客户端。
     *
     * @param builder WebClient builder（已配置 baseUrl）
     * @param name    server 名（日志/标识）
     */
    public McpClient(WebClient.Builder builder, String name) {
        this.client = builder.build();
        this.name = name;
    }

    /**
     * 从配置构造 client（便捷）。
     *
     * @param url  MCP server endpoint URL
     * @param name server 名
     * @return {@link McpClient}
     */
    public static McpClient create(String url, String name) {
        WebClient.Builder b = WebClient.builder().baseUrl(url);
        return new McpClient(b, name);
    }

    /** @return server 名 */
    public String name() {
        return name;
    }

    /**
     * initialize 握手：交换协议版本与能力，保存可能的 mcp-session-id。
     *
     * @return 成功返回该 server 的 {@link ToolDescriptor} 空列表（仅建立会话）；失败记录 WARN 并返回 false
     */
    public boolean initialize() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put(
                "capabilities",
                Map.of("tools", Map.of("listChanged", false)));
        params.put("clientInfo", Map.of("name", "agent-demo", "version", "0.1.0"));
        try {
            JsonNode resp = post("initialize", params, true);
            if (resp == null) return false;
            String sid = resp.path("_mcpSessionId").asText(null);
            if (sid != null && !sid.isBlank()) sessionId = sid;
            return true;
        } catch (Exception e) {
            log.warn("[mcp:{}] initialize 失败: {}", name, e.toString());
            return false;
        }
    }

    /**
     * tools/list：列出 server 提供的工具（name/description/inputSchema）。
     *
     * @return 工具描述列表；失败时空列表
     */
    public List<ToolDescriptor> listTools() {
        try {
            JsonNode resp = post("tools/list", null, false);
            List<ToolDescriptor> tools = new ArrayList<>();
            if (resp != null && resp.has("tools")) {
                for (JsonNode t : resp.get("tools")) {
                    String toolName = t.path("name").asText(null);
                    if (toolName == null) continue;
                    tools.add(
                            new ToolDescriptor(
                                    toolName,
                                    t.path("description").asText(null),
                                    t.path("inputSchema").isObject()
                                            ? JSON.convertValue(t.path("inputSchema"), new TypeReference<Map<String, Object>>() {})
                                            : Map.of()));
                }
            }
            return tools;
        } catch (Exception e) {
            log.warn("[mcp:{}] tools/list 失败: {}", name, e.toString());
            return List.of();
        }
    }

    /**
     * tools/call：调用一个工具。
     *
     * @param toolName   工具名
     * @param arguments  工具参数
     * @return 工具结果（content 聚合文本；调用失败/错误时返回错误文本）
     */
    public CallResult callTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments == null ? Map.of() : arguments);
        try {
            JsonNode resp = post("tools/call", params, false);
            if (resp == null) return CallResult.error("tools/call 无响应");
            boolean isError = resp.path("isError").asBoolean(false);
            StringBuilder content = new StringBuilder();
            if (resp.has("content")) {
                for (JsonNode block : resp.get("content")) {
                    String type = block.path("type").asText("text");
                    if ("text".equals(type)) {
                        content.append(block.path("text").asText(""));
                    } else if ("image".equals(type)) {
                        content.append("[image block]");
                    } else if ("resource".equals(type)) {
                        content.append("[resource block]");
                    }
                }
            }
            return isError ? CallResult.error(content.toString())
                    : CallResult.ok(content.toString());
        } catch (Exception e) {
            log.warn("[mcp:{}] tools/call {} 失败: {}", name, toolName, e.toString());
            return CallResult.error("MCP 工具调用失败: " + e.getMessage());
        }
    }

    /** 发送 JSON-RPC POST，返回 result 节点。 */
    private JsonNode post(String method, Object params, boolean captureSession) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", System.nanoTime());
        body.put("method", method);
        if (params != null) body.put("params", params);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, "application/json, text/event-stream");
        if (sessionId != null && !sessionId.isBlank()) headers.set("mcp-session-id", sessionId);

        JsonNode resp =
                client.post()
                        .uri("")
                        .headers(h -> h.putAll(headers))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .onErrorResume(e -> {
                            log.warn("[mcp:{}] POST {} 失败: {}", name, method, e.toString());
                            return Mono.empty();
                        })
                        .block();
        if (resp == null) return null;
        return resp.path("result");
    }

    /** MCP 工具描述。 */
    public record ToolDescriptor(
            String name, String description, Map<String, Object> inputSchema) {}

    /** tools/call 结果（text content 聚合）。 */
    public record CallResult(boolean isError, String text) {
        public static CallResult ok(String text) {
            return new CallResult(false, text);
        }

        public static CallResult error(String text) {
            return new CallResult(true, text);
        }
    }
}
