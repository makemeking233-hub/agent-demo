package com.example.agent.permission;

import com.example.agent.tools.Tool;

/**
 * 权限裁决管理器（详见 design.md §6.5）。
 *
 * <p>裁决顺序：
 *
 * <ol>
 *   <li>敏感路径匹配 → 强制 ask（即使 defaultRead=allow）
 *   <li>按 tool 类型查默认策略（read/write/shell）
 * </ol>
 *
 * <p>Q9 决议：Tool.checkPermissions 返回 deny 是终态，由本类调用 tool 层方法处理； 本类当前 v0.1 仅做 1+2 两步。
 *
 * <p>路径匹配逻辑抽到 {@link PermissionPathMatcher}，本类专注策略。
 */
public class PermissionManager {
  private final PermissionPolicy policy;
  private final PermissionPathMatcher pathMatcher;

  public PermissionManager() {
    this(PermissionPolicy.defaults());
  }

  public PermissionManager(PermissionPolicy policy) {
    this.policy = policy;
    this.pathMatcher = new PermissionPathMatcher(policy.sensitivePathPatterns());
  }

  /** 主裁决方法 */
  public PermissionDecision decide(String toolName, Object input, Tool.ToolContext ctx) {
    String path = extractPath(input);
    if (path != null && pathMatcher.matches(path)) {
      return PermissionDecision.ask();
    }
    return switch (toolName) {
      case "ReadFile", "Ls" -> policy.defaultRead()
          ? PermissionDecision.allow()
          : PermissionDecision.ask();
      case "WriteFile", "EditFile" -> policy.defaultWrite()
          ? PermissionDecision.allow()
          : PermissionDecision.ask();
      case "Shell" -> policy.defaultShell() ? PermissionDecision.allow() : PermissionDecision.ask();
      default -> PermissionDecision.ask();
    };
  }

  /** 兼容 stub 调用（M2 AgentLoop 用） */
  public PermissionDecision decide(String toolName, Object input) {
    return decide(toolName, input, null);
  }

  private String extractPath(Object input) {
    if (input instanceof Tool.ToolContext) return null;
    try {
      if (input instanceof com.example.agent.tools.ReadFileTool.Input i) return i.path();
      if (input instanceof com.example.agent.tools.WriteFileTool.Input i) return i.path();
      if (input instanceof com.example.agent.tools.EditFileTool.Input i) return i.path();
      if (input instanceof com.example.agent.tools.LsTool.Input i) return i.path();
    } catch (Exception ignored) {
      /* instanceof 不会抛，防御性 */
    }
    return null;
  }
}
