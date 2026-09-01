package com.example.agent.permission;

import com.example.agent.tools.ToolCategory;
import java.util.Locale;
import java.util.Map;

/**
 * 权限模式（add-permission-mode-dropdown）：决定 {@link PermissionManager} 的全局权限基准。
 *
 * <p>三档：
 *
 * <ul>
 *   <li>{@link #READ_ONLY}：只读工具放行；写 / 执行 / 其它 询问
 *   <li>{@link #WORKSPACE_WRITE}：只读放行；写工具按工作区边界（内允许 / 外询问）；shell / 其它 询问
 *   <li>{@link #FULL_ACCESS}：全部放行（含敏感路径，仅工具级 DENY 兜底）
 * </ul>
 */
public enum PermissionMode {
    /** 只读（缺省；与 v0.1 现状 read=allow / write=ask / shell=ask 行为一致） */
    READ_ONLY,
    /** 工作区写（只读 + 工作目录内写放行） */
    WORKSPACE_WRITE,
    /** 全权限（无弹窗，唯一例外是工具级 DENY） */
    FULL_ACCESS;

    /** 缺省模式（新会话未指定时）。 */
    public static final PermissionMode DEFAULT = READ_ONLY;

    private static final Map<String, PermissionMode> BY_VALUE =
            Map.of(
                    "read_only", READ_ONLY,
                    "workspace_write", WORKSPACE_WRITE,
                    "full_access", FULL_ACCESS);

    /**
     * 按 wire 值解析（大小写不敏感）。
     *
     * @param value wire 值（read_only / workspace_write / full_access）
     * @return 对应模式
     * @throws IllegalArgumentException 值为空或不在三档之列
     */
    public static PermissionMode from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("未知权限模式（空值）");
        }
        PermissionMode mode = BY_VALUE.get(value.toLowerCase(Locale.ROOT));
        if (mode == null) {
            throw new IllegalArgumentException(
                    "未知权限模式: " + value + "（支持 read_only / workspace_write / full_access）");
        }
        return mode;
    }

    /** wire 值（小写下划线），用于 HTTP 载荷持久化/展示。 */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * 按工具类别给默认裁决（不含路径边界；工作区边界由 {@link PermissionManager} 补）。
     *
     * @param category 工具语义分类
     * @param withinWorkspace 目标路径是否在会话工作目录内（仅 WRITE 有意义）
     * @return 默认裁决（allow / ask）
     */
    public PermissionDecision defaultDecision(ToolCategory category, boolean withinWorkspace) {
        return switch (this) {
            case FULL_ACCESS -> PermissionDecision.allow();
            case WORKSPACE_WRITE ->
                    switch (category) {
                        case READ -> PermissionDecision.allow();
                        case WRITE -> withinWorkspace
                                ? PermissionDecision.allow()
                                : PermissionDecision.ask();
                        case SHELL, OTHER -> PermissionDecision.ask();
                    };
            case READ_ONLY ->
                    switch (category) {
                        case READ -> PermissionDecision.allow();
                        case WRITE, SHELL, OTHER -> PermissionDecision.ask();
                    };
        };
    }
}
