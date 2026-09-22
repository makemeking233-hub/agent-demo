package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * GET 响应：某会话的全部 feedback 项（add-message-feedback F2.2）。
 *
 * <p>{@code items} 的 key 是消息 uuid，value 是 feedback 项；前端一次性拉取后渲染整会话。
 */
public record FeedbackResponse(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("items") Map<String, FeedbackItemDto> items) {}