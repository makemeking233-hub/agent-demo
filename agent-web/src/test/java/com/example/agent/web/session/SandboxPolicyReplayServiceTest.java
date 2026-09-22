package com.example.agent.web.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * SandboxPolicyReplayService 单元测试（rewrite-permission-mode-dsh T9.6；Q4 决策）。
 *
 * <p>覆盖 {@link SandboxPolicyReplayService#findUnrecoveredEscalate} 的 jsonl 解析逻辑：
 * escalate 无对应 turn_end_restore → 报告；有对应 → 不报告；无 escalate → 不报告。
 */
class SandboxPolicyReplayServiceTest {

    private static String line(String reason, String from, String to) {
        return "{\"type\":\"sandbox/mode\",\"stream_id\":\"s\",\"from_mode\":\"" + from
                + "\",\"to_mode\":\"" + to + "\",\"reason\":\"" + reason + "\",\"ts\":1}";
    }

    @Test
    void escalateWithoutRestoreIsReported(@TempDir Path tmp) throws Exception {
        Path jsonl = tmp.resolve("s1.jsonl");
        Files.write(jsonl, List.of(
                line("initial", "", "plan"),
                line("escalate", "plan", "danger-full")));
        var result = SandboxPolicyReplayService.findUnrecoveredEscalate(jsonl);
        assertTrue(result.isPresent(), "escalate 无 restore 应报告");
        assertEquals("danger-full", result.get());
    }

    @Test
    void escalateWithRestoreIsNotReported(@TempDir Path tmp) throws Exception {
        Path jsonl = tmp.resolve("s2.jsonl");
        Files.write(jsonl, List.of(
                line("initial", "", "plan"),
                line("escalate", "plan", "danger-full"),
                line("turn_end_restore", "danger-full", "plan")));
        var result = SandboxPolicyReplayService.findUnrecoveredEscalate(jsonl);
        assertFalse(result.isPresent(), "escalate 有 restore 不应报告");
    }

    @Test
    void noEscalateAtAllIsNotReported(@TempDir Path tmp) throws Exception {
        Path jsonl = tmp.resolve("s3.jsonl");
        Files.write(jsonl, List.of(
                line("initial", "", "plan"),
                line("user_set", "plan", "ask")));
        var result = SandboxPolicyReplayService.findUnrecoveredEscalate(jsonl);
        assertFalse(result.isPresent(), "无 escalate 不应报告");
    }

    @Test
    void multipleEscalatesLastOneWins(@TempDir Path tmp) throws Exception {
        Path jsonl = tmp.resolve("s4.jsonl");
        Files.write(jsonl, List.of(
                line("escalate", "plan", "ask"),
                line("turn_end_restore", "ask", "plan"),
                line("escalate", "plan", "danger-full")));
        var result = SandboxPolicyReplayService.findUnrecoveredEscalate(jsonl);
        assertTrue(result.isPresent());
        assertEquals("danger-full", result.get(), "最后一次 escalate 生效");
    }

    @Test
    void escalateRestoredThenUserSetNotReported(@TempDir Path tmp) throws Exception {
        Path jsonl = tmp.resolve("s5.jsonl");
        Files.write(jsonl, List.of(
                line("escalate", "plan", "danger-full"),
                line("turn_end_restore", "danger-full", "plan"),
                line("user_set", "plan", "ask")));
        var result = SandboxPolicyReplayService.findUnrecoveredEscalate(jsonl);
        assertFalse(result.isPresent(), "restore 已抵消 escalate; 后续 user_set 不影响");
    }

    @Test
    void malformedLinesAreSkipped(@TempDir Path tmp) throws Exception {
        Path jsonl = tmp.resolve("s6.jsonl");
        Files.write(jsonl, List.of(
                "{not json",
                "",
                line("escalate", "plan", "danger-full"),
                "{\"type\":\"other/event\"}"));
        var result = SandboxPolicyReplayService.findUnrecoveredEscalate(jsonl);
        assertTrue(result.isPresent(), "坏行跳过, escalate 仍被识别");
    }

    @Test
    void missingFileReturnsEmpty(@TempDir Path tmp) {
        assertFalse(SandboxPolicyReplayService.findUnrecoveredEscalate(tmp.resolve("nope.jsonl")).isPresent());
    }

    @Test
    void nullPathReturnsEmpty() {
        assertFalse(SandboxPolicyReplayService.findUnrecoveredEscalate(null).isPresent());
    }

    @Test
    void userSetDoesNotClearPendingEscalate(@TempDir Path tmp) throws Exception {
        Path jsonl = tmp.resolve("s7.jsonl");
        Files.write(jsonl, List.of(
                line("escalate", "plan", "danger-full"),
                line("user_set", "danger-full", "ask")));
        var result = SandboxPolicyReplayService.findUnrecoveredEscalate(jsonl);
        // user_set 不参与 escalate 配对, 故 pending 仍是 danger-full
        assertTrue(result.isPresent());
        assertEquals("danger-full", result.get());
    }
}
