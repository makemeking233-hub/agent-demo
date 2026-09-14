package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Provider 分组条目（add-provider-catalog-abstract）。
 *
 * <p>前端两层菜单的外层:列出 provider 名称(如 "DeepSeek" / "OpenAI" / "Anthropic"),
 * 每个 provider 嵌套该 provider 下的所有 {@link ModelEntry}。
 *
 * <p>对齐 dsh web {@code ModelProviderGroup} 数据形态。
 */
public record ProviderGroup(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("models") List<ModelEntry> models) {
}