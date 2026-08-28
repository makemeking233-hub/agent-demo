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
  /** 权限策略（read/write/shell 默认 + 敏感路径 glob） */
  private final PermissionPolicy policy;

  /** 路径 glob 匹配器（敏感路径检测） */
  private final PermissionPathMatcher pathMatcher;

  /** 默认策略构造（{@link PermissionPolicy#defaults()}） */
  public PermissionManager() {
    this(PermissionPolicy.defaults());
  }

  /**
   * 自定义策略构造。
   *
   * @param policy 权限策略（不可空）
   */
  public PermissionManager(PermissionPolicy policy) {
    this.policy = policy;
    this.pathMatcher = new PermissionPathMatcher(policy.sensitivePathPatterns());
  }

  /**
   * 主裁决方法。
   *
   * @param toolName 工具名
   * @param input 工具输入（用于抽取路径）
   * @param ctx 工具上下文（可空，v0.1 暂未使用）
   * @return 裁决结果
   */
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

  /**
   * 兼容 stub 调用（M2 AgentLoop 用）。
   *
   * @param toolName 工具名
   * @param input 工具输入
   * @return 裁决结果
   */
  public PermissionDecision decide(String toolName, Object input) {
    return decide(toolName, input, null);
  }

  /**
   * 从工具输入抽取文件路径（sealed {@link com.example.agent.tools.ToolInput} 多态分发）。
   *
   * @param input 工具输入
   * @return 路径字符串；类型不匹配或 ToolContext 时返回 {@code null}
   */
  private String extractPath(Object input) {
    if (input instanceof Tool.ToolContext) return null;
    if (input instanceof com.example.agent.tools.ToolInput ti) return ti.path();
    return null;
  }
}
