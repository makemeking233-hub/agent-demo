package com.example.agent.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Cover the friendlyError() classifier so the 401/404/429/network hints don't regress. */
class ChatCommandFriendlyErrorTest {

  @Test
  void unauthorizedMapsToApiKeyHint() {
    Throwable e = WebClientResponseException.create(
        401, "Unauthorized", null, null, null);
    String out = ChatCommand.friendlyError(e);
    assertTrue(out.contains("401"), "should mention status code: " + out);
    assertTrue(out.contains("DEEPSEEK_API_KEY"),
        "should mention DEEPSEEK_API_KEY: " + out);
  }

  @Test
  void notFoundMapsToBaseUrlHint() {
    Throwable e = WebClientResponseException.create(
        404, "Not Found", null, null, null);
    String out = ChatCommand.friendlyError(e);
    assertTrue(out.contains("404"), "should mention status code: " + out);
    assertTrue(out.contains("baseUrl") || out.contains("model"),
        "should mention baseUrl/model: " + out);
  }

  @Test
  void rateLimitMapsToWaitHint() {
    Throwable e = WebClientResponseException.create(
        429, "Too Many Requests", null, null, null);
    String out = ChatCommand.friendlyError(e);
    assertTrue(out.contains("429"), "should mention status code: " + out);
  }

  @Test
  void connectExceptionMapsToNetworkHint() {
    // 用含 "connect" 子串的异常触发网络分支
    java.net.SocketTimeoutException e = new java.net.SocketTimeoutException("connect timed out");
    String out = ChatCommand.friendlyError(e);
    assertTrue(out.contains("网络") || out.toLowerCase().contains("network"),
        "should mention network: " + out);
  }

  @Test
  void unclassifiedReturnsRawMessage() {
    String out = ChatCommand.friendlyError(new RuntimeException("something weird"));
    assertTrue(out.contains("something weird"), "fallback returns raw: " + out);
  }
}
