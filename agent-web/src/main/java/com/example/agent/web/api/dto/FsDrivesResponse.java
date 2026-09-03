package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** GET /api/fs/drives 响应 (add-workspace-picker-modal；Windows 返回盘符列表，Linux/macOS 返回空). */
public record FsDrivesResponse(@JsonProperty("drives") List<FsDrive> drives) {

    /** 单个盘符（Windows 用，如 {@code C:}/{@code D:}）.*/
    public record FsDrive(
            @JsonProperty("name") String name,
            @JsonProperty("path") String path) {}
}
