package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /api/sessions 列表项（add-session-switch change）。 */
public record SessionSummaryDto(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("preview") String preview,
        @JsonProperty("workspace") String workspace) {}
