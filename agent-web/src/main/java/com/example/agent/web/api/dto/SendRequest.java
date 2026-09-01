package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /api/chat/send body (spec §Requirement: Chat Send → `{"content","session_id"}`)。
 *
 * <p>{@code permission_mode} 为该会话初始权限基准（add-permission-mode-dropdown；可选，缺省
 * {@code read_only}）。
 */
public record SendRequest(
        @JsonProperty("content") String content,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("permission_mode") String permissionMode) {}
