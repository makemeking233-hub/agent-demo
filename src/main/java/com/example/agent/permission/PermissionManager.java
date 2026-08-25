package com.example.agent.permission;

/**
 * M2 占位：Task 3.8 升级为完整实现（含 Policy + 敏感路径 + 交互确认）。
 */
public class PermissionManager {
    public PermissionDecision decide(String toolName, Object input) {
        return PermissionDecision.ask();
    }
}