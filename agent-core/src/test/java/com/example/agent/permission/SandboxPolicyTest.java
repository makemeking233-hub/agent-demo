package com.example.agent.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SandboxPolicy record 测试（rewrite-permission-mode-dsh T1.2）。
 *
 * <p>覆盖：record 字段访问、构造校验（mode/workspaceRoot/capability/tempRoots 不可空）、
 * tempRoots 不可变（List.copyOf 语义）、capability 误用抛错。
 */
class SandboxPolicyTest {

    @Test
    void recordExposesAllFields() {
        var root = Path.of("/proj");
        var temps = List.of(Path.of("/tmp"), Path.of("/tmp/user"));
        var policy = new SandboxPolicy(SandboxMode.DONT_ASK, root, temps, Capability.FS);
        assertEquals(SandboxMode.DONT_ASK, policy.mode());
        assertSame(root, policy.workspaceRoot());
        assertEquals(temps.size(), policy.tempRoots().size());
        assertEquals(Capability.FS, policy.capability());
    }

    @Test
    void emptyTempRootsAllowed() {
        var policy = new SandboxPolicy(SandboxMode.PLAN, Path.of("/proj"), List.of(), Capability.FS);
        assertEquals(0, policy.tempRoots().size());
    }

    @Test
    void nullModeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandboxPolicy(null, Path.of("/proj"), List.of(), Capability.FS));
    }

    @Test
    void nullWorkspaceRootRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandboxPolicy(SandboxMode.PLAN, null, List.of(), Capability.FS));
    }

    @Test
    void nullCapabilityRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandboxPolicy(SandboxMode.PLAN, Path.of("/proj"), List.of(), null));
    }

    @Test
    void nullTempRootsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandboxPolicy(SandboxMode.PLAN, Path.of("/proj"), null, Capability.FS));
    }

    @Test
    void tempRootsIsImmutable() {
        var mutable = new ArrayList<>(List.of(Path.of("/tmp")));
        var policy = new SandboxPolicy(SandboxMode.DONT_ASK, Path.of("/proj"), mutable, Capability.FS);
        assertThrows(UnsupportedOperationException.class,
                () -> policy.tempRoots().add(Path.of("/etc")));
    }
}
