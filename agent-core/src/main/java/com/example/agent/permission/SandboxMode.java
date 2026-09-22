package com.example.agent.permission;

import java.util.Locale;
import java.util.Map;

/**
 * 沙箱权限模式（dsh 4 档；rewrite-permission-mode-dsh 引入）。
 *
 * <p>对齐 dsh web 的 SandboxMode：{@code plan}（只读 + 计划工具）/ {@code ask}（只读 + 写/Shell 询问）/
 * {@code danger-full}（解除文件 fence，工具级 DENY 兜底）/ {@code dontAsk}（自动 allow 无 UI 弹窗）。
 *
 * <p>原 3 档 {@link PermissionMode}（READ_ONLY / WORKSPACE_WRITE / FULL_ACCESS）由
 * {@link #legacyMigrationMap()} 提供到本枚举的迁移映射，archive 阶段删除。
 *
 * @see <a href="https://github.com/deepseek-ai/dsh/blob/main/packages/sandbox/sandbox/src/index.ts">dsh SandboxMode</a>
 */
public enum SandboxMode {
    /** 只读 + 计划工具（plan 模式禁止任何写入）。 */
    PLAN,
    /** 只读放行；写工具 workspace 内 allow / 外 ask；shell / other 询问。 */
    ASK,
    /** 解除文件 fence；工具级 DENY 仍兜底；敏感路径放行。 */
    DANGER_FULL,
    /** 不询问；自动 allow；按 writableRoots 派生根（workspace + /tmp + tmpdir）。 */
    DONT_ASK;

    /** 默认模式（新会话未指定时）。 */
    public static final SandboxMode DEFAULT = PLAN;

    private static final Map<String, SandboxMode> BY_WIRE_VALUE =
            Map.of(
                    "plan", PLAN,
                    "ask", ASK,
                    "danger-full", DANGER_FULL,
                    "dontask", DONT_ASK);

    /**
     * 按 wire 值解析（大小写不敏感；非法值抛错）。
     *
     * @param value wire 值（plan / ask / danger-full / dontAsk）
     * @return 对应模式
     * @throws IllegalArgumentException 值为空或不在四档之列
     */
    public static SandboxMode from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("未知 SandboxMode（空值；支持 plan/ask/danger-full/dontAsk）");
        }
        // wire value 是不区分大小写的（前端可能传 DontAsk / DONOTASK 等）
        String key = value.toLowerCase(Locale.ROOT);
        SandboxMode mode = BY_WIRE_VALUE.get(key);
        if (mode == null) {
            throw new IllegalArgumentException(
                    "未知 SandboxMode: " + value + "（支持 plan/ask/danger-full/dontAsk）");
        }
        return mode;
    }

    /** wire 值（dsh 命名规范）。 */
    public String wireValue() {
        return switch (this) {
            case PLAN -> "plan";
            case ASK -> "ask";
            case DANGER_FULL -> "danger-full";
            case DONT_ASK -> "dontAsk";
        };
    }

    /**
     * 旧 3 档 PermissionMode → SandboxMode 迁移表（archive 阶段删除）。
     *
     * @return 旧 wire value → 新 SandboxMode
     */
    public static Map<String, SandboxMode> legacyMigrationMap() {
        return Map.of(
                "read_only", PLAN,
                "workspace_write", ASK,
                "full_access", DANGER_FULL);
    }
}
