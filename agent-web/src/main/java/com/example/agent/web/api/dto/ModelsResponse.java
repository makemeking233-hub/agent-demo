package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * GET /api/chat/models 响应（add-reasoning-thinking-streaming）。
 */
public record ModelsResponse(@JsonProperty("models") List<Model> models) {
    /**
     * 模型条目（add-models-dropdown-v0 新增 {@code reasoningEfforts} 字段）。
     *
     * @param reasoningEfforts 该模型支持的思考强度档位（如 {@code ["low","medium","high"]}）；
     *     {@code supportsReasoning=false} 时为空数组。
     */
    public record Model(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("supportsReasoning") boolean supportsReasoning,
            @JsonProperty("reasoningEfforts") List<String> reasoningEfforts) {}
}