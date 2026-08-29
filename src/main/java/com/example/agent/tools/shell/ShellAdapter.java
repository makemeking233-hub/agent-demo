package com.example.agent.tools.shell;

import java.util.List;

/**
 * 跨平台 shell 抽象。
 *
 * <p>黑名单匹配语义（详见 design.md §6.6 + test-design.md Q4 答复）：
 *
 * <ul>
 *   <li>归一化：命令名取 basename（{@code /bin/rm} -> {@code rm}）
 *   <li>短参数簇展开：{@code -rf} 等价 {@code -fr} 等价 {@code -r -f} 等价 {@code {r, f}}
 *   <li>命中条件：命令名相同，且黑名单条目的标志集合包含于输入命令的标志集合
 * </ul>
 *
 * <p>v0.1 简化：默认实现为 {@link DefaultDenylistMatcher}（基于 flags + basename 语义）； 子类可 override {@link
 * #denylistMatcher()} 提供 regex / glob / LLM 判定等更复杂策略。
 */
public interface ShellAdapter {
  /**
   * 组装最终命令行（含 executable + arg）。
   *
   * @param command 用户输入的命令
   * @return 完整命令行（executable + arg + command）
   */
  List<String> commandLine(String command);

  /**
   * 该 shell 默认危险命令黑名单（每条格式：{@code cmd [-flags]...}）。
   *
   * @return 黑名单条目列表
   */
  List<String> defaultDenylist();

  /**
   * 黑名单匹配器（策略模式；默认 {@link DefaultDenylistMatcher}）。
   *
   * @return 当前 adapter 使用的匹配器
   */
  default DenylistMatcher denylistMatcher() {
    return new DefaultDenylistMatcher(defaultDenylist());
  }

  /**
   * 黑名单匹配（默认委托 {@link #denylistMatcher()}）。
   *
   * @param command 待检查命令
   * @return true=命中黑名单
   */
  default boolean isDenylisted(String command) {
    return denylistMatcher().matches(command);
  }
}
