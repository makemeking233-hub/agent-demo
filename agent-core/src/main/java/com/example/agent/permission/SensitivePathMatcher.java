package com.example.agent.permission;

import java.util.List;

/**
 * 敏感路径匹配器（rewrite-permission-mode-dsh T5.1 引入；从 PermissionManager 抽出）。
 *
 * <p>职责单一：判断目标路径是否命中敏感 pattern（如 {@code **\/.ssh/**}）。
 * 不与 mode / category / toolName 耦合；可单独注入到 PermissionManager。
 *
 * <p>默认 pattern 列表：
 *
 * <ul>
 *   <li>{@code **\/.ssh/**}
 *   <li>{@code **\/.env*}
 *   <li>{@code **\/*.pem}
 *   <li>{@code **\/*credentials*}
 * </ul>
 *
 * <p>用户可在 {@code ~/.agent-demo/settings.yaml} 的 {@code general.permission.sensitivePatterns} 段
 * 扩展（追加，不替换默认）。
 */
public final class SensitivePathMatcher {

    /** 默认敏感 path patterns（与 v0.1 PermissionPathMatcher 一致） */
    public static final List<String> DEFAULT_PATTERNS =
            List.of("**/.ssh/**", "**/.env*", "**/*.pem", "**/*credentials*");

    private final PermissionPathMatcher delegate;

    public SensitivePathMatcher(List<String> patterns) {
        this.delegate = new PermissionPathMatcher(patterns);
    }

    /** 默认 patterns 实例 */
    public static SensitivePathMatcher defaults() {
        return new SensitivePathMatcher(DEFAULT_PATTERNS);
    }

    /**
     * 路径是否命中任一敏感 pattern。
     *
     * @param path 待匹配路径（{@code null} 返回 false）
     * @return true 表示命中
     */
    public boolean matches(String path) {
        return delegate.matches(path);
    }

    /** 委托原始 patterns（只读） */
    public List<String> patterns() {
        return delegate.patterns();
    }
}