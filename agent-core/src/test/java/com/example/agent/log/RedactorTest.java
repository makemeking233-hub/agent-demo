package com.example.agent.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Redactor 脱敏测试：常见密钥格式全部打码，普通文本不误伤。
 */
class RedactorTest {

    @Test
    void redactsSkStyleKeys() {
        String out = Redactor.redact("key=sk-aBcDeFgHiJkLmNoPqRsT0123456789 end");
        assertTrue(out.contains("***REDACTED***"));
        assertFalse(out.contains("sk-aBcDeFgHiJkLmNoPqRsT0123456789"));
    }

    @Test
    void redactsBearerTokens() {
        String out = Redactor.redact("Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.abc");
        assertTrue(out.contains("***REDACTED***"));
        assertFalse(out.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
    }

    @Test
    void redactsApiKeyKeyValuePairs() {
        String out = Redactor.redact("apiKey: \"sk-live-abcdefghijklmnop\"");
        assertTrue(out.contains("***REDACTED***"));
        String out2 = Redactor.redact("api_key = abcdef1234567890abcdef");
        assertTrue(out2.contains("***REDACTED***"));
    }

    @Test
    void keepsOrdinaryTextUnchanged() {
        String text = "普通文本：读取 README.md，共 128 行。";
        assertEquals(text, Redactor.redact(text));
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertEquals("", Redactor.redact(""));
        assertEquals(null, Redactor.redact(null));
    }
}
