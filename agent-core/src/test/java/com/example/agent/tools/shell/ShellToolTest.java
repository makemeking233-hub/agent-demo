package com.example.agent.tools.shell;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.PermissionManager;
import com.example.agent.tools.Tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reactor.test.StepVerifier;

import java.nio.file.Path;

class ShellToolTest {
    @TempDir Path tmp;

    /** 当前平台的 shell adapter（Windows→cmd，其余→bash），避免在 Linux CI 上执行 "cmd"。 */
    private static ShellAdapter platformAdapter() {
        return System.getProperty("os.name").toLowerCase().contains("win")
                ? new CmdAdapter()
                : new BashAdapter();
    }

    private static boolean windows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private Tool.ToolContext ctx() {
        return new Tool.ToolContext(tmp, new PermissionManager(), () -> false);
    }

    @Test
    void deniesBlacklistedCommand() {
        var tool = new ShellTool(platformAdapter(), 5, 10000, false);
        String cmd = windows() ? "format C: /q" : "rm -rf /tmp/agent-demo-shell-test";
        StepVerifier.create(tool.execute(new ShellTool.Input(cmd), ctx()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();
    }

    @Test
    void allowsNormalCommand() {
        var tool = new ShellTool(platformAdapter(), 5, 10000, false);
        StepVerifier.create(tool.execute(new ShellTool.Input("echo hello"), ctx()))
                .assertNext(
                        r -> {
                            assertFalse(r.isError());
                            assertTrue(r.output().contains("hello"));
                        })
                .verifyComplete();
    }

    @Test
    void blacklistedTakesPrecedenceOverSuccess() {
        // 黑名单命中时即使命令"看似无害"也拒绝
        var tool = new ShellTool(platformAdapter(), 5, 10000, false);
        String cmd = windows() ? "del /f /s /q C:\\foo" : "rm -rf /tmp/agent-demo-shell-test";
        StepVerifier.create(tool.execute(new ShellTool.Input(cmd), ctx()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();
    }
}
