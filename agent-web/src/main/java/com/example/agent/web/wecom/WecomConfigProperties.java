package com.example.agent.web.wecom;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业微信通道配置（add-wecom-channel task 1.1）。
 *
 * <p>从 {@code agent.wecom} 配置块绑定。结构示例见
 * {@code agent-web/src/main/resources/application-web.yml}。
 *
 * @param enabled          是否启用通道；false 时 {@link WecomConfig} 不加载，
 *                          {@link WecomCallbackController} 不注册（@ConditionalOnProperty）
 * @param corpId           企业微信 CorpID（自建应用 bot 所属企业的 ID）
 * @param agentId          应用 AgentID（自建应用的数字 ID）
 * @param secret           应用 Secret（调 gettoken API 用）
 * @param token            回调 Token（企业微信管理后台配的回调校验串）
 * @param encodingAesKey   EncodingAESKey（43 字符 Base64；前 32 字节作 AES-256 key）
 * @param callbackBaseUrl  HTTPS 公网入口前缀（企业微信后台填的回调 URL 前缀）
 * @param reply            流式推送限频配置（嵌套）
 */
@ConfigurationProperties(prefix = "agent.wecom")
public record WecomConfigProperties(
        boolean enabled,
        String corpId,
        String agentId,
        String secret,
        String token,
        String encodingAesKey,
        String callbackBaseUrl,
        Reply reply) {

    /**
     * 流式 markdown 回复推送的限频与截断参数（add-wecom-channel task 1.1）。
     *
     * @param flushIntervalMs 累积窗口（毫秒）；窗口结束或累积 ≥ flushMinChars 触发 flush
     * @param flushMinChars    累积字符阈值
     * @param maxChars         单条 markdown 上限（字节，UTF-8；超长截断发新条）
     * @param retryBackoffMs   企业微信 API 限频（45009）后退避时长
     */
    public record Reply(
            long flushIntervalMs,
            int flushMinChars,
            int maxChars,
            long retryBackoffMs) {
    }
}
