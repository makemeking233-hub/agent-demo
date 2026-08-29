package com.example.agent.tools.file;

/**
 * 文件路径类工具输入的 sealed interface（v0.1：Read/Write/Edit/Ls）。
 *
 * <p>用于 {@link com.example.agent.permission.PermissionManager#decide(String, Object,
 * com.example.agent.tools.Tool.ToolContext)} 抽取路径字段， 消除 instanceof 硬编码。
 *
 * <p>新增路径类工具时：让 Input {@code implements ToolInput}，并实现 {@link #path()} 即可被 {@code
 * PermissionManager} 自动识别。
 */
public sealed interface ToolInput
        permits ReadFileTool.Input, WriteFileTool.Input, EditFileTool.Input, LsTool.Input {
    /**
     * 工具输入中的相对路径（{@code null} 表示无路径语义）
     */
    String path();
}
