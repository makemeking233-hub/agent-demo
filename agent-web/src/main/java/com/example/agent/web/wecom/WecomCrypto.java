package com.example.agent.web.wecom;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 企业微信回调消息加解密 + 签名校验（add-wecom-channel task 2.1）。
 *
 * <p>参考企业微信官方 {@code WXBizMsgCrypt} 实现：
 *
 * <ul>
 *   <li>AES-256-CBC + PKCS#7 padding（Java 中 {@code PKCS5Padding} 等价，块大小 16 字节）
 *   <li>key = {@code Base64.decode(EncodingAESKey)} 全部 32 字节；IV = key 前 16 字节
 *   <li>明文格式：{@code random(16B) || msg_len(4B 网络字节序) || msg(UTF-8) || receiveId(UTF-8)}
 *   <li>签名：{@code SHA1(token, timestamp, nonce, encrypted)} 的小写 hex；
 *       校验用常量时间比较避免计时攻击
 * </ul>
 *
 * <p>线程安全：实例字段不可变；{@link Cipher} 每次调用新建。
 */
public class WecomCrypto {

    private static final String AES_ALG = "AES";
    private static final String CIPHER_ALG = "AES/CBC/PKCS5Padding";
    private static final int AES_KEY_LENGTH = 32;
    private static final int IV_LENGTH = 16;
    private static final int RANDOM_PREFIX_LENGTH = 16;
    private static final int LENGTH_PREFIX_LENGTH = 4;

    private final byte[] aesKey;
    private final byte[] iv;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param encodingAesKeyBase64 企业微信管理后台配的 EncodingAESKey（43 字符 Base64）
     * @throws IllegalArgumentException 解码后 key 长度不等于 32 字节
     */
    public WecomCrypto(String encodingAesKeyBase64) {
        if (encodingAesKeyBase64 == null) {
            throw new IllegalArgumentException("encodingAesKey 不能为空");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodingAesKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "encodingAesKey 不是合法 Base64: " + e.getMessage(), e);
        }
        if (key.length != AES_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "encodingAesKey Base64 解码后必须为 " + AES_KEY_LENGTH
                            + " 字节，实际 " + key.length);
        }
        this.aesKey = key;
        this.iv = Arrays.copyOfRange(key, 0, IV_LENGTH);
    }

    /**
     * 加密明文（明文格式：random || len || msg || receiveId）。
     *
     * @param plain     业务明文（UTF-8）
     * @param receiveId 企业 CorpID（自建应用模式）
     * @return Base64(AES-256-CBC(plain + padding))
     */
    public String encrypt(String plain, String receiveId) {
        byte[] plainBytes = plain.getBytes(StandardCharsets.UTF_8);
        byte[] receiveIdBytes = receiveId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(
                RANDOM_PREFIX_LENGTH + LENGTH_PREFIX_LENGTH
                        + plainBytes.length + receiveIdBytes.length);
        byte[] randomBytes = new byte[RANDOM_PREFIX_LENGTH];
        random.nextBytes(randomBytes);
        buf.put(randomBytes);
        buf.putInt(plainBytes.length);
        buf.put(plainBytes);
        buf.put(receiveIdBytes);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALG);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(aesKey, AES_ALG),
                    new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(buf.array());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("wecom encrypt failed", e);
        }
    }

    /**
     * 解密并校验 receiveId。
     *
     * @param encryptedBase64 Base64 密文（企业微信回调中的 {@code Encrypt} 字段）
     * @param receiveId       企业 CorpID（自建应用模式）；与密文中嵌入的不一致则抛异常
     * @return 业务明文（UTF-8）
     * @throws RuntimeException 解密失败 / PKCS#7 padding 不合法 / receiveId 不匹配
     */
    public String decrypt(String encryptedBase64, String receiveId) {
        byte[] encrypted;
        try {
            encrypted = Base64.getDecoder().decode(encryptedBase64);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("encrypted 不是合法 Base64: " + e.getMessage(), e);
        }

        byte[] decrypted;
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALG);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(aesKey, AES_ALG),
                    new IvParameterSpec(iv));
            decrypted = cipher.doFinal(encrypted);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("wecom decrypt failed (bad key or ciphertext)", e);
        }

        if (decrypted.length < RANDOM_PREFIX_LENGTH + LENGTH_PREFIX_LENGTH) {
            throw new RuntimeException(
                    "decrypted 长度不足（< random+len prefix），实际 " + decrypted.length);
        }
        int msgLen = ByteBuffer.wrap(decrypted, RANDOM_PREFIX_LENGTH, LENGTH_PREFIX_LENGTH).getInt();
        int msgStart = RANDOM_PREFIX_LENGTH + LENGTH_PREFIX_LENGTH;
        if (msgLen < 0 || msgStart + msgLen > decrypted.length) {
            throw new RuntimeException(
                    "msgLen 越界：msgLen=" + msgLen + ", decrypted.length=" + decrypted.length);
        }
        String msg = new String(decrypted, msgStart, msgLen, StandardCharsets.UTF_8);

        String embeddedReceiveId = new String(
                decrypted, msgStart + msgLen,
                decrypted.length - msgStart - msgLen,
                StandardCharsets.UTF_8);
        if (!receiveId.equals(embeddedReceiveId)) {
            throw new RuntimeException(
                    "receiveId 不匹配：期望 " + receiveId + "，密文嵌入 " + embeddedReceiveId);
        }
        return msg;
    }

    /**
     * 校验企业微信回调签名。
     *
     * <p>签名 = {@code SHA1(token, timestamp, nonce, encrypted)} 的小写 hex；
     * 用常量时间比较避免计时攻击。
     *
     * @param token      企业微信管理后台配的 Token
     * @param timestamp  URL 参数 timestamp
     * @param nonce      URL/POST 参数 nonce
     * @param encrypted  URL/POST 参数 encrypted
     * @param signature  企业微信传入的 msg_signature
     * @return true=签名匹配
     */
    public boolean verifySignature(String token, String timestamp, String nonce,
                                    String encrypted, String signature) {
        if (signature == null) return false;
        String computed = sha1Hex(token, timestamp, nonce, encrypted);
        return constantTimeEquals(computed, signature);
    }

    /**
     * 计算 {@code SHA1(token, timestamp, nonce, encrypted)} 的小写 hex。
     *
     * <p>公开供 {@link WecomMessageDispatcher} 构造回调响应签名（企业微信
     * "被动回复消息" 也需类似签名）。调用方负责传入 UTF-8 字符串。
     */
    public static String sha1Hex(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (String p : parts) {
                if (p != null) {
                    md.update(p.getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("SHA-1 不可用", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) return false;
        int diff = 0;
        for (int i = 0; i < ba.length; i++) {
            diff |= ba[i] ^ bb[i];
        }
        return diff == 0;
    }
}
