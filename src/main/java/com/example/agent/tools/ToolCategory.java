package com.example.agent.tools;

/**
 * 工具语义分类（用于权限策略注册表）。
 *
 * <p>v0.1 工具按 IO 风险分 4 类：
 *
 * <ul>
 *   <li>{@link #READ} - 只读 IO（ReadFile / Ls）
 *   <li>{@link #WRITE} - 覆盖写 IO（WriteFile / EditFile）
 *   <li>{@link #SHELL} - 进程执行（Shell）
 *   <li>{@link #OTHER} - 默认（无显式分类的工具）
 * </ul>
 *
 * <p>新增工具时通过 {@link Tool#category()} 自报家门，{@code PermissionManager} 即可自动按类别走策略。
 */
public enum ToolCategory {
  /** 只读 IO */
  READ,
  /** 覆盖写 IO */
  WRITE,
  /** 进程执行 */
  SHELL,
  /** 未分类（默认最严策略） */
  OTHER
}