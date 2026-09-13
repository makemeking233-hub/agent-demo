package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * GET /api/chat/models 响应（add-reasoning-thinking-streaming）。
 */
public record ModelsResponse(@JsonProperty("models") List<Model> models) {
    public record Model(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("supportsReasoning") boolean supportsReasoning) {}
}