package com.example.agent.tools;

import java.util.List;

/** Windows PowerShell ShellAdapter。 */
public class PowerShellAdapter implements ShellAdapter {
    @Override
    public List<String> commandLine(String command) {
        return List.of("powershell.exe", "-Command", command);
    }

    @Override
    public List<String> defaultDenylist() {
        return List.of("Format-Volume", "Remove-Item -Recurse -Force", "diskpart", "bcdedit");
    }
}