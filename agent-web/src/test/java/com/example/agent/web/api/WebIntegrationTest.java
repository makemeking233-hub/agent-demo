package com.example.agent.web.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * T2.5 placeholder: @SpringBootTest + @AutoConfigureWebTestClient 跑 /api/health + SPA 集成验证.
 *
 * <p>当前 blocked: {@code AgentCli.run()} 在 web profile 下要早返回避免 picocli 抢 stdin
 * (T6.1 已修). 但 Spring ApplicationContext 启动时仍触发 main() 的类初始化路径, 加上
 * agent-core 不带 webflux 启动器, 在测试上下文里 server 起不来.
 *
 * <p>完整集成测试留到 T6.1 后: 把 agent-core 加 webflux-test 依赖 + AgentCli 改用
 * @PostConstruct 判 profile 早返回, 不在 run() 里判 (run 时 ctor 已注入 ctx).
 */
@Disabled("blocked: see comment, enable after T6.1 wires proper profile gating")
class WebIntegrationTest {

    @Test
    void placeholder() {}
}
