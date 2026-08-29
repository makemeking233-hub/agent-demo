package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /api/chat/send body (spec §Requirement: Chat Send → `{"content","session_id"}`). */
public record SendRequest(
        @JsonProperty("content") String content,
        @JsonProperty("session_id") String sessionId) {}
