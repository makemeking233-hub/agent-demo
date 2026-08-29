package com.example.agent.permission;

/**
 * 权限交互确认器：当裁决结果为 {@code ASK} 时，由宿主（REPL）决定是否放行。
 *
 * <p>v0.1 实现：{@code ChatCommand} 用 stdin 交互实现；测试用 {@link #allowAll()} / {@link #denyAll()} 桩。
 */
@FunctionalInterface
public interface PermissionConfirmer {
    /**
     * 询问用户是否放行。
     *
     * @param prompt 展示给用户的提示（工具名 + 命令/路径描述）
     * @return true=允许执行；false=拒绝
     */
    boolean confirm(String prompt);

    /** 始终放行（测试 / --auto-approve-write） */
    static PermissionConfirmer allowAll() {
        return prompt -> true;
    }

    /** 始终拒绝（fail-closed 兜底） */
    static PermissionConfirmer denyAll() {
        return prompt -> false;
    }
}
