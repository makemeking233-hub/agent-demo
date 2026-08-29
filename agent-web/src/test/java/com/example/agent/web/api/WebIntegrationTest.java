package com.example.agent.web.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** T2.5 placeholder: @SpringBootTest + @AutoConfigureWebTestClient 跑 /api/health + SPA 集成验证.
 *
 * <p>当前 @Disabled: agent-core 没引 webflux starter, 启动时 server 监听 8080 没起, WebTestClient
 * 连不上 (Connection refused). v0.2 等 T6 把 agent-core 加 webflux-test 依赖后启.
 */
@Disabled("T2.5 placeholder: 等 T6 修 agent-core 加 webflux-test 依赖")
class WebIntegrationTest {

    @Test
    void placeholder() {}
}
