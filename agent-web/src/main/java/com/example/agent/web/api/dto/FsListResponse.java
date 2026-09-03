package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * GET /api/fs/list 响应 (add-workspace-picker-modal).
 *
 * @param path    当前绝对路径
 * @param parent  父目录绝对路径；根目录时为 null
 * @param entries 当前目录下条目（默认不含隐藏文件）
 */
public record FsListResponse(
        @JsonProperty("path") String path,
        @JsonProperty("parent") String parent,
        @JsonProperty("entries") List<FsEntry> entries) {}
