package com.example.agent;

import com.example.agent.cli.ChatCommand;
import com.example.agent.cli.InitCommand;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

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
        version = "agent-demo 0.1.0")
public class AgentCli implements CommandLineRunner {

    private ApplicationContext context;

    /**
     * 无参构造器：仅用于 picocli 直接派发（绕过 Spring 容器）的测试场景。
     *
     * <p>生产路径由 {@link #AgentCli(ApplicationContext)} 注入真实 context。
     */
    public AgentCli() {}

    /** Spring 注入构造器（生产用；@Autowired 强制单构造器注入） */
    @Autowired
    public AgentCli(ApplicationContext context) {
        this.context = context;
    }

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
     * <p>关键：picocli 默认会反射创建 subcommand 实例，绕过 Spring 导致 {@code @Value} 注入失效。
     * 必须从 Spring 容器拿 subcommand bean 并 {@code addSubcommand} 到 picocli，才能让
     * {@code application-local.yml} 的 {@code agent.provider.api-key} 等字段真正生效。
     *
     * @param args 命令行参数
     */
    @Override
    public void run(String... args) {
        // 没传子命令 → 默认启动 chat（避免 "Missing required subcommand"）
        String[] effectiveArgs = args.length == 0 ? new String[] {"chat"} : args;
        // 从 Spring 容器拿 bean（保证 @Value 注入生效）
        AgentCli cliBean = context.getBean(AgentCli.class);
        ChatCommand chatBean = context.getBean(ChatCommand.class);
        InitCommand initBean = context.getBean(InitCommand.class);
        int exitCode =
                new CommandLine(cliBean)
                        .addSubcommand(chatBean)
                        .addSubcommand(initBean)
                        .execute(effectiveArgs);
        System.exit(exitCode);
    }
}