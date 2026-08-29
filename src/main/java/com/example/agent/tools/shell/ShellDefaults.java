package com.example.agent.tools.shell;

import java.util.List;

/**
 * 跨平台 shell 默认黑名单常量（详见 design.md §6.6）。
 *
 * <p>每个常量供对应 Adapter 注入到 {@link DefaultDenylistMatcher}。
 */
public final class ShellDefaults {
  /** Bash（Unix / Linux / macOS）默认黑名单 */
  public static final List<String> BASH = List.of("rm -rf", "mkfs", "dd if=", "shutdown", "reboot");

  /** cmd.exe（Windows）默认黑名单 */
  public static final List<String> CMD =
      List.of("format", "diskpart", "bcdedit", "rmdir /s /q", "del /f /s /q");

  /** PowerShell（Windows）默认黑名单 */
  public static final List<String> POWERSHELL =
      List.of("Format-Volume", "Remove-Item -Recurse -Force", "diskpart", "bcdedit");

  private ShellDefaults() {}
}
