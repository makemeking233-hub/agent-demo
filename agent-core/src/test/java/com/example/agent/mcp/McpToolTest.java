package com.example.agent.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.tools.Tool.ToolContext;
import com.example.agent.tools.ToolResult;
import com.github.tomakehurst.wiremock.WireMockServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

class McpToolTest {
    private WireMockServer wm;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private McpClient client(String name) {
        return McpClient.create("http://localhost:" + wm.port(), name);
    }

    private void stubCallToolResult(String method, String body) {
        wm.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingJsonPath("$.method", equalTo(method)))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(body)));
    }

    @Test
    void executeForwardsToMcpAndReturnsText() {
        stubCallToolResult("tools/call", "{\"jsonrpc\":\"2.0\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}}");
        McpClient client = client("srv");
        McpTool tool = new McpTool(client, new McpClient.ToolDescriptor("echo", "回显", Map.of()));
        ToolResult<String> r = tool.execute("{}", new ToolContext(java.nio.file.Path.of("."), null, () -> false)).block();
        assertEquals("hello", r.toModelContent());
    }

    @Test
    void executeReturnsErrorOnMcpError() {
        stubCallToolResult("tools/call", "{\"jsonrpc\":\"2.0\",\"result\":{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"boom\"}]}}");
        McpClient client = client("srv");
        McpTool tool = new McpTool(client, new McpClient.ToolDescriptor("echo", "回显", Map.of()));
        ToolResult<String> r = tool.execute("{}", new ToolContext(java.nio.file.Path.of("."), null, () -> false)).block();
        assertTrue(r.isError());
    }
}
