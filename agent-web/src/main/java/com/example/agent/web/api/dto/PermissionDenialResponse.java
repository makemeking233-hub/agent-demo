package com.example.agent.web.api.dto;

import com.example.agent.permission.FsDenialKind;
import com.example.agent.permission.SandboxMode;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 权限拒绝响应 DTO（rewrite-permission-mode-dsh T7.3 引入）。
 *
 * <p>用于工具调用被 sandbox policy 拒绝时返回结构化 denial，前端据 {@code kind} / {@code currentMode} /
 * {@code suggestedMode} / {@code marker} 渲染 sandbox marker + 升级按钮。
 */
public record PermissionDenialResponse(
        @JsonProperty("kind") FsDenialKind kind,
        @JsonProperty("current_mode") SandboxMode currentMode,
        @JsonProperty("suggested_mode") SandboxMode suggestedMode,
        @JsonProperty("marker") String marker) {
}