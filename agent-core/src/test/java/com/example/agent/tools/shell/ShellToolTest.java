package com.example.agent.tools.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.agent.permission.PermissionDecision;
import com.example.agent.permission.PermissionManager;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolCategory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.util.List;

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

    // ---------- fix-jacoco-rule：补协议面与截断/超时分支 ----------

    @Test
    void protocolSurface() {
        var tool = new ShellTool(platformAdapter(), 5, 10000, false);

        assertEquals("Shell", tool.name());
        assertFalse(tool.description().isBlank());
        assertEquals(ToolCategory.SHELL, tool.category());
        assertTrue(tool.isDestructive(new ShellTool.Input("echo x")));
        assertEquals(List.of("command"), tool.inputSchema().get("required"));
        // shell 一律 ask（黑名单在 AgentLoop 层二次确认）
        assertEquals(
                PermissionDecision.ask(),
                tool.checkPermissions(new ShellTool.Input("echo x"), ctx()));
        assertTrue(tool.renderUse(new ShellTool.Input("echo x")).contains("echo x"));
        assertEquals("原样", tool.renderResult("原样"));
    }

    @Test
    void parseArgumentsReadsCommand() {
        var tool = new ShellTool(platformAdapter(), 5, 10000, false);
        assertEquals("echo hi", tool.parseArguments("{\"command\":\"echo hi\"}").command());
    }

    @Test
    void parseArgumentsRejectsMalformedJson() {
        var tool = new ShellTool(platformAdapter(), 5, 10000, false);
        assertThrows(
                IllegalArgumentException.class, () -> tool.parseArguments("{不是合法 JSON"));
    }

    @Test
    void truncatesOutputBeyondMaxOutputBytes() {
        // 输出上限设成 20 字节：echo 的内容必然超限，应写入截断标记并停止读取
        var tool = new ShellTool(platformAdapter(), 5, 20, false);
        String payload = "A".repeat(120);

        StepVerifier.create(tool.execute(new ShellTool.Input("echo " + payload), ctx()))
                .assertNext(
                        r -> {
                            assertFalse(r.isError());
                            assertTrue(
                                    r.output().contains("[truncated: output exceeded 20 bytes]"),
                                    () -> "期望截断标记，实际=" + r.output());
                        })
                .verifyComplete();
    }

    @Test
    void timeoutReportsTimeoutAndKillsProcess() {
        // timeoutSec=0 → waitFor 立即返回 false，必然走超时分支（并调用 killTree）
        var tool = new ShellTool(platformAdapter(), 0, 10000, false);
        String slow = windows() ? "ping -n 4 127.0.0.1" : "sleep 3";

        StepVerifier.create(tool.execute(new ShellTool.Input(slow), ctx()))
                .assertNext(
                        r -> {
                            assertTrue(r.isError());
                            assertTrue(
                                    r.toModelContent().contains("[TIMEOUT after 0s]"),
                                    () -> "期望超时标记，实际=" + r.toModelContent());
                        })
                .verifyComplete();
    }
}

