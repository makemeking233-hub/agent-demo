package com.example.agent.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * WritableRoots 工具类测试（rewrite-permission-mode-dsh T1.4）。
 *
 * <p>覆盖：4 种 mode 各自的 writableRoots 行为、canonicalize IOException 回退、contains 路径判定、null policy 拒绝。
 */
class WritableRootsTest {

    @Test
    void planModeReturnsEmpty() {
        var policy = new SandboxPolicy(SandboxMode.PLAN, Path.of("/proj"), List.of(), Capability.FS);
        assertEquals(0, WritableRoots.writableRoots(policy).size());
    }

    @Test
    void askModeReturnsEmpty() {
        var policy = new SandboxPolicy(SandboxMode.ASK, Path.of("/proj"), List.of(), Capability.FS);
        assertEquals(0, WritableRoots.writableRoots(policy).size());
    }

    @Test
    void dangerFullModeReturnsEmpty() {
        var policy = new SandboxPolicy(SandboxMode.DANGER_FULL, Path.of("/proj"), List.of(), Capability.FS);
        assertEquals(0, WritableRoots.writableRoots(policy).size());
    }

    @Test
    void dontAskModeReturnsThreeRoots(@TempDir Path workspace) throws Exception {
        // workspace 存在以便 canonicalize
        var policy = new SandboxPolicy(SandboxMode.DONT_ASK, workspace, List.of(), Capability.FS);
        List<Path> roots = WritableRoots.writableRoots(policy);
        // 在 Windows /tmp 可能不存在, canonicalize 回退原拼写; 所以最多 3 个, 最少 2 个 (workspace + tmpdir)
        assertTrue(roots.size() >= 2, "至少 workspace + tmpdir");
        assertTrue(roots.size() <= 3, "最多 workspace + /tmp + tmpdir");
        assertTrue(roots.contains(workspace.toRealPath()), "包含 workspace canonical 形式");
    }

    @Test
    void canonicalizeResolvesExistingPath(@TempDir Path tmp) throws Exception {
        Path real = WritableRoots.canonicalize(tmp);
        assertEquals(tmp.toRealPath(), real);
    }

    @Test
    void canonicalizeFallsBackOnMissing() {
        // 不存在的路径: canonicalize 回退原拼写而非抛错
        Path ghost = Path.of("/nonexistent-path-for-test-12345");
        assertEquals(ghost, WritableRoots.canonicalize(ghost));
    }

    @Test
    void canonicalizeNullReturnsNull() {
        assertEquals(null, WritableRoots.canonicalize(null));
    }

    @Test
    void nullPolicyRejected() {
        assertThrows(IllegalArgumentException.class, () -> WritableRoots.writableRoots(null));
    }

    @Test
    void containsRecognizesPathUnderRoot(@TempDir Path tmp) throws Exception {
        Path root = tmp.toRealPath();
        Path child = Files.createDirectory(tmp.resolve("child"));
        var roots = List.of(root);
        assertTrue(WritableRoots.contains(roots, child));
    }

    @Test
    void containsRejectsPathOutsideRoot(@TempDir Path tmp, @TempDir Path other) throws Exception {
        var roots = List.of(tmp.toRealPath());
        assertFalse(WritableRoots.contains(roots, other));
    }

    @Test
    void containsRejectsOnEmptyRoots() {
        assertFalse(WritableRoots.contains(List.of(), Path.of("/anything")));
    }

    @Test
    void containsRejectsOnNullPath() {
        assertFalse(WritableRoots.contains(List.of(Path.of("/")), null));
    }

    @Test
    void dontAskRootsAreDistinct(@TempDir Path workspace) {
        // 如果 /tmp 与 tmpdir 是同一路径, 应去重
        var policy = new SandboxPolicy(SandboxMode.DONT_ASK, workspace, List.of(), Capability.FS);
        List<Path> roots = WritableRoots.writableRoots(policy);
        long distinct = roots.stream().distinct().count();
        assertEquals(roots.size(), distinct, "无重复根");
    }

    @Test
    void dontAskReturnsNonNull() {
        var policy = new SandboxPolicy(SandboxMode.DONT_ASK, Path.of("/proj"), List.of(), Capability.FS);
        List<Path> roots = WritableRoots.writableRoots(policy);
        assertNotNull(roots);
        assertFalse(roots.isEmpty());
    }
}
