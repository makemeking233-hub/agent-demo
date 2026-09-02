package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /api/chat/send body (spec §Requirement: Chat Send → `{"content","session_id"}`)。
 *
 * <p>{@code permission_mode} 为该会话初始权限基准（add-permission-mode-dropdown；可选，缺省
 * {@code read_only}）；{@code workspace} 为会话归属工作区（add-workspaces-and-rename；可选，缺省
 * 默认工作区 {@code agent-demo}）。
 */
public record SendRequest(
        @JsonProperty("content") String content,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("permission_mode") String permissionMode,
        @JsonProperty("workspace") String workspace) {}
