package com.example.agent.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Command(name = "init", mixinStandardHelpOptions = true, description = "生成 ~/.agent-demo/config.yaml 默认配置")
public class InitCommand implements Runnable {
    @Override
    public void run() {
        Path target = Path.of(System.getProperty("user.home"), ".agent-demo", "config.yaml");
        try {
            Files.createDirectories(target.getParent());
            if (Files.notExists(target)) {
                Files.writeString(target, "# agent-demo 配置（请填入 API key）\nprovider:\n  apiKey: REPLACE_ME\n");
                System.out.println("已生成: " + target);
            } else {
                System.out.println("配置已存在，未覆盖: " + target);
            }
        } catch (Exception e) {
            throw new RuntimeException("生成配置失败: " + e.getMessage(), e);
        }
    }
}