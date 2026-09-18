package com.example.agent.permission;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件系统拒绝原因分类（rewrite-permission-mode-dsh T1.3 引入）。
 *
 * <p>用于 {@code PathResult.denied(kind, currentMode, suggestedMode, message)} 携带拒绝原因分类，
 * 前端据此渲染不同 marker / 不同升级按钮；session log 据此审计哪种拒绝最多。
 *
 * <p>{@link #suggestedMode()} 返回推荐 escalate 升级 mode（{@code null} 表示不建议升级）。
 */
public enum FsDenialKind {
    /** 读取路径在工作区 / 临时目录之外。 */
    READ_OUT_OF_BOUNDS(null),
    /** 写入路径在工作区 / 临时目录之外。 */
    WRITE_OUT_OF_BOUNDS(SandboxMode.DANGER_FULL),
    /** 命中敏感路径 pattern（**\/.ssh/** 等）。 */
    SENSITIVE_PATH(null),
    /** 工具级 DENY（{@code Tool.checkPermissions} 返回 deny，与 mode 无关）。 */
    TOOL_DENY(null),
    /** 当前 mode 本身拒绝该操作（如 PLAN 模式禁止写入）。 */
    MODE_REJECTED(SandboxMode.ASK);

    private final SandboxMode suggestedMode;

    FsDenialKind(SandboxMode suggestedMode) {
        this.suggestedMode = suggestedMode;
    }

    /**
     * 推荐 escalate 升级 mode；{@code null} 表示不建议升级（升级后仍受其他层约束）。
     *
     * @return 推荐 mode 或 null
     */
    public SandboxMode suggestedMode() {
        return suggestedMode;
    }

    /**
     * 全表（测试 / 文档用）。Map.of 不支持 null value，用 LinkedHashMap 保证插入序。
     */
    public static Map<FsDenialKind, SandboxMode> allSuggestedModes() {
        Map<FsDenialKind, SandboxMode> m = new LinkedHashMap<>();
        for (FsDenialKind k : values()) {
            m.put(k, k.suggestedMode);
        }
        return Collections.unmodifiableMap(m);
    }
}
