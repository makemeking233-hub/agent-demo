package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * {@code GET /api/chat/models} 响应（add-provider-catalog-abstract）。
 *
 * <p>v0.1 (add-models-dropdown-v0) 是平铺 {@code models[]};v0.2 (本 change) 升级为嵌套
 * {@code providers[]},对齐 dsh web {@code ModelProviderGroup[]} 形态。前端两层菜单
 * (外层 provider / 内层 model) 直接消费此结构。
 *
 * <p>BREAKING：响应字段名从 {@code models} 改为 {@code providers},每项从 {@link Model}
 * (含 {@code reasoningEfforts: List<String>}) 升级为 {@link ProviderGroup} +
 * {@link ModelEntry} + {@link ReasoningEffort} 三层嵌套。
 */
public record ModelsResponse(@JsonProperty("providers") List<ProviderGroup> providers) {
}