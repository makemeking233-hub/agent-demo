package com.example.agent.tools;

import java.util.List;

/**
 * Windows PowerShell ShellAdapter。
 *
 * <p>命令通过 {@code powershell.exe -Command <command>} 执行。
 */
public class PowerShellAdapter implements ShellAdapter {
    /**
     * @param command 用户命令
     * @return {@code ["powershell.exe", "-Command", command]}
     */
    @Override
    public List<String> commandLine(String command) {
        return List.of("powershell.exe", "-Command", command);
    }

    /**
     * @return PowerShell 默认黑名单（4 项：Format-Volume、Remove-Item -Recurse -Force、diskpart、bcdedit）
     */
    @Override
    public List<String> defaultDenylist() {
        return List.of("Format-Volume", "Remove-Item -Recurse -Force", "diskpart", "bcdedit");
    }
}