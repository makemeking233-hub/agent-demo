package com.example.agent;

import com.example.agent.cli.ChatCommand;
import com.example.agent.cli.InitCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * agent-demo 入口。
 *
 * <p>实现 Spring Boot {@link CommandLineRunner}，Spring 启动完成后会自动执行 {@link #run(String...)}，
 * 把命令行参数转交给 picocli 分发到 chat / init 子命令。
 */
@SpringBootApplication
@Command(name = "agent-demo", mixinStandardHelpOptions = true, version = "agent-demo 0.1.0",
        subcommands = {ChatCommand.class, InitCommand.class})
public class AgentCli implements CommandLineRunner {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(AgentCli.class, args)));
    }

    @Override
    public void run(String... args) {
        // Spring 启动后，picocli-spring-boot-starter 已自动注册所有 @Component 子命令
        int exitCode = new CommandLine(new AgentCli()).execute(args);
        System.exit(exitCode);
    }
}