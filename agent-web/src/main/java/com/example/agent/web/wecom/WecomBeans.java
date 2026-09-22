package com.example.agent.web.wecom;

import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 企业微信通道 Spring bean 装配（add-wecom-channel task 6.5）。
 *
 * <p>仅在 {@code agent.wecom.enabled=true} 时生效（与 {@link WecomConfig} / {@link WecomCallbackController}
 * 的 {@link ConditionalOnProperty} 一致）。
 */
@Configuration
@ConditionalOnProperty(name = "agent.wecom.enabled", havingValue = "true")
public class WecomBeans {

    @Bean
    public WecomCrypto wecomCrypto(WecomConfigProperties props) {
        return new WecomCrypto(props.encodingAesKey());
    }

    @Bean
    public WecomClient wecomClient(WecomConfigProperties props) {
        return new WecomClient(props);
    }

    @Bean
    public WecomSessionMapper wecomSessionMapper() {
        // 与 ChatCommand 行为一致：~/.agent-demo/wecom/ 持久化 userId→sessionId 映射
        // fix-agent-home-isolation：走单一入口，使测试隔离（agent.demo.home）也能盖住 wecom 映射
        Path agentDataDir = com.example.agent.config.AgentPaths.agentHome();
        return new WecomSessionMapper(agentDataDir.resolve("wecom"));
    }

    @Bean
    public WecomReplyPusher wecomReplyPusher(WecomClient client, WecomConfigProperties props) {
        return new WecomReplyPusher(client, props.reply(), System::currentTimeMillis);
    }
}
