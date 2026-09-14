package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 推理强度档位条目（add-provider-catalog-abstract）。
 *
 * <p>对齐 dsh web {@code ModelReasoningEffort} 数据形态:每个推理模型可选档位的
 * {@code {id, name, description?}} 三元组。前端用于两层菜单里的"模型 → effort 联动"。
 *
 * @param id          唯一标识,提交回后端的值（如 {@code "low"}）
 * @param name        UI 显示名（如 {@code "Low"} / "中"）
 * @param description 描述（可选,空时 Jackson 不输出字段）
 */
public record ReasoningEffort(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("description") String description) {
}