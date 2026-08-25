package com.example.agent;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * agent-demo 入口（M0 脚手架阶段）。
 *
 * <p>实现 Spring Boot {@link CommandLineRunner}，Spring 启动完成后会自动执行 {@link #run(String...)}。
 * M0 阶段仅打印启动信息；M1+ 会接入 picocli 子命令并在此分发到 chat / init。
 */
@SpringBootApplication
public class AgentCli implements CommandLineRunner {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(AgentCli.class, args)));
    }

    @Override
    public void run(String... args) {
        // M0 阶段为空；M1+ 添加 picocli 子命令
        System.out.println("agent-demo v0.1.0 — 启动成功（脚手架阶段）");
    }
}