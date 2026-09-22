package com.example.agent.permission;

import java.nio.file.Path;
import java.util.List;

/**
 * 单次工具调用的完整 sandbox policy（rewrite-permission-mode-dsh T1.2 引入）。
 *
 * <p>由 {@link SandboxPolicyService#resolve} 解析返回；工具执行时按 (mode, capability, target)
 * 推导默认裁决（见 {@link SandboxPolicyService#defaultDecision}）。
 *
 * <p>字段语义：
 *
 * <ul>
 *   <li>{@code mode} — 当前会话权限模式（plan / ask / danger-full / dontAsk）
 *   <li>{@code workspaceRoot} — 会话工作目录（来自 {@code ToolContext.workingDirectory()}）
 *   <li>{@code tempRoots} — 由 {@link WritableRoots} 派生的可写根（mode=DONT_ASK 时非空；其余空）
 *   <li>{@code capability} — 调用所属能力（FS / BASH / TERMINAL）
 * </ul>
 *
 * @param mode          权限模式
 * @param workspaceRoot 工作目录
 * @param tempRoots     可写临时根列表（已 canonicalize 去重）
 * @param capability    能力分类
 */
public record SandboxPolicy(
        SandboxMode mode,
        Path workspaceRoot,
        List<Path> tempRoots,
        Capability capability) {

    /**
     * 紧凑构造：校验 capability 必填，tempRoots 不可变。
     */
    public SandboxPolicy {
        if (mode == null) {
            throw new IllegalArgumentException("SandboxPolicy.mode 不可空");
        }
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("SandboxPolicy.workspaceRoot 不可空");
        }
        if (capability == null) {
            throw new IllegalArgumentException("SandboxPolicy.capability 不可空");
        }
        if (tempRoots == null) {
            throw new IllegalArgumentException("SandboxPolicy.tempRoots 不可空（允许空列表）");
        }
        tempRoots = List.copyOf(tempRoots);
    }
}
