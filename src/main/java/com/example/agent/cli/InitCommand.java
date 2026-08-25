package com.example.agent.cli;

import com.example.agent.config.AgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

/**
 * 生成默认 ~/.agent-demo/config.yaml + memory / sessions / cache / logs 目录（v0.1 init 子命令）。
 */
@Command(name = "init", description = "在 ~/.agent-demo/ 生成默认 config.yaml")
public class InitCommand implements Runnable {

    @Option(names = "--home", description = "覆盖 home 目录（测试用）")
    String homeOverride;

    @Option(names = "--force", description = "覆盖已存在配置")
    boolean force;

    @Override
    public void run() {
        try {
            Path home = resolveHome();
            Files.createDirectories(home);
            trySetPosixPermissions(home, "rwx------");

            for (String sub : new String[]{"memory", "sessions", "cache", "logs"}) {
                Path p = home.resolve(sub);
                Files.createDirectories(p);
                trySetPosixPermissions(p, "rwx------");
            }

            Path cfg = home.resolve("config.yaml");
            if (Files.exists(cfg) && !force) {
                System.err.println("[init] 配置已存在: " + cfg + "（使用 --force 覆盖）");
                return;
            }
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
            yaml.writeValue(cfg.toFile(), AgentConfig.defaults());
            trySetPosixPermissions(cfg, "rw-------");
            System.out.println("[init] 已生成: " + cfg);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Path runForTest(Path home) throws Exception {
        this.homeOverride = home.toString();
        run();
        return home.resolve("config.yaml");
    }

    private Path resolveHome() {
        if (homeOverride != null) return Paths.get(homeOverride);
        String env = System.getenv("AGENT_DEMO_HOME");
        return Paths.get(env != null && !env.isBlank() ? env : System.getProperty("user.home"), ".agent-demo");
    }

    private void trySetPosixPermissions(Path p, String mode) {
        try {
            EnumSet<PosixFilePermission> set = EnumSet.noneOf(PosixFilePermission.class);
            if (mode.contains("r")) set.add(PosixFilePermission.OWNER_READ);
            if (mode.contains("w")) set.add(PosixFilePermission.OWNER_WRITE);
            if (mode.contains("x")) set.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(p, set);
        } catch (UnsupportedOperationException ignored) { /* Windows */ }
        catch (Exception e) {
            System.err.println("[init] 设置权限失败: " + p + " - " + e.getMessage());
        }
    }
}