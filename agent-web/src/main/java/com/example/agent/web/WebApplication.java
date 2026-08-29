package com.example.agent.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * agent-web 独立入口 (OpenSpec add-web-ui-v0-1 / D2).
 *
 * <p>与 CLI 入口 {@code com.example.agent.AgentCli} 分离: AgentCli 默认走 picocli REPL,
 * 其 base {@code application.yml} 设 {@code spring.main.web-application-type=none}
 * (CLI 不需要内嵌服务器). 此入口显式声明 {@link WebApplicationType#REACTIVE},
 * 让 web profile 真正启动 WebFlux 内嵌服务器。
 *
 * <p>{@code scanBasePackages = "com.example.agent"} 复用 agent-core 全部业务 bean
 * (AgentLoop / SessionStore / PermissionManager / ToolRegistry) 以及 agent-web 的
 * web bean (控制器 / stream / filter)。
 *
 * <p>激活方式: {@code mvn -pl agent-web spring-boot:run -Dspring.profiles.active=web}
 */
@SpringBootApplication(scanBasePackages = "com.example.agent")
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(WebApplication.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(args);
    }
}

