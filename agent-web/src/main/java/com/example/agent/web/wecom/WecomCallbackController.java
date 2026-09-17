package com.example.agent.web.wecom;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业微信回调 controller（add-wecom-channel task 6.3）。
 *
 * <p>仅在 {@code agent.wecom.enabled=true} 时注册（{@link ConditionalOnProperty}）。
 * agent-web 用 WebFlux（响应式），不用 servlet API；用 {@link ResponseEntity} 返
 * 文本响应。
 *
 * <ul>
 *   <li>{@code GET /wecom/callback} — 企业微信管理后台 URL 验签；
 *       校验成功则解密 echostr 并明文回写
 *   <li>{@code POST /wecom/callback} — 接收加密消息；解密 → 派发 → 200 OK（空响应）
 * </ul>
 */
@RestController
@RequestMapping("/wecom")
@ConditionalOnProperty(name = "agent.wecom.enabled", havingValue = "true")
public class WecomCallbackController {

    private static final Logger log = LoggerFactory.getLogger(WecomCallbackController.class);

    private final WecomCrypto crypto;
    private final WecomConfigProperties props;
    private final WecomMessageDispatcher dispatcher;
    private final XmlMapper xml = new XmlMapper();

    public WecomCallbackController(WecomCrypto crypto,
                                     WecomConfigProperties props,
                                     WecomMessageDispatcher dispatcher) {
        this.crypto = crypto;
        this.props = props;
        this.dispatcher = dispatcher;
    }

    /**
     * GET /wecom/callback?msg_signature=&timestamp=&nonce=&echostr=
     *
     * <p>企业微信首次配回调 URL 时调用，需原样返回解密后的 echostr（明文）。
     */
    @GetMapping("/callback")
    public ResponseEntity<String> verify(
            @RequestParam("msg_signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {
        if (!crypto.verifySignature(props.token(), timestamp, nonce, echostr, signature)) {
            log.warn("wecom URL verify 签名校验失败");
            return ResponseEntity.status(401).body("signature_invalid");
        }
        try {
            String plain = crypto.decrypt(echostr, props.corpId());
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(plain);
        } catch (RuntimeException e) {
            log.warn("wecom URL verify 解密失败：{}", e.getMessage());
            return ResponseEntity.status(400).body("decrypt_failed");
        }
    }

    /**
     * POST /wecom/callback — 加密 XML body。
     *
     * <p>5s 回调超时窗口内：解密 → 解析 → 派发 → 返回 200 OK（"ok"）。
     */
    @PostMapping(value = "/callback",
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> onMessage(@RequestBody String xmlBody) {
        WecomEncryptedPayload payload;
        try {
            payload = xml.readValue(xmlBody, WecomEncryptedPayload.class);
        } catch (Exception e) {
            log.warn("wecom POST 解析 XML 失败：{}", e.getMessage());
            return ResponseEntity.ok("ok");
        }
        if (payload == null || payload.encrypt() == null) {
            log.warn("wecom POST 缺 encrypt 字段");
            return ResponseEntity.ok("ok");
        }
        if (!crypto.verifySignature(props.token(),
                payload.timestamp(), payload.nonce(), payload.encrypt(),
                payload.msgSignature())) {
            log.warn("wecom POST 签名校验失败");
            return ResponseEntity.ok("ok");
        }
        String plainXml;
        try {
            plainXml = crypto.decrypt(payload.encrypt(), props.corpId());
        } catch (RuntimeException e) {
            log.warn("wecom POST 解密失败：{}", e.getMessage());
            return ResponseEntity.ok("ok");
        }
        WecomEvent event;
        try {
            event = xml.readValue(plainXml, WecomEvent.class);
        } catch (Exception e) {
            log.warn("wecom POST 解析明文 XML 失败：{}", e.getMessage());
            return ResponseEntity.ok("ok");
        }
        try {
            dispatcher.dispatch(event);
        } catch (RuntimeException e) {
            log.error("wecom dispatch 异常 userId={} msgType={}: {}",
                    event.fromUserName(), event.msgType(), e.getMessage());
        }
        return ResponseEntity.ok("ok");
    }

    /** 企业微信 POST body 的根节点。 */
    public record WecomEncryptedPayload(
            @com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty(
                    localName = "ToUserName") String toUserName,
            @com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty(
                    localName = "Encrypt") String encrypt,
            @com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty(
                    localName = "TimeStamp") String timestamp,
            @com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty(
                    localName = "Nonce") String nonce,
            @com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty(
                    localName = "MsgSignature") String msgSignature) {}
}
