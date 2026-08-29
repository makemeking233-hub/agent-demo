package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /api/chat/send response (spec §Requirement: Chat Send → `{"stream_id","session_id","model"}`). */
public record SendResponse(
        @JsonProperty("stream_id") String streamId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("model") String model) {}
