package com.example.agent.web.wecom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * WecomCrypto 加解密 + 签名校验（add-wecom-channel task 2.2）。
 *
 * <p>参考企业微信官方 WXBizMsgCrypt 实现：
 * <ul>
 *   <li>AES-256-CBC + PKCS#7 padding（Java 中 PKCS5Padding 等价）
 *   <li>key = Base64.decode(EncodingAESKey) 前 32 字节；IV = key 前 16 字节
 *   <li>明文格式：random(16B) + msg_len(4B 网络字节序) + msg + receiveid
 *   <li>签名：SHA1(token, timestamp, nonce, encrypted) hex（小写）
 * </ul>
 */
class WecomCryptoTest {

    /** 43 字符 Base64（解码后 32 字节 key，前 16 字节作 IV）。 */
    private static final String AES_KEY_BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFG"; // 43 chars
    private static final String TOKEN = "QDG6eK";
    private static final String CORP_ID = "wxcorp123";

    @Test
    void roundtripShortMessage() {
        WecomCrypto crypto = new WecomCrypto(AES_KEY_BASE64);
        String msg = "你好";
        String encrypted = crypto.encrypt(msg, CORP_ID);
        String decrypted = crypto.decrypt(encrypted, CORP_ID);
        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());
        assertEquals(msg, decrypted);
    }

    @Test
    void roundtripLongMessage() {
        WecomCrypto crypto = new WecomCrypto(AES_KEY_BASE64);
        String msg = "a".repeat(2048); // 接近 max-chars 上限
        String encrypted = crypto.encrypt(msg, CORP_ID);
        String decrypted = crypto.decrypt(encrypted, CORP_ID);
        assertEquals(msg, decrypted);
    }

    @Test
    void roundtripAsciiMessage() {
        WecomCrypto crypto = new WecomCrypto(AES_KEY_BASE64);
        String msg = "hello world 123!@#";
        String encrypted = crypto.encrypt(msg, CORP_ID);
        assertEquals(msg, crypto.decrypt(encrypted, CORP_ID));
    }

    @Test
    void signatureMatchesSha1OfParts() {
        WecomCrypto crypto = new WecomCrypto(AES_KEY_BASE64);
        String timestamp = "1409659813";
        String nonce = "testnonce";
        String encrypted = "encrypted-msg-base64";
        String expected = sha1Hex(TOKEN, timestamp, nonce, encrypted);

        assertTrue(crypto.verifySignature(TOKEN, timestamp, nonce, encrypted, expected));
    }

    @Test
    void signatureRejectsTamperedValue() {
        WecomCrypto crypto = new WecomCrypto(AES_KEY_BASE64);
        String timestamp = "1409659813";
        String nonce = "testnonce";
        String encrypted = "encrypted-msg-base64";
        String expected = sha1Hex(TOKEN, timestamp, nonce, encrypted);
        String tampered = flipLastChar(expected);

        assertFalse(crypto.verifySignature(TOKEN, timestamp, nonce, encrypted, tampered));
    }

    @Test
    void signatureRejectsDifferentToken() {
        WecomCrypto crypto = new WecomCrypto(AES_KEY_BASE64);
        String timestamp = "1409659813";
        String nonce = "testnonce";
        String encrypted = "encrypted-msg-base64";
        String expected = sha1Hex(TOKEN, timestamp, nonce, encrypted);

        assertFalse(crypto.verifySignature("WRONG-TOKEN", timestamp, nonce, encrypted, expected));
    }

    @Test
    void decryptRejectsAesKeyWrongLength() {
        // EncodingAESKey 必须 43 字符
        assertThrows(IllegalArgumentException.class, () -> new WecomCrypto("tooshort"));
    }

    @Test
    void decryptRejectsCorruptedCiphertext() {
        WecomCrypto crypto = new WecomCrypto(AES_KEY_BASE64);
        String encrypted = crypto.encrypt("hello", CORP_ID);
        // 翻转密文最后一字符（仍合法 Base64 但解不出来正确明文）
        String corrupted = flipLastChar(encrypted);
        assertThrows(RuntimeException.class, () -> crypto.decrypt(corrupted, CORP_ID));
    }

    @Test
    void decryptRejectsWrongCorpId() {
        WecomCrypto crypto = new WecomCrypto(AES_KEY_BASE64);
        String encrypted = crypto.encrypt("hello", CORP_ID);
        assertThrows(RuntimeException.class, () -> crypto.decrypt(encrypted, "wrong-corp"));
    }

    // ----- helpers -----

    private static String sha1Hex(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (String p : parts) {
                md.update(p.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String flipLastChar(String s) {
        char last = s.charAt(s.length() - 1);
        char flipped = last == 'a' ? 'b' : 'a';
        return s.substring(0, s.length() - 1) + flipped;
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private static void assertEquals(String expected, String actual) {
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
