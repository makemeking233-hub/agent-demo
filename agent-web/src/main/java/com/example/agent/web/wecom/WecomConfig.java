package com.example.agent.web.wecom;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 企业微信通道启动校验（add-wecom-channel task 1.3）。
 *
 * <p>仅在 {@code agent.wecom.enabled=true} 时加载。校验失败抛 {@link IllegalStateException}
 * 阻止 Spring 启动（fail-fast）。校验项：
 *
 * <ul>
 *   <li>必填字段（corpId / agentId / secret / token / encodingAesKey / callbackBaseUrl）非空
 *   <li>{@code encodingAesKey} 长度恰好 = 43（Base64 解码后取前 32 字节作 AES-256 key）
 *   <li>{@code callbackBaseUrl} 以 {@code https://} 开头（企业微信拒 http 回调）
 *   <li>流式推送参数（reply.*）均为正数
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(WecomConfigProperties.class)
@ConditionalOnProperty(name = "agent.wecom.enabled", havingValue = "true")
public class WecomConfig {

    private static final Logger log = LoggerFactory.getLogger(WecomConfig.class);

    private static final int AES_KEY_LENGTH = 43;

    private final WecomConfigProperties props;

    public WecomConfig(WecomConfigProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void validate() {
        requireNonBlank(props.corpId(), "agent.wecom.corp-id");
        requireNonBlank(props.agentId(), "agent.wecom.agent-id");
        requireNonBlank(props.secret(), "agent.wecom.secret");
        requireNonBlank(props.token(), "agent.wecom.token");
        requireNonBlank(props.encodingAesKey(), "agent.wecom.encoding-aes-key");
        requireNonBlank(props.callbackBaseUrl(), "agent.wecom.callback-base-url");

        if (props.encodingAesKey().length() != AES_KEY_LENGTH) {
            throw new IllegalStateException(
                    "agent.wecom.encoding-aes-key 长度必须恰好为 43 字符（Base64），实际="
                            + props.encodingAesKey().length());
        }

        if (!props.callbackBaseUrl().startsWith("https://")) {
            throw new IllegalStateException(
                    "agent.wecom.callback-base-url 必须以 https:// 开头（企业微信拒 http），实际="
                            + props.callbackBaseUrl());
        }

        WecomConfigProperties.Reply reply = props.reply();
        if (reply == null) {
            throw new IllegalStateException("agent.wecom.reply 必须配置（嵌套字段）");
        }
        if (reply.flushIntervalMs() <= 0) {
            throw new IllegalStateException("agent.wecom.reply.flush-interval-ms 必须为正数");
        }
        if (reply.flushMinChars() <= 0) {
            throw new IllegalStateException("agent.wecom.reply.flush-min-chars 必须为正数");
        }
        if (reply.maxChars() <= 0 || reply.maxChars() > 4096) {
            throw new IllegalStateException(
                    "agent.wecom.reply.max-chars 必须在 (0, 4096] 区间（企业微信 markdown 上限 4096 字节）");
        }
        if (reply.retryBackoffMs() <= 0) {
            throw new IllegalStateException("agent.wecom.reply.retry-backoff-ms 必须为正数");
        }

        log.info("WecomConfig 校验通过：corp-id={}, agent-id={}, callback={}",
                props.corpId(), props.agentId(), props.callbackBaseUrl());
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 必须配置（非空）");
        }
    }
}
