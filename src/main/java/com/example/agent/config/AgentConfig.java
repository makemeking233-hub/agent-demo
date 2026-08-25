package com.example.agent.config;

import java.util.List;

public record AgentConfig(
        Provider provider,
        Permission permission,
        Cost cost,
        Context context,
        Shell shell,
        List<String> memoryInject) {

    public record Provider(String type, String apiKey, String baseUrl, String model, int maxOutputTokens) {}

    public record Permission(String defaultPolicy, List<String> shellDenylist) {}

    public record Cost(double inputPerMTokens, double outputPerMTokens, double warnThreshold, double stopThreshold) {}

    public record Context(int compactBuffer, int maxConsecutiveCompactFailures) {}

    public record Shell(int timeoutMs, int maxOutputBytes) {}

    public static AgentConfig defaults() {
        return new AgentConfig(
                new Provider("deepseek", "", "https://api.deepseek.com", "deepseek-chat", 8192),
                new Permission("ask-write", List.of(
                        "rm -rf /", "mkfs", "dd if=.*of=/dev/.*", "chmod -R 777 /",
                        "shutdown", "reboot",
                        "format", "rd /s /q C:\\", "del /f /s /q C:\\*",
                        "diskpart", "bcdedit", "reg delete HKLM")),
                new Cost(2.0, 8.0, 4.0, 5.0),
                new Context(8000, 3),
                new Shell(120_000, 1_000_000),
                List.of());
    }
}