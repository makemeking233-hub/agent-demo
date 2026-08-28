package com.example.agent.tools;

/**
 * 黑名单匹配策略接口（详见 design.md §6.6 黑名单匹配语义）。
 *
 * <p>默认实现见 {@link DefaultDenylistMatcher}（基于命令名 + 短参数簇 + flag 包含语义）。 后续可扩展为 regex / glob / LLM 判定等。
 */
public interface DenylistMatcher {
  /**
   * 判断命令是否命中黑名单。
   *
   * @param command 待检查命令（含参数）
   * @return true=命中黑名单（需要二次确认或拒绝）
   */
  boolean matches(String command);
}