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
 *
 * <p>v0.1 web 扩展（add-web-ui-v0-1 / T6.1）: web profile 下 {@link #run} 早返回,
 * 不调 picocli / System.exit, 让 Spring WebFlux HTTP server 持续监听.
 * CLI profile (默认) 行为不变, picocli 派发到 chat/init 后 System.exit.
 */
@SpringBootApplication
@Command(
        name = "agent-demo",
        mixinStandardHelpOptions = true,
        version = "agent-demo 0.1.0")
public class AgentCli implements CommandLineRunner {

    private ApplicationContext context;

    public AgentCli() {}

    @Autowired
    public AgentCli(ApplicationContext context) {
        this.context = context;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(AgentCli.class, args)));
    }

    @Override
    public void run(String... args) {
        // v0.1 web: web profile 下不调 picocli, 让 HTTP server 持续跑.
        String[] activeProfiles = context.getEnvironment().getActiveProfiles();
        boolean webProfile = java.util.Arrays.asList(activeProfiles).contains("web");
        if (webProfile) {
            return;
        }
        // CLI profile (默认) — 跟 v0.1 一样
        String[] effectiveArgs = args.length == 0 ? new String[] {"chat"} : args;
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
