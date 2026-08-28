package com.example.agent;

import com.example.agent.cli.ChatCommand;
import com.example.agent.cli.InitCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * agent-demo 入口（M0+）。
 *
 * <p>Spring 启动后自动执行 {@link #run(String...)}，把命令行参数转交给 picocli 分发到 chat / init 子命令。
 */
@SpringBootApplication
@Command(
    name = "agent-demo",
    mixinStandardHelpOptions = true,
    version = "agent-demo 0.1.0",
    subcommands = {ChatCommand.class, InitCommand.class})
public class AgentCli implements CommandLineRunner {

  /**
   * Java 入口点。
   *
   * @param args 命令行参数（picocli 解析）
   */
  public static void main(String[] args) {
    System.exit(SpringApplication.exit(SpringApplication.run(AgentCli.class, args)));
  }

  /**
   * Spring 启动后回调：再次执行 picocli 解析（因为 Spring 启动时 picocli 未注册）。
   *
   * <p>未传子命令时默认执行 {@code chat}（v0.1：Agent 即 REPL）； 显式传 {@code init} 则生成默认配置目录。
   *
   * @param args 命令行参数
   */
  @Override
  public void run(String... args) {
    // 没传子命令 → 默认启动 chat（避免 "Missing required subcommand"）
    String[] effectiveArgs = args.length == 0 ? new String[] {"chat"} : args;
    int exitCode = new CommandLine(new AgentCli()).execute(effectiveArgs);
    System.exit(exitCode);
  }
}
