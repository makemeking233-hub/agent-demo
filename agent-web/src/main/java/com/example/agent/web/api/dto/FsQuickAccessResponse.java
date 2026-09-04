package com.example.agent.web.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * GET /api/fs/quick-access 响应 (polish-workspace-picker-dsh-style).
 *
 * <p>返回当前用户家目录下的快速访问目录列表（Home + 探测到的 Desktop / Documents / Downloads）。
 * 不存在的目录跳过；越界路径跳过。
 *
 * @param items 快速访问条目列表（始终至少含 Home）
 */
public record FsQuickAccessResponse(@JsonProperty("items") List<FsQuickAccessItem> items) {

    /**
     * 单个快速访问条目。
     *
     * @param name 显示名（"Home" / "Desktop" / "Documents" / "Downloads"）
     * @param path 绝对路径（已通过 {@code HomePathGuard} 校验）
     */
    public record FsQuickAccessItem(
            @JsonProperty("name") String name, @JsonProperty("path") String path) {}
}
