package com.example.agent.tools;

import java.util.List;

/** Windows cmd.exe ShellAdapter。 */
public class CmdAdapter implements ShellAdapter {
    @Override
    public List<String> commandLine(String command) {
        return List.of("cmd.exe", "/c", command);
    }

    @Override
    public List<String> defaultDenylist() {
        return List.of("format", "diskpart", "bcdedit", "rmdir /s /q", "del /f /s /q");
    }
}