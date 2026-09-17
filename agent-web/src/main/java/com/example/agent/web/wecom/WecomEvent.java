package com.example.agent.web.wecom;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * 企业微信回调 XML 事件（add-wecom-channel task 6.1）。
 *
 * <p>字段名按企业微信官方 XML 节点定义；{@code @JacksonXmlProperty} 用
 * 小写 local-name 反序列化（Jackson 默认按 bean 名匹配，不区分大小写）。
 *
 * @param fromUserName 发送方 userId
 * @param content      消息文本（仅 MsgType=text 时非空）
 * @param msgType      消息类型：text / image / voice / event / location / link
 * @param createTime   事件 unix 时间戳（秒）
 * @param event        事件类型（仅 MsgType=event 时非空，如 subscribe / unsubscribe）
 */
public record WecomEvent(
        @JacksonXmlProperty(localName = "FromUserName") String fromUserName,
        @JacksonXmlProperty(localName = "Content") String content,
        @JacksonXmlProperty(localName = "MsgType") String msgType,
        @JacksonXmlProperty(localName = "CreateTime") long createTime,
        @JacksonXmlProperty(localName = "Event") String event) {

    public boolean isText() {
        return "text".equalsIgnoreCase(msgType);
    }

    public boolean isEvent() {
        return "event".equalsIgnoreCase(msgType);
    }
}
