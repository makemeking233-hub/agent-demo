package com.example.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.tools.Tool.ToolContext;
import com.example.agent.tools.websearch.Source;
import com.example.agent.tools.websearch.WebSearchProvider;
import com.example.agent.tools.websearch.WebSearchResult;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class WebSearchToolTest {

    private final ToolContext ctx = new ToolContext(Path.of("/tmp"), null, () -> false);

    private WebSearchTool toolWith(WebSearchProvider provider) {
        return new WebSearchTool(provider, 5, 60000);
    }

    @Test
    void exposesProtocolMetadata() {
        WebSearchTool tool = toolWith((q, max, t) -> new WebSearchResult(List.of(), false));
        assertEquals("web_search", tool.name());
        assertFalse(tool.description().isBlank());
        assertTrue(tool.isReadOnly(new WebSearchTool.Input("q", null)));
        assertEquals(ToolCategory.READ, tool.category());
        assertEquals(PermissionDecision.allow(), tool.checkPermissions(null, null));
    }

    @Test
    void inputSchemaHasRequiredQueryAndOptionalMaxResults() {
        WebSearchTool tool = toolWith((q, max, t) -> new WebSearchResult(List.of(), false));
        Map<String, Object> schema = tool.inputSchema();
        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("query"));
        assertTrue(props.containsKey("maxResults"));
        assertEquals(List.of("query"), schema.get("required"));
    }

    @Test
    void parseArgumentsExtractsQueryAndMaxResults() {
        WebSearchTool tool = toolWith((q, max, t) -> new WebSearchResult(List.of(), false));
        WebSearchTool.Input in = tool.parseArguments("{\"query\":\"天气\",\"maxResults\":3}");
        assertEquals("天气", in.query());
        assertEquals(3, in.maxResults());
    }

    @Test
    void parseArgumentsDefaultsMaxResultsWhenAbsent() {
        WebSearchTool tool = toolWith((q, max, t) -> new WebSearchResult(List.of(), false));
        WebSearchTool.Input in = tool.parseArguments("{\"query\":\"天气\"}");
        assertEquals("天气", in.query());
        assertNull(in.maxResults());
    }

    @Test
    void parseArgumentsRejectsBlankQuery() {
        WebSearchTool tool = toolWith((q, max, t) -> new WebSearchResult(List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> tool.parseArguments("{\"query\":\"  \"}"));
    }

    @Test
    void executeRendersSources() {
        WebSearchProvider provider =
                (q, max, t) ->
                        new WebSearchResult(
                                List.of(
                                        new Source(
                                                "https://a.example",
                                                "标题 A",
                                                "摘要 A",
                                                "2024-01-01")),
                                false);
        WebSearchTool tool = toolWith(provider);
        ToolResult<String> r = tool.execute(tool.parseArguments("{\"query\":\"天气\"}"), ctx).block();
        assertFalse(r.isError());
        assertTrue(r.output().contains("标题 A"));
        assertTrue(r.output().contains("https://a.example"));
        assertTrue(r.output().contains("摘要 A"));
        assertTrue(r.output().contains("2024-01-01"));
    }

    @Test
    void executeReturnsErrorOnProviderFailure() {
        WebSearchProvider provider =
                (q, max, t) -> {
                    throw new IllegalStateException("缺少 API key");
                };
        WebSearchTool tool = toolWith(provider);
        ToolResult<String> r = tool.execute(tool.parseArguments("{\"query\":\"天气\"}"), ctx).block();
        assertTrue(r.isError());
        assertTrue(r.toModelContent().contains("缺少 API key"));
    }

    @Test
    void executeReturnsErrorOnBlankQuery() {
        WebSearchTool tool = toolWith((q, max, t) -> new WebSearchResult(List.of(), false));
        ToolResult<String> r = tool.execute(new WebSearchTool.Input("  ", null), ctx).block();
        assertTrue(r.isError());
    }
}
