package com.example.agent.web.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * 企业微信 API 客户端（add-wecom-channel task 3.1）。
 *
 * <p>支持：
 *
 * <ul>
 *   <li>{@link #getAccessToken()} — 内存缓存 + 过期前 200s 自动重取
 *   <li>{@link #sendMarkdown(String, String)} — 推 markdown 消息；
 *       限频（45009）抛 {@link RetryableWecomException} 由上游退避
 *   <li>{@link #uploadMedia(byte[], String)} — v1 桩（不支持，抛
 *       {@link UnsupportedOperationException}）
 * </ul>
 *
 * <p>线程安全：{@link #cachedToken} 用 {@code synchronized} 保护；
 * HttpClient / ObjectMapper 线程安全可复用。
 */
public class WecomClient implements WecomMessageSender {

    private static final String DEFAULT_BASE_URL = "https://qyapi.weixin.qq.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    /** access_token 提前 200s 过期，避免边界竞态。 */
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 200;

    private final WecomConfigProperties props;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json;

    private volatile CachedToken cachedToken;

    /** 默认构造：用于 Spring 注入。 */
    public WecomClient(WecomConfigProperties props) {
        this(props, DEFAULT_BASE_URL,
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build(),
                new ObjectMapper());
    }

    /** 包级构造：用于测试注入（WireMock baseUrl）。 */
    WecomClient(WecomConfigProperties props, String baseUrl, HttpClient http, ObjectMapper json) {
        this.props = props;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
        this.json = json;
    }

    /**
     * 获取有效 access_token；过期或未缓存则调 gettoken API 刷新。
     *
     * @throws RuntimeException 网络错 / 企业微信返回非零 errcode
     */
    public synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedToken.expiresAt)) {
            return cachedToken.token;
        }
        try {
            String url = baseUrl + "/cgi-bin/gettoken?corpid=" + urlEncode(props.corpId())
                    + "&corpsecret=" + urlEncode(props.secret());
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(READ_TIMEOUT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = json.readTree(resp.body());
            int errcode = body.path("errcode").asInt(0);
            if (errcode != 0) {
                throw new RuntimeException("wecom gettoken 失败：errcode=" + errcode
                        + ", errmsg=" + body.path("errmsg").asText());
            }
            String token = body.path("access_token").asText();
            int expiresIn = body.path("expires_in").asInt(7200);
            long effectiveSeconds = Math.max(60, expiresIn - TOKEN_EXPIRY_BUFFER_SECONDS);
            cachedToken = new CachedToken(token, Instant.now().plusSeconds(effectiveSeconds));
            return token;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("wecom gettoken 网络错", e);
        }
    }

    /**
     * 推送 markdown 消息给单个 userId。
     *
     * <p>限频（45009）抛 {@link RetryableWecomException} 由调用方退避重试。
     * 其他非零 errcode 抛 {@link RuntimeException}。
     *
     * @param userId  企业微信用户 ID（corp 内唯一）
     * @param content markdown 内容（&lt;=4096 字节）
     * @return 微信返回的 msgid（用于日志关联）
     */
    public String sendMarkdown(String userId, String content) {
        if (content == null) content = "";
        String token = getAccessToken();
        ObjectNode payload = json.createObjectNode();
        payload.put("touser", userId);
        payload.put("msgtype", "markdown");
        payload.put("agentid", Integer.parseInt(props.agentId()));
        ObjectNode md = payload.putObject("markdown");
        md.put("content", content);

        try {
            String url = baseUrl + "/cgi-bin/message/send?access_token=" + urlEncode(token);
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(READ_TIMEOUT)
                            .header("Content-Type", "application/json; charset=utf-8")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    json.writeValueAsString(payload), StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = json.readTree(resp.body());
            int errcode = body.path("errcode").asInt(0);
            if (errcode == 45009) {
                throw new RetryableWecomException(
                        "wecom sendMarkdown 限频：errcode=" + errcode
                                + ", errmsg=" + body.path("errmsg").asText());
            }
            if (errcode != 0) {
                throw new RuntimeException("wecom sendMarkdown 失败：errcode=" + errcode
                        + ", errmsg=" + body.path("errmsg").asText());
            }
            return body.path("msgid").asText("");
        } catch (RetryableWecomException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("wecom sendMarkdown 网络错", e);
        }
    }

    /**
     * 上传媒体到企业微信（v1 不实现，桩）。
     *
     * @throws UnsupportedOperationException 留待后续 PR
     */
    public String uploadMedia(byte[] data, String filename) {
        throw new UnsupportedOperationException(
                "wecom uploadMedia 在 add-wecom-channel v1 不实现（v2 引入群消息 / 图片时再补）");
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** 缓存的 access_token + 过期时刻。 */
    static final class CachedToken {
        final String token;
        final Instant expiresAt;

        CachedToken(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
