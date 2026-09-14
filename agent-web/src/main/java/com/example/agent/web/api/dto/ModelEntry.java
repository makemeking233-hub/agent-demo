package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 单个 LLM 模型条目（add-provider-catalog-abstract）。
 *
 * <p>从属于 {@link ProviderGroup#models} 列表。{@code supportsReasoning=true} 时
 * {@code reasoningEfforts} 必有至少一个档位;否则必须为空数组(ProviderCatalogService
 * 启动校验会拒绝配置冲突)。
 */
public record ModelEntry(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("supportsReasoning") boolean supportsReasoning,
        @JsonProperty("reasoningEfforts") List<ReasoningEffort> reasoningEfforts) {
}