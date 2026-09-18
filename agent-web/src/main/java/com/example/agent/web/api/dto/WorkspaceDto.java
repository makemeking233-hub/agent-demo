package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /api/workspaces 列表项（align-dsh-workspace v2）。
 *
 * <p>v2 字段：{@code name / id / title / dir / updatedAt / status} + 兼容字段
 * {@code sessionCount / lastActiveAt}（v1 保留，向后兼容）。
 */
public record WorkspaceDto(
        @JsonProperty("name") String name,
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("dir") String dir,
        @JsonProperty("sessionCount") int sessionCount,
        @JsonProperty("lastActiveAt") long lastActiveAt,
        @JsonProperty("updatedAt") long updatedAt,
        @JsonProperty("status") String status) {
    /** v1 兼容入口（status OK）。 */
    public static WorkspaceDto v1(String name, String dir, int sessionCount, long lastActiveAt) {
        return new WorkspaceDto(name, name, name, dir, sessionCount, lastActiveAt, 0L, "ok");
    }
}