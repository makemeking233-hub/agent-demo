package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /api/sessions 列表项（add-session-switch change；add-session-management 加 time）。
 *
 * @param id 会话 id
 * @param title 标题
 * @param preview 预览（首条消息）
 * @param workspace 归属工作区
 * @param time 最后活动时间（毫秒）
 * @param bucket 时间分档（仅归档列表填充，见 {@code SessionAgeBucket}；普通列表为 {@code null}）
 */
public record SessionSummaryDto(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("preview") String preview,
        @JsonProperty("workspace") String workspace,
        @JsonProperty("time") long time,
        @JsonProperty("bucket") String bucket) {

    /**
     * 兼容既有调用（普通会话列表不需要分档）。
     *
     * @param id 会话 id
     * @param title 标题
     * @param preview 预览
     * @param workspace 归属工作区
     * @param time 最后活动时间
     */
    public SessionSummaryDto(String id, String title, String preview, String workspace, long time) {
        this(id, title, preview, workspace, time, null);
    }
}
