package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /api/workspaces 列表项（add-workspaces-and-rename）。 */
public record WorkspaceDto(
        @JsonProperty("name") String name,
        @JsonProperty("dir") String dir,
        @JsonProperty("sessionCount") int sessionCount,
        @JsonProperty("lastActiveAt") long lastActiveAt) {}
