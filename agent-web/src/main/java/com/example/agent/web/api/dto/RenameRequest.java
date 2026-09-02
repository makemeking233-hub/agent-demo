package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /api/sessions/{id}/rename body (add-workspaces-and-rename → `{"title":"..."}`)。 */
public record RenameRequest(@JsonProperty("title") String title) {}
