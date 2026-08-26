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

  /** Placeholder to avoid double-star-slash replacement conflict with double-star */
  private static final String DOUBLE_SLASH_PLACEHOLDER = "::DOUBLESLASH::";

  private final List<String> patterns;
  private final Pattern[] compiled;

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

  public List<String> patterns() {
    return patterns;
  }
}
