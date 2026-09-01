package com.example.agent.tools.websearch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class DeepSeekWebSearchProviderTest {
    private WireMockServer wm;
    private DeepSeekWebSearchProvider provider;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        provider =
                new DeepSeekWebSearchProvider("sk-test", "http://localhost:" + wm.port() + "/anthropic/v1");
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private void stubMessages(String body) {
        wm.stubFor(
                post(urlEqualTo("/anthropic/v1/messages"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(body)));
    }

    @Test
    void parsesWebSearchToolResultBlocks() {
        stubMessages(
                "{\"content\":["
                        + "{\"type\":\"web_search_tool_result\",\"content\":["
                        + "{\"type\":\"web_search_result\",\"url\":\"https://a.example\",\"title\":\"标题 A\",\"page_age\":\"2024-01-01\"},"
                        + "{\"type\":\"web_search_result\",\"url\":\"https://b.example\",\"title\":\"标题 B\",\"page_age\":\"2024-02-02\"}"
                        + "]},"
                        + "{\"type\":\"text\",\"text\":\"...\",\"citations\":["
                        + "{\"url\":\"https://a.example\",\"cited_text\":\"摘要 A\"},"
                        + "{\"url\":\"https://b.example\",\"cited_text\":\"摘要 B\"}"
                        + "]}"
                        + "]}");
        WebSearchResult r = provider.search("测试", 5, Duration.ofSeconds(5));
        assertEquals(2, r.sources().size());
        Source a = r.sources().get(0);
        assertEquals("https://a.example", a.url());
        assertEquals("标题 A", a.title());
        assertEquals("摘要 A", a.snippet());
        assertEquals("2024-01-01", a.publishedAt());
        assertFalse(r.truncated());
    }

    @Test
    void dedupesByUrl() {
        stubMessages(
                "{\"content\":["
                        + "{\"type\":\"web_search_tool_result\",\"content\":["
                        + "{\"type\":\"web_search_result\",\"url\":\"https://a.example\",\"title\":\"标题 A\",\"page_age\":\"2024-01-01\"},"
                        + "{\"type\":\"web_search_result\",\"url\":\"https://a.example\",\"title\":\"标题 A 重复\",\"page_age\":\"2024-01-01\"}"
                        + "]},"
                        + "{\"type\":\"text\",\"text\":\"...\",\"citations\":["
                        + "{\"url\":\"https://a.example\",\"cited_text\":\"摘要 A\"}"
                        + "]}"
                        + "]}");
        WebSearchResult r = provider.search("测试", 5, Duration.ofSeconds(5));
        assertEquals(1, r.sources().size());
        assertEquals("https://a.example", r.sources().get(0).url());
    }

    @Test
    void throwsWhenNoWebSearchToolResultBlock() {
        stubMessages("{\"content\":[{\"type\":\"text\",\"text\":\"未触发搜索\"}]}");
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> provider.search("测试", 5, Duration.ofSeconds(5)));
        assertTrue(e.getMessage().contains("web_search_tool_result"));
    }

    @Test
    void throwsWhenApiKeyMissing() {
        DeepSeekWebSearchProvider p = new DeepSeekWebSearchProvider(null);
        assertThrows(
                IllegalStateException.class, () -> p.search("测试", 5, Duration.ofSeconds(5)));
    }
}
