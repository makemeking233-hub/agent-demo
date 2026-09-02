package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /api/workspaces body (add-workspaces-and-rename → `{"name","dir"}`)。 */
public record CreateWorkspaceRequest(
        @JsonProperty("name") String name,
        @JsonProperty("dir") String dir) {}
