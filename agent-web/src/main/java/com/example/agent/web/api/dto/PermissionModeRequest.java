package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /api/chat/{stream_id}/permission body (rewrite-permission-mode-dsh T7.1 扩展)。
 *
 * <p>{@code mode} 接受 4 档 dsh 命名（{@code plan} / {@code ask} / {@code danger-full} / {@code dontAsk}）
 * 与旧 3 档（{@code read_only} / {@code workspace_write} / {@code full_access}）；旧值自动 normalize。
 *
 * <p>{@code escalate} 为 true 时表示临时升级：mode 立即生效但 turn 结束后自动恢复。响应体含
 * {@code effective_mode} 字段告知客户端实际生效的 mode（normalise 后）。
 */
public record PermissionModeRequest(
        @JsonProperty("mode") String mode,
        @JsonProperty("escalate") Boolean escalate) {
}
