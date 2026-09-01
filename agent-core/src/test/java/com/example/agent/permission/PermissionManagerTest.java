package com.example.agent.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.agent.tools.Tool;
import com.example.agent.tools.file.LsTool;
import com.example.agent.tools.file.ReadFileTool;
import com.example.agent.tools.file.WriteFileTool;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

class PermissionManagerTest {

    private Tool.ToolContext ctx() {
        return new Tool.ToolContext(Paths.get("/tmp"), new PermissionManager(), () -> false);
    }

    @Test
    void readIsAllowedByDefault() {
        var mgr = new PermissionManager(PermissionPolicy.defaults());
        var d = mgr.decide("ReadFile", new ReadFileTool.Input("a.txt"), ctx());
        assertEquals(PermissionDecision.Behavior.ALLOW, d.behavior());
    }

    @Test
    void writeAsks() {
        var mgr = new PermissionManager(PermissionPolicy.defaults());
        var d = mgr.decide("WriteFile", new WriteFileTool.Input("a.txt", "x"), ctx());
        assertEquals(PermissionDecision.Behavior.ASK, d.behavior());
    }

    @Test
    void shellAsks() {
        var mgr = new PermissionManager(PermissionPolicy.defaults());
        var d =
                mgr.decide(
                        "Shell",
                        new Tool() {
                            @Override
                            public String name() {
                                return "stub";
                            }

                            @Override
                            public String description() {
                                return "";
                            }

                            @Override
                            public java.util.Map<String, Object> inputSchema() {
                                return java.util.Map.of();
                            }

                            @Override
                            public String renderUse(Object input) {
                                return "";
                            }

                            @Override
                            public String renderResult(Object output) {
                                return "";
                            }

                            @Override
                            public reactor.core.publisher.Mono<
                                            com.example.agent.tools.ToolResult<Object>>
                                    execute(Object input, ToolContext ctx) {
                                return reactor.core.publisher.Mono.empty();
                            }
                        },
                        ctx());
        // 上面 Shell 路径决策；这里直接调 decide("Shell", input)
        // 简化：直接构造一次
        var d2 = mgr.decide("Shell", new Object(), ctx());
        assertEquals(PermissionDecision.Behavior.ASK, d2.behavior());
    }

    @Test
    void sensitivePathForcesAskEvenForRead() {
        var mgr = new PermissionManager(PermissionPolicy.defaults());
        var d = mgr.decide("ReadFile", new ReadFileTool.Input(".env"), ctx());
        assertEquals(PermissionDecision.Behavior.ASK, d.behavior());
    }

    @Test
    void sensitiveSshPathForcesAsk() {
        var mgr = new PermissionManager(PermissionPolicy.defaults());
        var d = mgr.decide("ReadFile", new ReadFileTool.Input("home/.ssh/id_rsa"), ctx());
        assertEquals(PermissionDecision.Behavior.ASK, d.behavior());
    }

    @Test
    void pemFileForcesAsk() {
        var mgr = new PermissionManager(PermissionPolicy.defaults());
        var d = mgr.decide("ReadFile", new ReadFileTool.Input("certs/server.pem"), ctx());
        assertEquals(PermissionDecision.Behavior.ASK, d.behavior());
    }

    @Test
    void lsAllowByDefault() {
        var mgr = new PermissionManager(PermissionPolicy.defaults());
        var d = mgr.decide("Ls", new LsTool.Input("."), ctx());
        assertEquals(PermissionDecision.Behavior.ALLOW, d.behavior());
    }

    // ---- add-permission-mode-dropdown：模式感知 + 工作区边界 ----

    private Tool.ToolContext ctxIn(java.nio.file.Path dir) {
        return new Tool.ToolContext(dir, new PermissionManager(), () -> false);
    }

    @Test
    void readOnlyModeReadAllowWriteAsk() {
        var mgr = new PermissionManager(PermissionPolicy.defaults());
        mgr.setMode(PermissionMode.READ_ONLY);
        assertEquals(
                PermissionDecision.Behavior.ALLOW,
                mgr.decide("ReadFile", new ReadFileTool.Input("a.txt"), ctx()).behavior());
        assertEquals(
                PermissionDecision.Behavior.ASK,
                mgr.decide("WriteFile", new WriteFileTool.Input("a.txt", "x"), ctx()).behavior());
    }

    @Test
    void fullAccessModeAllowsAllIncludingSensitive() {
        var mgr = new PermissionManager(PermissionPolicy.defaults());
        mgr.setMode(PermissionMode.FULL_ACCESS);
        assertEquals(
                PermissionDecision.Behavior.ALLOW,
                mgr.decide("WriteFile", new WriteFileTool.Input("a.txt", "x"), ctx()).behavior());
        assertEquals(
                PermissionDecision.Behavior.ALLOW,
                mgr.decide("ReadFile", new ReadFileTool.Input(".env"), ctx()).behavior());
    }

    @Test
    void sensitivePathForcesAskInReadOnlyButAllowsInFull() {
        var readOnly = new PermissionManager(PermissionPolicy.defaults());
        readOnly.setMode(PermissionMode.READ_ONLY);
        assertEquals(
                PermissionDecision.Behavior.ASK,
                readOnly.decide("ReadFile", new ReadFileTool.Input(".env"), ctx()).behavior());
        var full = new PermissionManager(PermissionPolicy.defaults());
        full.setMode(PermissionMode.FULL_ACCESS);
        assertEquals(
                PermissionDecision.Behavior.ALLOW,
                full.decide("ReadFile", new ReadFileTool.Input(".env"), ctx()).behavior());
    }

    @Test
    void workspaceWriteAllowsWithinDirAsksOutside() {
        java.nio.file.Path ws = Paths.get("/ws").toAbsolutePath();
        var mgr = new PermissionManager(PermissionPolicy.defaults());
        mgr.setMode(PermissionMode.WORKSPACE_WRITE);
        mgr.setWorkingDirectory(ws);
        var inside = new WriteFileTool.Input(ws.resolve("sub/a.txt").toString(), "x");
        var outside = new WriteFileTool.Input("/outside/b.txt", "x");
        assertEquals(
                PermissionDecision.Behavior.ALLOW,
                mgr.decide("WriteFile", inside, ctxIn(ws)).behavior());
        assertEquals(
                PermissionDecision.Behavior.ASK,
                mgr.decide("WriteFile", outside, ctxIn(ws)).behavior());
    }
}
