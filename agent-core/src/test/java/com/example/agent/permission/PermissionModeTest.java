package com.example.agent.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.agent.tools.ToolCategory;
import org.junit.jupiter.api.Test;

class PermissionModeTest {

    @Test
    void fromMapsValidValues() {
        assertEquals(PermissionMode.READ_ONLY, PermissionMode.from("read_only"));
        assertEquals(PermissionMode.WORKSPACE_WRITE, PermissionMode.from("workspace_write"));
        assertEquals(PermissionMode.FULL_ACCESS, PermissionMode.from("full_access"));
    }

    @Test
    void fromIsCaseInsensitive() {
        assertEquals(PermissionMode.READ_ONLY, PermissionMode.from("READ_ONLY"));
        assertEquals(PermissionMode.FULL_ACCESS, PermissionMode.from("Full_Access"));
    }

    @Test
    void fromThrowsOnInvalid() {
        assertThrows(IllegalArgumentException.class, () -> PermissionMode.from("bogus"));
        assertThrows(IllegalArgumentException.class, () -> PermissionMode.from(""));
        assertThrows(IllegalArgumentException.class, () -> PermissionMode.from(null));
    }

    @Test
    void defaultIsReadOnly() {
        assertEquals(PermissionMode.READ_ONLY, PermissionMode.DEFAULT);
    }

    @Test
    void readOnlyDecision() {
        var m = PermissionMode.READ_ONLY;
        assertEquals(PermissionDecision.Behavior.ALLOW, m.defaultDecision(ToolCategory.READ, false).behavior());
        assertEquals(PermissionDecision.Behavior.ASK, m.defaultDecision(ToolCategory.WRITE, true).behavior());
        assertEquals(PermissionDecision.Behavior.ASK, m.defaultDecision(ToolCategory.SHELL, false).behavior());
        assertEquals(PermissionDecision.Behavior.ASK, m.defaultDecision(ToolCategory.OTHER, false).behavior());
    }

    @Test
    void workspaceWriteDecision() {
        var m = PermissionMode.WORKSPACE_WRITE;
        assertEquals(PermissionDecision.Behavior.ALLOW, m.defaultDecision(ToolCategory.READ, false).behavior());
        assertEquals(PermissionDecision.Behavior.ALLOW, m.defaultDecision(ToolCategory.WRITE, true).behavior());
        assertEquals(PermissionDecision.Behavior.ASK, m.defaultDecision(ToolCategory.WRITE, false).behavior());
        assertEquals(PermissionDecision.Behavior.ASK, m.defaultDecision(ToolCategory.SHELL, false).behavior());
    }

    @Test
    void fullAccessDecision() {
        var m = PermissionMode.FULL_ACCESS;
        assertEquals(PermissionDecision.Behavior.ALLOW, m.defaultDecision(ToolCategory.WRITE, true).behavior());
        assertEquals(PermissionDecision.Behavior.ALLOW, m.defaultDecision(ToolCategory.SHELL, true).behavior());
        assertEquals(PermissionDecision.Behavior.ALLOW, m.defaultDecision(ToolCategory.OTHER, false).behavior());
    }
}
