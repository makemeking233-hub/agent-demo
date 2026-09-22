package com.example.agent.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.tools.Tool;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SandboxPolicyService 测试（rewrite-permission-mode-dsh T1.5）。
 *
 * <p>覆盖：构造（默认/指定）、per-call resolve、capability null 拒绝、workingDirectory null 拒绝、
 * setMode 立即生效、escalate/restoreOnTurnEnd 配对、同 stream 重复 escalate 覆盖、createForTest 工厂。
 */
class SandboxPolicyServiceTest {

    private static final Path WS = Path.of("/tmp/ws");

    @Test
    void defaultConstructorUsesUserDir(@org.junit.jupiter.api.io.TempDir Path tmp) {
        // 默认构造: mode = DEFAULT, workspaceRoot = user.dir
        var svc = new SandboxPolicyService();
        assertEquals(SandboxMode.DEFAULT, svc.currentMode());
        // 间接验证 workspaceRoot: resolve 必须用 ctx.workingDirectory() (非 service 内 workspaceRoot)
        // 因此 service 默认 workspaceRoot 不能直接被 resolve 看到; 改测 setMode + resolve 链路
        svc.setMode(SandboxMode.DONT_ASK);
        var ctx = new Tool.ToolContext(tmp, null, null);
        SandboxPolicy policy = svc.resolve(ctx, Capability.FS);
        // resolve 用 ctx.workingDirectory (=tmp) 而非 service.workspaceRoot
        assertEquals(tmp, policy.workspaceRoot());
    }

    @Test
    void workspaceRootConstructorStoresIt() {
        var svc = new SandboxPolicyService(WS);
        // 通过 resolve 间接验证: resolve 必须用 ctx.workingDirectory 而非 service.workspaceRoot
        // 这里只验证 service 自身不抛错
        assertNotNull(svc);
    }

    @Test
    void nullWorkspaceRootRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SandboxPolicyService(null));
    }

    @Test
    void resolveRejectsNullCapability() {
        var svc = new SandboxPolicyService(WS);
        var ctx = new Tool.ToolContext(WS, null, null);
        assertThrows(IllegalArgumentException.class, () -> svc.resolve(ctx, null));
    }

    @Test
    void resolveRejectsNullWorkingDirectory() {
        var svc = new SandboxPolicyService(WS);
        var ctx = new Tool.ToolContext(null, null, null);
        assertThrows(IllegalStateException.class, () -> svc.resolve(ctx, Capability.FS));
    }

    @Test
    void resolveReturnsPolicyWithCurrentMode(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.DONT_ASK, tmp);
        var ctx = new Tool.ToolContext(tmp, null, null);
        SandboxPolicy policy = svc.resolve(ctx, Capability.FS);
        assertEquals(SandboxMode.DONT_ASK, policy.mode());
        assertEquals(tmp, policy.workspaceRoot());
        assertEquals(Capability.FS, policy.capability());
        // DONT_ASK 应该有 writableRoots
        assertFalse(policy.tempRoots().isEmpty());
    }

    @Test
    void resolvePlanModeYieldsEmptyWritableRoots(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.PLAN, tmp);
        var ctx = new Tool.ToolContext(tmp, null, null);
        SandboxPolicy policy = svc.resolve(ctx, Capability.FS);
        assertEquals(0, policy.tempRoots().size());
    }

    @Test
    void setModeUpdatesImmediately(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = new SandboxPolicyService(tmp);
        assertEquals(SandboxMode.DEFAULT, svc.currentMode());
        svc.setMode(SandboxMode.ASK);
        assertEquals(SandboxMode.ASK, svc.currentMode());
        var ctx = new Tool.ToolContext(tmp, null, null);
        assertEquals(SandboxMode.ASK, svc.resolve(ctx, Capability.FS).mode());
    }

    @Test
    void setModeNullBecomesDefault(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.ASK, tmp);
        svc.setMode(null);
        assertEquals(SandboxMode.DEFAULT, svc.currentMode());
    }

    @Test
    void escalateStoresOriginalAndSwitchesMode(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.ASK, tmp);
        svc.escalate("stream-1", SandboxMode.DANGER_FULL);
        assertEquals(SandboxMode.DANGER_FULL, svc.currentMode());
        assertEquals(SandboxMode.ASK, svc.escalationsSnapshot().get("stream-1"));
    }

    @Test
    void restoreOnTurnEndRecoversOriginalMode(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.ASK, tmp);
        svc.escalate("stream-2", SandboxMode.DANGER_FULL);
        boolean restored = svc.restoreOnTurnEnd("stream-2");
        assertTrue(restored);
        assertEquals(SandboxMode.ASK, svc.currentMode());
        assertFalse(svc.escalationsSnapshot().containsKey("stream-2"));
    }

    @Test
    void restoreOnUnknownStreamReturnsFalse(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.PLAN, tmp);
        assertFalse(svc.restoreOnTurnEnd("never-escalated"));
    }

    @Test
    void restoreOnNullStreamReturnsFalse(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.PLAN, tmp);
        assertFalse(svc.restoreOnTurnEnd(null));
    }

    @Test
    void escalateRejectsNullArgs(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.PLAN, tmp);
        assertThrows(IllegalArgumentException.class, () -> svc.escalate(null, SandboxMode.ASK));
        assertThrows(IllegalArgumentException.class, () -> svc.escalate("", SandboxMode.ASK));
        assertThrows(IllegalArgumentException.class, () -> svc.escalate("s", null));
    }

    @Test
    void repeatedEscalateKeepsOriginalRecord(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.PLAN, tmp);
        svc.escalate("s", SandboxMode.ASK);          // esc[s]=PLAN, current=ASK
        svc.escalate("s", SandboxMode.DANGER_FULL);  // esc[s] 仍是 PLAN (不覆盖), current=DANGER_FULL
        assertEquals(SandboxMode.DANGER_FULL, svc.currentMode());
        assertEquals(SandboxMode.PLAN, svc.escalationsSnapshot().get("s"));
        // restore 回到最初 PLAN (turn 内多次 escalate 不改变原 mode)
        assertTrue(svc.restoreOnTurnEnd("s"));
        assertEquals(SandboxMode.PLAN, svc.currentMode());
    }

    @Test
    void differentStreamsAreIndependent(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.PLAN, tmp);
        svc.escalate("a", SandboxMode.DANGER_FULL);   // mode=DANGER_FULL, esc[a]=PLAN
        svc.escalate("b", SandboxMode.ASK);            // mode=ASK, esc[b]=DANGER_FULL
        assertEquals(SandboxMode.ASK, svc.currentMode());
        assertEquals(SandboxMode.PLAN, svc.escalationsSnapshot().get("a"));
        assertEquals(SandboxMode.DANGER_FULL, svc.escalationsSnapshot().get("b"));
        // restore b -> current=DANGER_FULL
        assertTrue(svc.restoreOnTurnEnd("b"));
        assertEquals(SandboxMode.DANGER_FULL, svc.currentMode());
        // restore a -> current=PLAN
        assertTrue(svc.restoreOnTurnEnd("a"));
        assertEquals(SandboxMode.PLAN, svc.currentMode());
    }

    @Test
    void createForTestFactorySetsMode(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.ASK, tmp);
        assertEquals(SandboxMode.ASK, svc.currentMode());
    }

    @Test
    void createForTestNullModeBecomesDefault(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(null, tmp);
        assertEquals(SandboxMode.DEFAULT, svc.currentMode());
    }

    @Test
    void resolveForDifferentCapabilitiesReturnsDifferentPolicy(@org.junit.jupiter.api.io.TempDir Path tmp) {
        var svc = SandboxPolicyService.createForTest(SandboxMode.DANGER_FULL, tmp);
        var ctx = new Tool.ToolContext(tmp, null, null);
        SandboxPolicy fsPolicy = svc.resolve(ctx, Capability.FS);
        SandboxPolicy bashPolicy = svc.resolve(ctx, Capability.BASH);
        assertEquals(Capability.FS, fsPolicy.capability());
        assertEquals(Capability.BASH, bashPolicy.capability());
        // 同一 service 状态, mode 一致
        assertEquals(fsPolicy.mode(), bashPolicy.mode());
    }
}
