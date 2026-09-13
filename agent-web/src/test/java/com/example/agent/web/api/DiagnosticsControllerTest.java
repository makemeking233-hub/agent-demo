package com.example.agent.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.llm.ToolCall;
import com.example.agent.session.SessionEntry;
import com.example.agent.session.SessionStore;
import com.example.agent.web.api.dto.DiagnosticsDto;
import com.example.agent.web.stream.WebAgentRuntime;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DiagnosticsController}（improve-failure-observability）：一条请求就能看到哪个会话记录不完整，
 * 替代此前手写脚本扫 JSONL。
 */
class DiagnosticsControllerTest {

    @TempDir Path tmp;

    private DiagnosticsController controller(Path sessionsDir) {
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.sessionsDirFor(any())).thenReturn(sessionsDir);
        return new DiagnosticsController(runtime);
    }

    @Test
    void reportsIncompleteSessions() throws Exception {
        SessionStore store = new SessionStore(tmp.resolve("sess-broken.jsonl"), 50, 60_000);
        store.append(SessionEntry.user("存入长期记忆吧", null));
        store.append(
                SessionEntry.assistant(
                        "",
                        List.of(new ToolCall("call_edit", "EditFile", "{\"path\":\"MEMORY.md\"}")),
                        null));
        store.append(SessionEntry.user("怎么还是没有流式输出", null));
        store.syncFlush();
        store.close();

        DiagnosticsDto dto = controller(tmp).get(null);

        assertThat(dto.scannedSessions()).isEqualTo(1);
        assertThat(dto.incompleteSessions()).hasSize(1);
        assertThat(dto.incompleteSessions().get(0).sessionId()).isEqualTo("sess-broken");
        assertThat(dto.incompleteSessions().get(0).danglingToolCallIds())
                .containsExactly("call_edit");
        assertThat(dto.incompleteSessions().get(0).sizeBytes()).isPositive();
        // 报告里带上日志根，排查时可直接过去看日志
        assertThat(dto.logsDir()).contains(".agent-demo");
    }

    @Test
    void emptyDirectoryYieldsEmptyReport() {
        DiagnosticsDto dto = controller(tmp.resolve("nope")).get(null);

        assertThat(dto.scannedSessions()).isZero();
        assertThat(dto.incompleteSessions()).isEmpty();
    }

    @Test
    void nullReportYieldsEmptyDto() {
        // 核心层返回 null 时不应 NPE（诊断端点自身不能成为故障源）
        DiagnosticsDto dto = DiagnosticsDto.from(null);

        assertThat(dto.incompleteSessions()).isEmpty();
        assertThat(dto.scannedSessions()).isZero();
        assertThat(dto.sessionsDir()).isNull();
    }
}
