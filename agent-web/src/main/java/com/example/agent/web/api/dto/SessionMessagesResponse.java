package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** GET /api/sessions/{id}/messages 响应（v0.3 会话重进恢复用）。 */
public record SessionMessagesResponse(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("messages") List<SessionMessageDto> messages) {}
