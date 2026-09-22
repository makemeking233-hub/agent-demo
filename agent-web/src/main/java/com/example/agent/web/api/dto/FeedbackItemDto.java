package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单条 feedback 项的 wire 形式（add-message-feedback F2.2）。
 *
 * @param rating  {@code "up"} / {@code "down"}
 * @param version per-item CAS 版本号（创建 = 1，每次成功 PUT 或 DELETE 后的下一 PUT 自增）
 */
public record FeedbackItemDto(
        @JsonProperty("rating") String rating,
        @JsonProperty("version") long version,
        @JsonProperty("updated_at") long updatedAt) {}