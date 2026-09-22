package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PUT 请求体：创建或更新某条消息的反馈（add-message-feedback F2.2）。
 *
 * @param rating    {@code "up"} 或 {@code "down"}
 * @param ifVersion 期望的现有 version（{@code null} 表示「必须不存在」，即首次创建）
 */
public record FeedbackPutRequest(
        @JsonProperty("rating") String rating,
        @JsonProperty("ifVersion") Long ifVersion) {}