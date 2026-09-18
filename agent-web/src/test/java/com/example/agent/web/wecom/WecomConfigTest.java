package com.example.agent.web.wecom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * WecomConfig 启动校验（add-wecom-channel task 1.3）。
 *
 * <p>不依赖 Spring 上下文；直接构造 {@link WecomConfig} 调 {@code validate()} 即可覆盖所有分支。
 */
class WecomConfigTest {

    private static WecomConfigProperties validProps() {
        return new WecomConfigProperties(
                true,
                "wxcorp123",
                "1000002",
                "secret_abc",
                "token_xyz",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFG", // 43 chars
                "https://example.com",
                new WecomConfigProperties.Reply(3000L, 500, 4000, 30000L));
    }

    @Test
    void acceptsAllValidFields() {
        assertDoesNotThrow(() -> new WecomConfig(validProps()).validate());
    }

    @Test
    void rejectsEmptyCorpId() {
        WecomConfigProperties p = new WecomConfigProperties(
                true, "", "1000002", "s", "t", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFG",
                "https://example.com", new WecomConfigProperties.Reply(3000L, 500, 4000, 30000L));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new WecomConfig(p).validate());
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("corp-id"));
    }

    @Test
    void rejectsEmptyAgentId() {
        WecomConfigProperties p = new WecomConfigProperties(
                true, "c", "", "s", "t", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFG",
                "https://example.com", new WecomConfigProperties.Reply(3000L, 500, 4000, 30000L));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new WecomConfig(p).validate());
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("agent-id"));
    }

    @Test
    void rejectsEmptySecret() {
        WecomConfigProperties p = new WecomConfigProperties(
                true, "c", "a", "", "t", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFG",
                "https://example.com", new WecomConfigProperties.Reply(3000L, 500, 4000, 30000L));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new WecomConfig(p).validate());
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("secret"));
    }

    @Test
    void rejectsEmptyToken() {
        WecomConfigProperties p = new WecomConfigProperties(
                true, "c", "a", "s", "", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFG",
                "https://example.com", new WecomConfigProperties.Reply(3000L, 500, 4000, 30000L));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new WecomConfig(p).validate());
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("token"));
    }

    @Test
    void rejectsAesKeyWrongLength() {
        WecomConfigProperties p = new WecomConfigProperties(
                true, "c", "a", "s", "t", "tooshort", // 8 chars not 43
                "https://example.com", new WecomConfigProperties.Reply(3000L, 500, 4000, 30000L));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new WecomConfig(p).validate());
        org.junit.jupiter.api.Assertions.assertTrue(
                e.getMessage().contains("encoding-aes-key") && e.getMessage().contains("43"));
    }

    @Test
    void rejectsNonHttpsCallbackUrl() {
        WecomConfigProperties p = new WecomConfigProperties(
                true, "c", "a", "s", "t", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFG",
                "http://insecure.example.com", new WecomConfigProperties.Reply(3000L, 500, 4000, 30000L));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new WecomConfig(p).validate());
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("https://"));
    }

    @Test
    void rejectsEmptyCallbackUrl() {
        WecomConfigProperties p = new WecomConfigProperties(
                true, "c", "a", "s", "t", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFG",
                "", new WecomConfigProperties.Reply(3000L, 500, 4000, 30000L));
        assertThrows(IllegalStateException.class, () -> new WecomConfig(p).validate());
    }
}
