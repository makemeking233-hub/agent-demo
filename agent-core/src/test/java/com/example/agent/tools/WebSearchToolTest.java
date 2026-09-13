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

import reactor.test.StepVerifier;

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

    /**
     * 验证 fix-web-search-blocking-call：provider 模拟阻塞 IO 时，execute 不会把阻塞调用留在
     * 订阅线程上，应调度到 boundedElastic 弹性线程池；调用方能在合理时间内拿到结果，不抛
     * {@code block() are not supported} 错误。
     */
    @Test
    void executeSchedulesBlockingCallOffSubscriberThread() {
        WebSearchProvider provider =
                (q, max, t) -> {
                    // 模拟 provider.search 内的同步阻塞 IO（如 WebClient.mono.block）
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new WebSearchResult(
                            List.of(new Source("https://a.example", "A", "摘要", "2024-01-01")),
                            false);
                };
        WebSearchTool tool = toolWith(provider);
        StepVerifier.create(tool.execute(tool.parseArguments("{\"query\":\"x\"}"), ctx))
                .assertNext(r -> {
                    assertFalse(r.isError());
                    assertTrue(r.output().contains("https://a.example"));
                })
                .verifyComplete();
    }

    /**
     * 验证 fix-web-search-blocking-call：provider 抛错时，错误结果透传原始异常消息，且不再
     * 附加 "请检查搜索 provider 的 API key 配置与网络连接" 这条误导性 fallback 文案。
     */
    @Test
    void executePassesThroughProviderExceptionMessageWithoutFallback() {
        String originalMessage = "DeepSeek 搜索缺少 API key：请设置环境变量 DEEPSEEK_API_KEY";
        WebSearchProvider provider =
                (q, max, t) -> {
                    throw new IllegalStateException(originalMessage);
                };
        WebSearchTool tool = toolWith(provider);
        ToolResult<String> r = tool.execute(tool.parseArguments("{\"query\":\"x\"}"), ctx).block();
        assertTrue(r.isError());
        // 透传原始消息
        assertTrue(r.toModelContent().contains(originalMessage), () -> "应包含原始异常消息，实际=" + r.toModelContent());
        // 不再附加误导性 fallback 文案
        assertFalse(r.toModelContent().contains("请检查搜索 provider 的 API key"),
                () -> "不应再含 fallback 误导文案，实际=" + r.toModelContent());
    }
}

