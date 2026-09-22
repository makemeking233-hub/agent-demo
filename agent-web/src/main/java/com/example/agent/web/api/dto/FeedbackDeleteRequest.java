package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DELETE 请求体：取消某条消息的反馈（add-message-feedback F2.2）。
 *
 * @param ifVersion 期望的现有 version（{@code null} 表示「必须不存在」）
 */
public record FeedbackDeleteRequest(
        @JsonProperty("ifVersion") Long ifVersion) {}