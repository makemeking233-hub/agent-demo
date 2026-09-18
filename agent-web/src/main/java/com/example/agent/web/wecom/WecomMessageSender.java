package com.example.agent.web.wecom;

/**
 * 企业微信 API 客户端抽象（add-wecom-channel task 5.1）。
 *
 * <p>从 {@link WecomClient} 抽出最小接口，便于
 * {@link WecomReplyPusher} 测试替身（FakeClient）。
 */
public interface WecomMessageSender {

    /**
     * 推送 markdown 消息；errcode=45009 时抛 {@link RetryableWecomException}。
     *
     * @return 微信返回的 msgid
     */
    String sendMarkdown(String userId, String content);

    /** 获取当前有效 access_token（pusher 自身不会调，但实现要保证可获取）。 */
    String getAccessToken();
}
