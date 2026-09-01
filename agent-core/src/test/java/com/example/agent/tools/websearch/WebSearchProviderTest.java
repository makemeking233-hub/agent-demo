package com.example.agent.tools.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class WebSearchProviderTest {

    @Test
    void providerIsInterfaceWithSearchMethod() throws Exception {
        assertTrue(WebSearchProvider.class.isInterface());
        Method m = WebSearchProvider.class.getMethod("search", String.class, int.class, Duration.class);
        assertEquals(WebSearchResult.class, m.getReturnType());
    }

    @Test
    void sourceRecordExposesFields() {
        Source s = new Source("https://a.example", "标题", "摘要", "2024-01-01");
        assertEquals("https://a.example", s.url());
        assertEquals("标题", s.title());
        assertEquals("摘要", s.snippet());
        assertEquals("2024-01-01", s.publishedAt());
    }

    @Test
    void webSearchResultWrapsSourcesAndTruncatedFlag() {
        Source s = new Source("https://a.example", "t", "sn", "d");
        WebSearchResult r = new WebSearchResult(List.of(s), true);
        assertEquals(1, r.sources().size());
        assertEquals("https://a.example", r.sources().get(0).url());
        assertTrue(r.truncated());
    }

    @Test
    void providerContractCanBeImplementedByLambda() {
        WebSearchProvider p = (query, max, timeout) -> new WebSearchResult(List.of(), false);
        WebSearchResult r = p.search("q", 5, Duration.ofSeconds(1));
        assertEquals(0, r.sources().size());
        assertFalse(r.truncated());
    }
}
