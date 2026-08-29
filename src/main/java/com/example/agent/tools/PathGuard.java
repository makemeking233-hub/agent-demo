package com.example.agent.tools;

import com.example.agent.permission.PermissionDecision;

/** 路径安全守卫：集中处理"含 {@code ..}"的路径越界判定，供 ReadFileTool / WriteFileTool / EditFileTool / LsTool 共用。 */
public final class PathGuard {
  /**
   * 检查路径是否含 {@code ..}（防止越权访问上级目录）。
   *
   * @param path 工具输入中的相对路径
   * @return 含 {@code ..} 时返回 {@link PermissionDecision#deny()}；否则返回 {@code null}（表示"由调用方继续判定"）
   */
  public static PermissionDecision denyIfTraversal(String path) {
    if (path == null || path.contains("..")) return PermissionDecision.deny();
    return null;
  }

  private PathGuard() {}
}
