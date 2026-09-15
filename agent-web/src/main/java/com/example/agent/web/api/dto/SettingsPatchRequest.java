package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** PATCH /api/settings/{path} 请求体 */
public record SettingsPatchRequest(
        @JsonProperty("value") Object value,
        @JsonProperty("revision") Long revision) {}
