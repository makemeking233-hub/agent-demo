package com.example.agent.tools;

import java.util.List;

/** Unix/Linux/macOS bash ShellAdapter。 */
public class BashAdapter implements ShellAdapter {
    @Override
    public List<String> commandLine(String command) {
        return List.of("/bin/bash", "-c", command);
    }

    @Override
    public List<String> defaultDenylist() {
        return List.of("rm -rf", "mkfs", "dd if=", "shutdown", "reboot");
    }
}