package com.example.agent.permission;

/**
 * 权限裁决（sealed）：Allow / Ask / Deny。
 *
 * <p>第 2 步（Tool 级）返回 Deny 是终态——不进入第 3 步交互确认（详见 design.md §6.5 Q9 决议）。
 */
public record PermissionDecision(Behavior behavior) {
    /**
     * 行为枚举
     */
    public enum Behavior {
        /**
         * 放行（直接执行工具）
         */
        ALLOW,
        /**
         * 询问用户（弹确认）
         */
        ASK,
        /**
         * 拒绝（终态，不可覆盖）
         */
        DENY
    }

    /**
     * 放行（直接执行）
     */
    public static PermissionDecision allow() {
        return new PermissionDecision(Behavior.ALLOW);
    }

    /**
     * 询问用户
     */
    public static PermissionDecision ask() {
        return new PermissionDecision(Behavior.ASK);
    }

    /**
     * 拒绝（终态，不可覆盖）
     */
    public static PermissionDecision deny() {
        return new PermissionDecision(Behavior.DENY);
    }
}
