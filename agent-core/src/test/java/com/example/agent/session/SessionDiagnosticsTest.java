package com.example.agent.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.llm.ToolCall;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link SessionDiagnostics}（improve-failure-observability）：把「手写脚本扫 JSONL」固化成一条可调用
 * 的诊断。报告存在「有 tool_calls 无 tool_result」的会话及其缺失 id。
 */
class SessionDiagnosticsTest {

    @TempDir Path tmp;

    private Path sessionsDir(Consumer<SessionStore>... fills) throws Exception {
        Path dir = tmp.resolve("sessions-" + System.nanoTime());
        Files.createDirectories(dir);
        int i = 0;
        for (Consumer<SessionStore> fill : fills) {
            SessionStore store = new SessionStore(dir.resolve("s" + (i++) + ".jsonl"), 50, 60_000);
            fill.accept(store);
            store.syncFlush();
            store.close();
        }
        return dir;
    }

    @Test
    void reportsDanglingToolCalls() throws Exception {
        // s0：悬挂（assistant 有 toolCalls，下一条直接是 user）——即 2026-09-13 事故的形状
        Path dir =
                sessionsDir(
                        store -> {
                            store.append(SessionEntry.user("存入长期记忆吧", null));
                            store.append(
                                    SessionEntry.assistant(
                                            "",
                                            List.of(
                                                    new ToolCall(
                                                            "call_edit",
                                                            "EditFile",
                                                            "{\"path\":\"MEMORY.md\"}")),
                                            null));
                            store.append(SessionEntry.user("怎么还是没有流式输出", null));
                        });

        SessionDiagnostics.Report report = SessionDiagnostics.scan(dir, Path.of("/tmp/logs"));

        assertThat(report.scannedSessions()).isEqualTo(1);
        assertThat(report.incomplete()).hasSize(1);
        assertThat(report.incomplete().get(0).sessionId()).isEqualTo("s0");
        assertThat(report.incomplete().get(0).danglingToolCallIds()).containsExactly("call_edit");
    }

    @Test
    void reportsNothingWhenAllSessionsArePaired() throws Exception {
        Path dir =
                sessionsDir(
                        store -> {
                            store.append(SessionEntry.user("读文件", null));
                            store.append(
                                    SessionEntry.assistant(
                                            "ok",
                                            List.of(new ToolCall("c1", "ReadFile", "{}")),
                                            null));
                            store.append(SessionEntry.toolResult("c1", "内容", false, null));
                        });

        SessionDiagnostics.Report report = SessionDiagnostics.scan(dir, Path.of("/tmp/logs"));

        assertThat(report.scannedSessions()).isEqualTo(1);
        assertThat(report.incomplete()).isEmpty();
    }

    @Test
    void scansAcrossMultipleSessionsAndReportsOnlyBrokenOnes() throws Exception {
        Path dir =
                sessionsDir(
                        store -> {
                            store.append(SessionEntry.user("u", null));
                            store.append(
                                    SessionEntry.assistant(
                                            "",
                                            List.of(new ToolCall("bad", "Ls", "{}")),
                                            null));
                        },
                        store -> {
                            store.append(SessionEntry.user("u", null));
                            store.append(
                                    SessionEntry.assistant(
                                            "",
                                            List.of(new ToolCall("good", "Ls", "{}")),
                                            null));
                            store.append(SessionEntry.toolResult("good", "ok", false, null));
                        });

        SessionDiagnostics.Report report = SessionDiagnostics.scan(dir, Path.of("/tmp/logs"));

        assertThat(report.scannedSessions()).isEqualTo(2);
        assertThat(report.incomplete()).hasSize(1);
        assertThat(report.incomplete().get(0).danglingToolCallIds()).containsExactly("bad");
    }

    @Test
    void missingDirectoryYieldsEmptyReport() {
        SessionDiagnostics.Report report =
                SessionDiagnostics.scan(tmp.resolve("nope"), tmp.resolve("logs"));

        assertThat(report.scannedSessions()).isZero();
        assertThat(report.incomplete()).isEmpty();
    }
}
