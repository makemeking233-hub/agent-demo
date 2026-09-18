package com.example.agent.web.wecom;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * WecomClient 单元测试（add-wecom-channel task 3.2）。
 *
 * <p>用 WireMock 模拟企业微信 gettoken / send API；通过 {@link WecomClient} 包级
 * 构造函数注入 {@code baseUrl} 指向 WireMock。
 */
class WecomClientTest {

    private WireMockServer wm;
    private WecomClient client;
    private WecomConfigProperties props;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(wireMockConfig().dynamicPort());
        wm.start();

        props = new WecomConfigProperties(
                true,
                "wxcorp123",
                "1000002",
                "secret_abc",
                "token_xyz",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFG",
                "https://example.com",
                new WecomConfigProperties.Reply(3000L, 500, 4000, 30000L));

        client = new WecomClient(props, wm.baseUrl(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void getAccessTokenFetchesAndCaches() {
        wm.stubFor(get(urlPathEqualTo("/cgi-bin/gettoken"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errcode\":0,\"access_token\":\"T-1\",\"expires_in\":7200}")));

        String t1 = client.getAccessToken();
        String t2 = client.getAccessToken();
        assertEquals("T-1", t1);
        assertEquals("T-1", t2);
        // 缓存生效：只调 1 次
        wm.verify(1, getRequestedFor(urlPathEqualTo("/cgi-bin/gettoken")));
    }

    @Test
    void getAccessTokenRefreshesAfterExpiry() throws Exception {
        wm.stubFor(get(urlPathEqualTo("/cgi-bin/gettoken"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"errcode\":0,\"access_token\":\"T-1\",\"expires_in\":7200}")));

        String t1 = client.getAccessToken();
        assertEquals("T-1", t1);

        // 强制过期缓存
        expireCachedToken(client);

        wm.resetRequests();
        wm.stubFor(get(urlPathEqualTo("/cgi-bin/gettoken"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"errcode\":0,\"access_token\":\"T-2\",\"expires_in\":7200}")));

        String t2 = client.getAccessToken();
        assertNotEquals(t1, t2);
        assertEquals("T-2", t2);
        wm.verify(1, getRequestedFor(urlPathEqualTo("/cgi-bin/gettoken")));
    }

    @Test
    void getAccessTokenThrowsOnErrorResponse() {
        wm.stubFor(get(urlPathEqualTo("/cgi-bin/gettoken"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"errcode\":40013,\"errmsg\":\"invalid corpsecret\"}")));
        RuntimeException e = assertThrows(RuntimeException.class, () -> client.getAccessToken());
        assertTrue(e.getMessage().contains("gettoken") || e.getMessage().contains("40013"));
    }

    @Test
    void sendMarkdownPostsCorrectBodyAndReturnsMsgId() {
        wm.stubFor(get(urlPathEqualTo("/cgi-bin/gettoken"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"errcode\":0,\"access_token\":\"T-OK\",\"expires_in\":7200}")));
        wm.stubFor(post(urlMatching("/cgi-bin/message/send.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errcode\":0,\"errmsg\":\"ok\",\"msgid\":\"MID-1\"}")));

        String msgId = client.sendMarkdown("user123", "今天天气晴");
        assertEquals("MID-1", msgId);

        // 校验请求体字段
        wm.verify(postRequestedFor(urlMatching("/cgi-bin/message/send.*"))
                .withRequestBody(containing("\"msgtype\":\"markdown\""))
                .withRequestBody(containing("\"touser\":\"user123\"")));
    }

    @Test
    void sendMarkdownThrowsRetryableOnRateLimit() {
        wm.stubFor(get(urlPathEqualTo("/cgi-bin/gettoken"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"errcode\":0,\"access_token\":\"T-OK\",\"expires_in\":7200}")));
        wm.stubFor(post(urlMatching("/cgi-bin/message/send.*"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"errcode\":45009,\"errmsg\":\"freq limit\"}")));

        RetryableWecomException e = assertThrows(RetryableWecomException.class,
                () -> client.sendMarkdown("user123", "hi"));
        assertTrue(e.getMessage().contains("45009"));
    }

    @Test
    void sendMarkdownThrowsOnPermanentError() {
        wm.stubFor(get(urlPathEqualTo("/cgi-bin/gettoken"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"errcode\":0,\"access_token\":\"T-OK\",\"expires_in\":7200}")));
        wm.stubFor(post(urlMatching("/cgi-bin/message/send.*"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"errcode\":40031,\"errmsg\":\"invalid userid\"}")));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> client.sendMarkdown("bogus", "hi"));
        assertTrue(e.getMessage().contains("40031"));
    }

    private static void expireCachedToken(WecomClient client) throws Exception {
        java.lang.reflect.Field f = WecomClient.class.getDeclaredField("cachedToken");
        f.setAccessible(true);
        Object current = f.get(client);
        if (current != null) {
            java.lang.reflect.Field expField = current.getClass().getDeclaredField("expiresAt");
            expField.setAccessible(true);
            expField.set(current, Instant.now().minusSeconds(60));
        }
    }
}
