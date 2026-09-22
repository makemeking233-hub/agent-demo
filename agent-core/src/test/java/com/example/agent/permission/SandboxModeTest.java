package com.example.agent.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * SandboxMode 枚举测试（rewrite-permission-mode-dsh T1.1）。
 *
 * <p>覆盖：四档枚举值、wireValue/from 往返、非法值抛错、DEFAULT、legacy migration map。
 */
class SandboxModeTest {

    @Test
    void wireValueRoundTrip() {
        for (SandboxMode mode : SandboxMode.values()) {
            assertEquals(mode, SandboxMode.from(mode.wireValue()));
        }
    }

    @Test
    void fromAcceptsCaseInsensitive() {
        assertEquals(SandboxMode.PLAN, SandboxMode.from("PLAN"));
        assertEquals(SandboxMode.ASK, SandboxMode.from("Ask"));
        assertEquals(SandboxMode.DANGER_FULL, SandboxMode.from("DANGER-FULL"));
        assertEquals(SandboxMode.DONT_ASK, SandboxMode.from("dontask"));
    }

    @Test
    void fromRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> SandboxMode.from(""));
        assertThrows(IllegalArgumentException.class, () -> SandboxMode.from("   "));
        assertThrows(IllegalArgumentException.class, () -> SandboxMode.from(null));
    }

    @Test
    void fromRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> SandboxMode.from("read_only"));
        assertThrows(IllegalArgumentException.class, () -> SandboxMode.from("workspace_write"));
        assertThrows(IllegalArgumentException.class, () -> SandboxMode.from("full_access"));
        assertThrows(IllegalArgumentException.class, () -> SandboxMode.from("bogus"));
    }

    @Test
    void defaultIsPlan() {
        assertEquals(SandboxMode.PLAN, SandboxMode.DEFAULT);
    }

    @Test
    void wireValuesMatchDshNaming() {
        // 验证 wire value 严格匹配 dsh 命名（前端 type 定义依赖此）
        assertEquals("plan", SandboxMode.PLAN.wireValue());
        assertEquals("ask", SandboxMode.ASK.wireValue());
        assertEquals("danger-full", SandboxMode.DANGER_FULL.wireValue());
        assertEquals("dontAsk", SandboxMode.DONT_ASK.wireValue());
    }

    @Test
    void legacyMigrationMapCoversThreeOldValues() {
        var map = SandboxMode.legacyMigrationMap();
        assertEquals(SandboxMode.PLAN, map.get("read_only"));
        assertEquals(SandboxMode.ASK, map.get("workspace_write"));
        assertEquals(SandboxMode.DANGER_FULL, map.get("full_access"));
        assertEquals(3, map.size());
    }
}
