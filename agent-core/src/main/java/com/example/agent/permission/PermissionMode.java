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
     * 按 wire 值解析（大小写不敏感；接受新旧两套命名）。
     *
     * <p>接受：
     *
     * <ul>
     *   <li>旧 v0.1 3 档：{@code read_only} / {@code workspace_write} / {@code full_access}
     *   <li>新 dsh 4 档：{@code plan} / {@code ask} / {@code danger-full} / {@code dontAsk}
     * </ul>
     *
     * <p>旧值自动 normalize 到 3 档映射（{@code read_only → READ_ONLY}, {@code workspace_write → WORKSPACE_WRITE},
     * {@code full_access → FULL_ACCESS}）；新值按对应关系 normalize。
     *
     * @param value wire 值
     * @return 对应 PermissionMode
     * @throws IllegalArgumentException 值为空或不在新旧命名之列
     */
    public static PermissionMode from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("未知权限模式（空值；支持 read_only/workspace_write/full_access 或 plan/ask/danger-full/dontAsk）");
        }
        String key = value.toLowerCase(Locale.ROOT);
        // 1) 先查旧 3 档
        PermissionMode mode = BY_VALUE.get(key);
        if (mode != null) return mode;
        // 2) 查新 4 档 (dsh 命名) 并 normalize
        SandboxMode sandbox = SandboxMode.from(value);
        return fromSandboxMode(sandbox);
    }

    /**
     * 从 SandboxMode 4 档映射到 PermissionMode 3 档（rewrite-permission-mode-dsh T7 兼容桥）。
     *
     * <p>DONT_ASK 没有旧 3 档对应值；映射到 FULL_ACCESS（最宽松）作为降级。
     *
     * @param sandbox 新 SandboxMode
     * @return 对应 PermissionMode
     */
    public static PermissionMode fromSandboxMode(SandboxMode sandbox) {
        if (sandbox == null) return DEFAULT;
        return switch (sandbox) {
            case PLAN -> READ_ONLY;
            case ASK -> WORKSPACE_WRITE;
            case DANGER_FULL -> FULL_ACCESS;
            case DONT_ASK -> FULL_ACCESS; // 无旧档位对应; 降级 FULL_ACCESS
        };
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

    /**
     * 映射到新 SandboxMode 4 档（rewrite-permission-mode-dsh T5.2 兼容桥）。
     *
     * <p>READ_ONLY → PLAN; WORKSPACE_WRITE → ASK; FULL_ACCESS → DANGER_FULL.
     *
     * @return 对应 SandboxMode
     */
    public SandboxMode toSandboxMode() {
        return switch (this) {
            case READ_ONLY -> SandboxMode.PLAN;
            case WORKSPACE_WRITE -> SandboxMode.ASK;
            case FULL_ACCESS -> SandboxMode.DANGER_FULL;
        };
    }
}
