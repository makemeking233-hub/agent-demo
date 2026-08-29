package com.example.agent.tools.shell;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 黑名单匹配默认实现（详见 design.md §6.6 + test-design.md Q4）。
 *
 * <p>语义：
 *
 * <ul>
 *   <li>归一化：命令名取 basename（{@code /bin/rm} → {@code rm}）
 *   <li>短参数簇展开：{@code -rf} ≡ {@code -fr} ≡ {@code -r -f} ≡ {@code {r, f}}
 *   <li>命中条件：命令名相同，且黑名单条目的 flag 集合 ⊆ 输入命令的 flag 集合
 * </ul>
 */
public final class DefaultDenylistMatcher implements DenylistMatcher {
  private final List<String> patterns;

  /**
   * 构造匹配器。
   *
   * @param patterns 黑名单条目列表（{@code cmd [-flags]...} 格式）
   */
  public DefaultDenylistMatcher(List<String> patterns) {
    this.patterns = List.copyOf(patterns);
  }

  @Override
  public boolean matches(String command) {
    if (command == null || command.isBlank()) return false;
    Set<Character> inputFlags = flagsOf(command);
    String inputBase = baseName(firstToken(command));
    for (String blocked : patterns) {
      String bCmd = baseName(firstToken(blocked));
      if (!bCmd.equals(inputBase)) continue;
      Set<Character> blockedFlags = flagsOf(blocked);
      if (blockedFlags.isEmpty()) return true; // 命令名命中即危险
      if (blockedFlags.stream().allMatch(inputFlags::contains)) return true;
    }
    return false;
  }

  /** 取命令的第一个 token（命令名） */
  static String firstToken(String cmd) {
    String trimmed = cmd.trim();
    int sp = trimmed.indexOf(' ');
    return sp < 0 ? trimmed : trimmed.substring(0, sp);
  }

  /** 取路径的 basename */
  static String baseName(String token) {
    int slash = token.lastIndexOf('/');
    return slash < 0 ? token : token.substring(slash + 1);
  }

  /** 提取命令中所有短选项 flag（{@code -x} 形式） */
  static Set<Character> flagsOf(String cmd) {
    Set<Character> flags = new HashSet<>();
    String[] tokens = cmd.split("\\s+");
    for (int i = 1; i < tokens.length; i++) {
      String t = tokens[i];
      if (!t.startsWith("-") || t.equals("-")) continue;
      for (int j = 1; j < t.length(); j++) {
        char c = t.charAt(j);
        if (c == '-') break; // 长选项 `--xx` 不计入
        flags.add(c);
      }
    }
    return flags;
  }
}
