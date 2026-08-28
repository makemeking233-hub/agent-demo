package com.example.agent.cli;

import com.example.agent.config.AgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** 生成默认 ~/.agent-demo/config.yaml + memory / sessions / cache / logs 目录（v0.1 init 子命令）。 */
@Command(name = "init", description = "在 ~/.agent-demo/ 生成默认 config.yaml")
public class InitCommand implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(InitCommand.class);

  /** 需要创建的子目录清单 */
  private static final String[] SUBDIRS = {"memory", "sessions", "cache", "logs"};

  /** --home：覆盖 home 目录（测试用） */
  @Option(names = "--home", description = "覆盖 home 目录（测试用）")
  String homeOverride;

  /** --force：覆盖已存在的 config.yaml */
  @Option(names = "--force", description = "覆盖已存在配置")
  boolean force;

  /**
   * picocli 入口：创建 ~/.agent-demo/ 子目录并写入默认 config.yaml。
   */
  @Override
  public void run() {
    try {
      Path home = resolveHome();
      Files.createDirectories(home);
      trySetPosixPermissions(home, "rwx------");

      for (String sub : SUBDIRS) {
        Path p = home.resolve(sub);
        Files.createDirectories(p);
        trySetPosixPermissions(p, "rwx------");
      }

      Path cfg = home.resolve("config.yaml");
      if (Files.exists(cfg) && !force) {
        log.info("[init] 配置已存在: {}（需要 --force 覆盖）", cfg);
        return;
      }
      ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
      yaml.writeValue(cfg.toFile(), AgentConfig.defaults());
      trySetPosixPermissions(cfg, "rw-------");
      log.info("[init] 已生成: {}", cfg);
    } catch (Exception e) {
      log.error("[init] 生成配置失败", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * 测试钩子：覆盖 home 目录后执行 {@link #run()}，返回生成的 config.yaml 路径。
   *
   * @param home 测试用 home 目录
   * @return 生成的 config.yaml 完整路径
   */
  public Path runForTest(Path home) throws Exception {
    this.homeOverride = home.toString();
    run();
    return home.resolve("config.yaml");
  }

  /**
   * 解析 home 目录（CLI flag &gt; env &gt; {@code user.home}）。
   *
   * @return 完整 home 路径（含 {@code .agent-demo} 子目录）
   */
  private Path resolveHome() {
    if (homeOverride != null) return Paths.get(homeOverride);
    String env = System.getenv("AGENT_DEMO_HOME");
    return Paths.get(
        env != null && !env.isBlank() ? env : System.getProperty("user.home"), ".agent-demo");
  }

  /**
   * 尝试设置 POSIX 权限（Windows 跳过；失败仅 warn 不阻断）。
   *
   * @param p 目标路径
   * @param mode 权限模式字符串（{@code "rwx------"} / {@code "rw-------"}）
   */
  private void trySetPosixPermissions(Path p, String mode) {
    try {
      EnumSet<PosixFilePermission> set = EnumSet.noneOf(PosixFilePermission.class);
      if (mode.contains("r")) set.add(PosixFilePermission.OWNER_READ);
      if (mode.contains("w")) set.add(PosixFilePermission.OWNER_WRITE);
      if (mode.contains("x")) set.add(PosixFilePermission.OWNER_EXECUTE);
      Files.setPosixFilePermissions(p, set);
    } catch (UnsupportedOperationException ignored) {
      /* Windows 不支持 POSIX */
    } catch (Exception e) {
      log.warn("[init] 设置权限失败: {} - {}", p, e.getMessage());
    }
  }
}
