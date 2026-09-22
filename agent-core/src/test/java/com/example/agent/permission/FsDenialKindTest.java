package com.example.agent.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * FsDenialKind 枚举测试（rewrite-permission-mode-dsh T1.3）。
 *
 * <p>覆盖：5 种 kind 枚举值、suggestedMode 决策表（见 sandbox-policy spec §"DenialKind 与 escalation 推荐映射"）。
 */
class FsDenialKindTest {

    @Test
    void readOutOfBoundsSuggestsNothing() {
        // 升级到 DANGER_FULL 也仅放行文件; reading /tmp 之外仍 ASK 询问, 不建议升级
        assertNull(FsDenialKind.READ_OUT_OF_BOUNDS.suggestedMode());
    }

    @Test
    void writeOutOfBoundsSuggestsDangerFull() {
        assertEquals(SandboxMode.DANGER_FULL, FsDenialKind.WRITE_OUT_OF_BOUNDS.suggestedMode());
    }

    @Test
    void sensitivePathSuggestsNothing() {
        assertNull(FsDenialKind.SENSITIVE_PATH.suggestedMode());
    }

    @Test
    void toolDenySuggestsNothing() {
        assertNull(FsDenialKind.TOOL_DENY.suggestedMode());
    }

    @Test
    void modeRejectedSuggestsAsk() {
        assertEquals(SandboxMode.ASK, FsDenialKind.MODE_REJECTED.suggestedMode());
    }

    @Test
    void allSuggestedModesTableMatches() {
        var table = FsDenialKind.allSuggestedModes();
        assertEquals(5, table.size());
        assertNull(table.get(FsDenialKind.READ_OUT_OF_BOUNDS));
        assertEquals(SandboxMode.DANGER_FULL, table.get(FsDenialKind.WRITE_OUT_OF_BOUNDS));
        assertNull(table.get(FsDenialKind.SENSITIVE_PATH));
        assertNull(table.get(FsDenialKind.TOOL_DENY));
        assertEquals(SandboxMode.ASK, table.get(FsDenialKind.MODE_REJECTED));
    }
}
