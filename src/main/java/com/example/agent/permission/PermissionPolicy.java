package com.example.agent.permission;

import java.util.List;

/**
 * 权限策略：全局规则（详见 design.md §6.5 + §9）。
 *
 * <p>v0.1 字段：
 * <ul>
 *   <li>默认策略：read/write/shell 是否需要 ask</li>
 *   <li>敏感路径模式：glob 列表，命中后强制 ask（即使 defaultRead=allow）</li>
 *   <li>破坏性命令：合并 ShellAdapter.defaultDenylist</li>
 * </ul>
 *
 * @param defaultRead read 工具默认策略（true=allow, false=ask）
 * @param defaultWrite write 工具默认策略
 * @param defaultShell shell 工具默认策略
 * @param sensitivePathPatterns 敏感路径 Ant glob 列表
 */
public record PermissionPolicy(
    boolean defaultRead,
    boolean defaultWrite,
    boolean defaultShell,
    List<String> sensitivePathPatterns
) {
    /**
     * v0.1 默认策略：read=allow, write=ask, shell=ask。
     * @return 默认 {@link PermissionPolicy}
     */
    public static PermissionPolicy defaults() {
        return new PermissionPolicy(
            true,   // default read: allow
            false,  // default write: ask
            false,  // default shell: ask
            List.of("**/.ssh/**", "**/.env*", "**/*credentials*", "**/*.pem")
        );
    }
}