package com.example.agent.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

/**
 * ~/.agent-demo/settings.yaml 路径解析与文件/目录创建（add-settings-foundation M1）。
 *
 * <p>home 解析规则与 {@code InitCommand.resolveHome()} 一致：CLI flag > env > {@code user.home}。
 *
 * <p>目录权限遵循 §3 Fail-Closed：0700；文件权限 0600；Windows 下 POSIX 权限不支持，仅 warn 不阻断。
 */
public final class SettingsFile {
    private static final Logger log = LoggerFactory.getLogger(SettingsFile.class);

    /** settings 文件名 */
    public static final String SETTINGS_FILE_NAME = "settings.yaml";

    private final Path home;
    private final Path file;

    /**
     * @param home agent-demo home（通常为 {@code ~/.agent-demo/}）
     */
    public SettingsFile(Path home) {
        this.home = home;
        this.file = home.resolve(SETTINGS_FILE_NAME);
    }

    /**
     * 解析 home 目录（CLI flag > env > {@code user.home}）。
     */
    public static Path resolveDefaultHome(String cliHome, String envHome) {
        if (cliHome != null && !cliHome.isBlank()) return Paths.get(cliHome);
        if (envHome != null && !envHome.isBlank()) return Paths.get(envHome);
        return Paths.get(System.getProperty("user.home"), ".agent-demo");
    }

    public Path home() {
        return home;
    }

    public Path file() {
        return file;
    }

    /**
     * 确保 home 目录存在且权限 0700；返回 settings.yaml 路径。
     */
    public Path ensureFile() throws IOException {
        Files.createDirectories(home);
        trySetPosixPermissions(home, "rwx------");
        if (!Files.exists(file)) {
            Files.createFile(file);
            trySetPosixPermissions(file, "rw-------");
        }
        return file;
    }

    static void trySetPosixPermissions(Path p, String mode) {
        try {
            EnumSet<PosixFilePermission> set = EnumSet.noneOf(PosixFilePermission.class);
            if (mode.contains("r")) set.add(PosixFilePermission.OWNER_READ);
            if (mode.contains("w")) set.add(PosixFilePermission.OWNER_WRITE);
            if (mode.contains("x")) set.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(p, set);
        } catch (UnsupportedOperationException ignored) {
            /* Windows 不支持 POSIX */
        } catch (IOException e) {
            log.warn("[settings] 设置权限失败: {} - {}", p, e.getMessage());
        }
    }
}
