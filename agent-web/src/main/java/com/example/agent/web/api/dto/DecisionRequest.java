package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /api/chat/decision/{id} body (spec §Requirement: permission_request 决策 → `{"permission_id","decision"}`). */
public record DecisionRequest(
        @JsonProperty("permission_id") String permissionId,
        @JsonProperty("decision") String decision) {}
