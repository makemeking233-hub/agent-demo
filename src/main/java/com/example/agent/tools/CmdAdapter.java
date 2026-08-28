package com.example.agent.tools;

import java.util.List;

/**
 * Windows cmd.exe ShellAdapter。
 *
 * <p>命令通过 {@code cmd.exe /c <command>} 执行。
 */
public class CmdAdapter implements ShellAdapter {
  /**
   * @param command 用户命令
   * @return {@code ["cmd.exe", "/c", command]}
   */
  @Override
  public List<String> commandLine(String command) {
    return List.of("cmd.exe", "/c", command);
  }

  /**
   * @return cmd 默认黑名单（5 项：format、diskpart、bcdedit、rmdir /s /q、del /f /s /q）
   */
  @Override
  public List<String> defaultDenylist() {
    return ShellDefaults.CMD;
  }
}
