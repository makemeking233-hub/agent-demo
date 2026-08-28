package com.example.agent.tools;

import java.util.List;

/**
 * Unix / Linux / macOS bash ShellAdapter。
 *
 * <p>命令通过 {@code /bin/bash -c <command>} 执行；POSIX 默认 shell。
 */
public class BashAdapter implements ShellAdapter {
  /**
   * @param command 用户命令
   * @return {@code ["/bin/bash", "-c", command]}（{@code -c} 后接命令字符串）
   */
  @Override
  public List<String> commandLine(String command) {
    return List.of("/bin/bash", "-c", command);
  }

  /**
   * @return bash 默认黑名单（5 项：rm -rf、mkfs、dd if=、shutdown、reboot）
   */
  @Override
  public List<String> defaultDenylist() {
    return ShellDefaults.BASH;
  }
}
