package com.example.agent.tools;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * 跨平台 shell 抽象。
 *
 * <p>黑名单匹配语义（详见 design.md §6.6 + test-design.md Q4 答复）：
 * <ul>
 *   <li>归一化：命令名取 basename（{@code /bin/rm} -> {@code rm}）</li>
 *   <li>短参数簇展开：{@code -rf} 等价 {@code -fr} 等价 {@code -r -f} 等价 {@code {r, f}}</li>
 *   <li>命中条件：命令名相同，且黑名单条目的标志集合包含于输入命令的标志集合</li>
 * </ul>
 */
public interface ShellAdapter {
    /**
     * 组装最终命令行（含 executable + arg）。
     * @param command 用户输入的命令
     * @return 完整命令行（executable + arg + command）
     */
    java.util.List<String> commandLine(String command);

    /**
     * 该 shell 默认危险命令黑名单。
     * @return 黑名单条目列表（每条格式：{@code cmd [-flags]...}）
     */
    java.util.List<String> defaultDenylist();

    /**
     * 黑名单匹配。
     * @param command 待检查命令
     * @return true=命中黑名单（需要二次确认或拒绝）
     */
    default boolean isDenylisted(String command) {
        if (command == null || command.isBlank()) return false;
        Set<Character> inputFlags = flagsOf(command);
        String inputBase = baseName(firstToken(command));
        for (String blocked : defaultDenylist()) {
            String bCmd = baseName(firstToken(blocked));
            if (!bCmd.equals(inputBase)) continue;
            Set<Character> blockedFlags = flagsOf(blocked);
            if (blockedFlags.isEmpty()) return true;          // 命令名命中即危险
            if (blockedFlags.stream().allMatch(inputFlags::contains)) return true;
        }
        return false;
    }

    /**
     * 取命令的第一个 token（命令名）。
     * @param cmd 完整命令
     * @return 第一个空格之前的子串
     */
    static String firstToken(String cmd) {
        String trimmed = cmd.trim();
        int sp = trimmed.indexOf(' ');
        return sp < 0 ? trimmed : trimmed.substring(0, sp);
    }

    /**
     * 取路径的 basename。
     * @param token 完整路径
     * @return 最后一段（去掉目录）
     */
    static String baseName(String token) {
        int slash = token.lastIndexOf('/');
        return slash < 0 ? token : token.substring(slash + 1);
    }

    /**
     * 提取命令中所有 flag（短选项，{@code -x} 形式；连字符后字母逐个）。
     * @param cmd 完整命令
     * @return 短选项字符集合
     */
    static Set<Character> flagsOf(String cmd) {
        Set<Character> flags = new HashSet<>();
        String[] tokens = cmd.split("\\s+");
        for (int i = 1; i < tokens.length; i++) {
            String t = tokens[i];
            if (!t.startsWith("-") || t.equals("-")) continue;
            for (int j = 1; j < t.length(); j++) {
                char c = t.charAt(j);
                if (c == '-') break;  // 长选项 `--xx` 不计入
                flags.add(c);
            }
        }
        return flags;
    }
}