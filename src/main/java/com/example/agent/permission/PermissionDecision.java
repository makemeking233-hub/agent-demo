package com.example.agent.permission;

/**
 * 权限裁决（sealed）：Allow / Ask / Deny。
 *
 * <p>第 2 步（Tool 级）返回 Deny 是终态——不进入第 3 步交互确认（详见 design.md §6.5 Q9 决议）。
 */
public record PermissionDecision(Behavior behavior) {
    public enum Behavior { ALLOW, ASK, DENY }

    public static PermissionDecision allow() { return new PermissionDecision(Behavior.ALLOW); }
    public static PermissionDecision ask() { return new PermissionDecision(Behavior.ASK); }
    public static PermissionDecision deny() { return new PermissionDecision(Behavior.DENY); }
}