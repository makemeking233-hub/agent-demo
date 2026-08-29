package com.example.agent.permission;

import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Ant-style glob to regex matcher. Used by PermissionManager for sensitive path detection.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>double-star matches any chars across segments (including /)
 *   <li>single-star matches any chars within one segment (excluding /)
 *   <li>double-star-slash can match empty path so .env matches the env pattern
 *   <li>Case-sensitive (POSIX path semantics)
 * </ul>
 */
public class PermissionPathMatcher {

    /**
     * Placeholder to avoid double-star-slash replacement conflict with double-star
     */
    private static final String DOUBLE_SLASH_PLACEHOLDER = "::DOUBLESLASH::";

    /**
     * 原始 glob 模式列表（不可变副本）
     */
    private final List<String> patterns;

    /**
     * 编译后的正则数组（与 patterns 一一对应）
     */
    private final Pattern[] compiled;

    /**
     * 构造路径匹配器。
     *
     * @param patterns Ant glob 模式列表
     */
    public PermissionPathMatcher(List<String> patterns) {
        this.patterns = List.copyOf(patterns);
        this.compiled = new Pattern[patterns.size()];
        for (int i = 0; i < patterns.size(); i++) {
            compiled[i] = Pattern.compile(toRegex(patterns.get(i)));
        }
    }

    /**
     * Returns true if the path matches at least one glob pattern.
     *
     * @param path candidate path (Windows backslashes normalized to forward)
     * @return true on first match
     */
    public boolean matches(String path) {
        if (path == null) return false;
        String normalized = Paths.get(path).toString().replace('\\', '/');
        for (Pattern p : compiled) {
            if (p.matcher(normalized).matches()) return true;
        }
        return false;
    }

    /**
     * Convert Ant glob to regex (exposed for testability).
     *
     * <p>Order: backslash escape, dot escape, double-star-slash placeholder, double-star to any,
     * single-star to non-slash, then placeholder to optional-path-prefix.
     */
    public static String toRegex(String glob) {
        return "^"
                + glob.replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("**/", DOUBLE_SLASH_PLACEHOLDER)
                .replace("**", ".*")
                .replace("*", "[^/]*")
                .replace(DOUBLE_SLASH_PLACEHOLDER, "(?:.*/)?")
                + "$";
    }

    /**
     * @return 原始 glob 模式列表（只读）
     */
    public List<String> patterns() {
        return patterns;
    }
}
