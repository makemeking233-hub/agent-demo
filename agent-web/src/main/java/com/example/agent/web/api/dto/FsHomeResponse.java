package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /api/fs/home 响应 (add-workspace-picker-modal).
 *
 * @param path     家目录绝对路径
 * @param platform "windows" / "linux" / "mac"，供前端"此电脑"层判断是否展示盘符列表
 */
public record FsHomeResponse(
        @JsonProperty("path") String path,
        @JsonProperty("platform") String platform) {}
