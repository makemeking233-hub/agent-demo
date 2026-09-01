package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /api/chat/{stream_id}/permission body (spec §Requirement: 权限模式实时切换 → `{"mode":"..."}`)。
 *
 * <p>{@code mode} 为三档之一：{@code read_only} / {@code workspace_write} / {@code full_access}。
 */
public record PermissionModeRequest(@JsonProperty("mode") String mode) {}
