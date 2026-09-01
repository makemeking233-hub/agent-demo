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
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

class TavilyWebSearchProviderTest {
    private WireMockServer wm;
    private TavilyWebSearchProvider provider;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(0);
        wm.start();
        provider =
                new TavilyWebSearchProvider("tvly-test", "http://localhost:" + wm.port() + "/search");
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void mapsResultsToSources() {
        wm.stubFor(
                post(urlEqualTo("/search"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"results\":["
                                                        + "{\"title\":\"标题 1\",\"url\":\"https://a.example\",\"content\":\"内容 1\",\"score\":0.9},"
                                                        + "{\"title\":\"标题 2\",\"url\":\"https://b.example\",\"content\":\"内容 2\",\"score\":0.8}"
                                                        + "]}")));
        WebSearchResult r = provider.search("测试", 5, Duration.ofSeconds(5));
        assertEquals(2, r.sources().size());
        Source a = r.sources().get(0);
        assertEquals("标题 1", a.title());
        assertEquals("https://a.example", a.url());
        assertEquals("内容 1", a.snippet());
        assertEquals("", a.publishedAt());
        assertFalse(r.truncated());
    }

    @Test
    void truncatesToMaxResults() {
        wm.stubFor(
                post(urlEqualTo("/search"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"results\":["
                                                        + "{\"title\":\"t1\",\"url\":\"https://a\",\"content\":\"c1\"},"
                                                        + "{\"title\":\"t2\",\"url\":\"https://b\",\"content\":\"c2\"},"
                                                        + "{\"title\":\"t3\",\"url\":\"https://c\",\"content\":\"c3\"}"
                                                        + "]}")));
        WebSearchResult r = provider.search("测试", 2, Duration.ofSeconds(5));
        assertEquals(2, r.sources().size());
        assertTrue(r.truncated());
    }

    @Test
    void throwsWhenApiKeyMissing() {
        TavilyWebSearchProvider p = new TavilyWebSearchProvider(null);
        assertThrows(
                IllegalStateException.class, () -> p.search("测试", 5, Duration.ofSeconds(5)));
    }

    @Test
    void throwsOnHttpFailure() {
        wm.stubFor(post(urlEqualTo("/search")).willReturn(aResponse().withStatus(500)));
        assertThrows(
                WebClientResponseException.class,
                () -> provider.search("测试", 5, Duration.ofSeconds(5)));
    }
}
