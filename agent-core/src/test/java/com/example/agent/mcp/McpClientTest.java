package com.example.agent.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class McpClientTest {
    private WireMockServer wm;
    private McpClient client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        client = McpClient.create("http://localhost:" + wm.port(), "test-server");
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private void stubInitializeOk() {
        wm.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingJsonPath("$.method", equalTo("initialize")))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withHeader("mcp-session-id", "sess-123")
                                        .withBody(
                                                "{\"jsonrpc\":\"2.0\",\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}}")));
    }

    @Test
    void initializeReturnsTrueOnOk() {
        stubInitializeOk();
        assertTrue(client.initialize());
    }

    @Test
    void initializeReturnsFalseOnServerError() {
        wm.stubFor(
                post(urlEqualTo("/"))
                        .willReturn(aResponse().withStatus(500)));
        assertFalse(client.initialize());
    }

    @Test
    void listToolsReturnsDescriptors() {
        stubInitializeOk();
        client.initialize();
        wm.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingJsonPath("$.method", equalTo("tools/list")))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[{\"name\":\"calc\",\"description\":\"计算\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}}")));
        List<McpClient.ToolDescriptor> tools = client.listTools();
        assertEquals(1, tools.size());
        assertEquals("calc", tools.get(0).name());
        assertEquals("计算", tools.get(0).description());
    }

    @Test
    void callToolReturnsTextContent() {
        stubInitializeOk();
        client.initialize();
        wm.stubFor(
                post(urlEqualTo("/"))
                        .withRequestBody(matchingJsonPath("$.method", equalTo("tools/call")))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"jsonrpc\":\"2.0\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"42\"}]}}")));
        McpClient.CallResult result = client.callTool("calc", Map.of("a", 1, "b", 2));
        assertFalse(result.isError());
        assertEquals("42", result.text());
    }
}
