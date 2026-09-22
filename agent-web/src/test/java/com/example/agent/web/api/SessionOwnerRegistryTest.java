package com.example.agent.web.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * SessionOwnerRegistry 单元测试（rewrite-permission-mode-dsh T9.5）。
 *
 * <p>覆盖：register / verify / unregister；同 IP 通过；异 IP 拒绝；未注册 stream 视为通过（向后兼容）。
 */
class SessionOwnerRegistryTest {

    @Test
    void registerAndVerifySameIpPasses() {
        var reg = new SessionOwnerRegistry();
        reg.register("stream-1", "127.0.0.1");
        assertTrue(reg.verify("stream-1", "127.0.0.1"));
    }

    @Test
    void verifyDifferentIpFails() {
        var reg = new SessionOwnerRegistry();
        reg.register("stream-1", "127.0.0.1");
        assertFalse(reg.verify("stream-1", "192.168.1.1"));
    }

    @Test
    void verifyUnregisteredStreamPasses() {
        var reg = new SessionOwnerRegistry();
        // 向后兼容: 旧 send 调用可能未经过 register, verify 应通过
        assertTrue(reg.verify("never-registered", "127.0.0.1"));
    }

    @Test
    void verifyWithEmptyRegisteredIpFails() {
        var reg = new SessionOwnerRegistry();
        reg.register("stream-1", "");
        // 空字符串 owner 视为不可注册, verify 应拒绝 (空 ownerIp 分支先于 callerIp 豁免)
        assertFalse(reg.verify("stream-1", "127.0.0.1"));
    }

    @Test
    void verifyWithNullCallerIpAgainstValidOwnerPasses() {
        var reg = new SessionOwnerRegistry();
        reg.register("stream-1", "127.0.0.1");
        // null callerIp 无法识别 → 豁免通过 (mock 环境兼容; 生产 proxy 注入 X-Forwarded-For)
        assertTrue(reg.verify("stream-1", null));
    }

    @Test
    void verifyWithUnknownOrBlankCallerIpPasses() {
        var reg = new SessionOwnerRegistry();
        reg.register("stream-1", "127.0.0.1");
        assertTrue(reg.verify("stream-1", "unknown"));
        assertTrue(reg.verify("stream-1", ""));
    }

    @Test
    void registerNullIpStoresEmpty() {
        var reg = new SessionOwnerRegistry();
        reg.register("stream-1", null);
        // null ownerIp 存为空字符串, verify("stream-1", "anything") 应失败
        assertFalse(reg.verify("stream-1", "127.0.0.1"));
    }

    @Test
    void registerRejectsNullOrBlankStreamId() {
        var reg = new SessionOwnerRegistry();
        try {
            reg.register(null, "1.2.3.4");
            org.junit.jupiter.api.Assertions.fail("应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            reg.register("", "1.2.3.4");
            org.junit.jupiter.api.Assertions.fail("应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    void verifyRejectsNullOrBlankStreamId() {
        var reg = new SessionOwnerRegistry();
        assertFalse(reg.verify(null, "1.2.3.4"));
        assertFalse(reg.verify("", "1.2.3.4"));
    }

    @Test
    void unregisterRemovesMapping() {
        var reg = new SessionOwnerRegistry();
        reg.register("stream-1", "127.0.0.1");
        assertTrue(reg.verify("stream-1", "127.0.0.1"));
        reg.unregister("stream-1");
        // 注销后: 未注册视为通过 (向后兼容)
        assertTrue(reg.verify("stream-1", "127.0.0.1"));
        // size 减 1
        assertTrue(reg.size() == 0);
    }

    @Test
    void sizeTracksRegisteredCount() {
        var reg = new SessionOwnerRegistry();
        assertTrue(reg.size() == 0);
        reg.register("a", "1.1.1.1");
        reg.register("b", "2.2.2.2");
        assertTrue(reg.size() == 2);
        reg.unregister("a");
        assertTrue(reg.size() == 1);
    }
}