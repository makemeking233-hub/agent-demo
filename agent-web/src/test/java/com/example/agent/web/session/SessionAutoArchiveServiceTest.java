package com.example.agent.web.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.agent.session.SessionStore;
import com.example.agent.web.stream.ChatStreamService;
import com.example.agent.web.stream.WebAgentRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link SessionAutoArchiveService}（auto-archive-stale-sessions）：开关、保留期、跳过活动会话。
 *
 * <p>用真实文件系统构造会话（mtime 由测试控制），只 mock 运行时与活动流。
 */
class SessionAutoArchiveServiceTest {

    @TempDir Path tmp;

    private Path sessionsDir;

    /** 造 sessions 目录并写入一个 daysAgo 天前活动过的会话。 */
    private void session(String id, long daysAgo) throws Exception {
        Files.createDirectories(sessionsDir);
        Path f = sessionsDir.resolve(id + ".jsonl");
        Files.writeString(f, "{\"type\":\"user\",\"content\":\"hi\"}\n");
        Files.setLastModifiedTime(
                f,
                FileTime.fromMillis(
                        Instant.now().minus(Duration.ofDays(daysAgo)).toEpochMilli()));
    }

    private SessionAutoArchiveService service(
            boolean enabled, int afterDays, boolean sessionActive) {
        sessionsDir = tmp.resolve("sessions");
        WebAgentRuntime runtime = mock(WebAgentRuntime.class);
        when(runtime.agentDataDir()).thenReturn(tmp);
        ChatStreamService streams = mock(ChatStreamService.class);
        // 裸 any()：调用方可能传 null
        when(streams.isSessionActive(any())).thenReturn(sessionActive);
        return new SessionAutoArchiveService(runtime, streams, enabled, afterDays);
    }

    private boolean archived(String id) {
        return Files.isRegularFile(sessionsDir.resolve(".archive").resolve(id + ".jsonl"));
    }

    @Test
    void archivesSessionsIdleLongerThanRetention() throws Exception {
        SessionAutoArchiveService svc = service(true, 7, false);
        session("stale", 10);
        session("fresh", 2);

        int archived = svc.archiveAllWorkspaces();

        assertThat(archived).isEqualTo(1);
        assertThat(archived("stale")).isTrue();
        assertThat(archived("fresh")).isFalse();
        assertThat(Files.isRegularFile(sessionsDir.resolve("fresh.jsonl"))).isTrue();
    }

    @Test
    void disabledDoesNothing() throws Exception {
        SessionAutoArchiveService svc = service(false, 7, false);
        session("stale", 30);

        assertThat(svc.archiveAllWorkspaces()).isZero();
        assertThat(Files.isRegularFile(sessionsDir.resolve("stale.jsonl"))).isTrue();
        assertThat(archived("stale")).isFalse();
    }

    @Test
    void skipsActiveSessions() throws Exception {
        SessionAutoArchiveService svc = service(true, 7, true);
        session("busy", 30);

        assertThat(svc.archiveAllWorkspaces()).isZero();
        // 活动中的会话必须原地不动
        assertThat(Files.isRegularFile(sessionsDir.resolve("busy.jsonl"))).isTrue();
    }

    @Test
    void retentionIsConfigurable() throws Exception {
        SessionAutoArchiveService svc = service(true, 30, false);
        session("tenDays", 10);
        session("fortyDays", 40);

        assertThat(svc.archiveAllWorkspaces()).isEqualTo(1);
        assertThat(archived("fortyDays")).isTrue();
        assertThat(archived("tenDays")).isFalse();
    }

    @Test
    void invalidRetentionSkipsRun() throws Exception {
        SessionAutoArchiveService svc = service(true, 0, false);
        session("stale", 100);

        assertThat(svc.archiveAllWorkspaces()).isZero();
        assertThat(Files.isRegularFile(sessionsDir.resolve("stale.jsonl"))).isTrue();
    }

    @Test
    void runIsIdempotent() throws Exception {
        SessionAutoArchiveService svc = service(true, 7, false);
        session("stale", 30);

        assertThat(svc.archiveAllWorkspaces()).isEqualTo(1);
        // 第二次已经没有可归档的了
        assertThat(svc.archiveAllWorkspaces()).isZero();
        assertThat(SessionStore.listArchivedFiles(sessionsDir)).hasSize(1);
    }

    @Test
    void startupHookRunsArchive() throws Exception {
        SessionAutoArchiveService svc = service(true, 7, false);
        session("stale", 30);

        svc.onStartup();

        assertThat(archived("stale")).isTrue();
    }

    @Test
    void scheduledHookRunsArchive() throws Exception {
        SessionAutoArchiveService svc = service(true, 7, false);
        session("stale", 30);

        svc.scheduled();

        assertThat(archived("stale")).isTrue();
    }

    @Test
    void noSessionsDirectoryIsHarmless() {
        SessionAutoArchiveService svc = service(true, 7, false);
        // 目录不存在时不应抛错
        assertThat(svc.archiveAllWorkspaces()).isZero();
    }
}
