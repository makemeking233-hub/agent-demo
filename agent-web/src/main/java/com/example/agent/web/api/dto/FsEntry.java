package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /api/fs/list 单个条目 (add-workspace-picker-modal).
 *
 * @param name  条目名（不含父路径）
 * @param path  条目绝对路径
 * @param isDir 是否为目录
 * @param size  文件字节数；目录固定为 0
 * @param mtime 最后修改时间（epoch ms）
 */
public record FsEntry(
        @JsonProperty("name") String name,
        @JsonProperty("path") String path,
        @JsonProperty("isDir") boolean isDir,
        @JsonProperty("size") long size,
        @JsonProperty("mtime") long mtime) {}
