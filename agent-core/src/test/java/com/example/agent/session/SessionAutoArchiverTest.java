package com.example.agent.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link SessionAutoArchiver}（auto-archive-stale-sessions）：超期归档的判定、跳过与健壮性。
 *
 * <p>用显式 {@code nowMillis} + 手工设置文件 mtime 精确构造边界，不依赖真实时钟。
 */
class SessionAutoArchiverTest {

    @TempDir Path tmp;

    private static final long NOW = Instant.parse("2026-09-13T12:00:00Z").toEpochMilli();
    private static final Duration WEEK = Duration.ofDays(7);

    /** 造一个会话文件并把 mtime 设为 daysAgo 天前。 */
    private Path session(Path dir, String id, long daysAgo) throws IOException {
        Files.createDirectories(dir);
        Path f = dir.resolve(id + ".jsonl");
        Files.writeString(f, "{\"type\":\"user\",\"content\":\"hi\"}\n");
        Files.setLastModifiedTime(
                f, FileTime.fromMillis(NOW - Duration.ofDays(daysAgo).toMillis()));
        return f;
    }

    private boolean archivedExists(Path dir, String id) {
        return Files.isRegularFile(dir.resolve(".archive").resolve(id + ".jsonl"));
    }

    private boolean liveExists(Path dir, String id) {
        return Files.isRegularFile(dir.resolve(id + ".jsonl"));
    }

    @Test
    void archivesSessionsOlderThanRetention() throws Exception {
        Path dir = tmp.resolve("sessions");
        session(dir, "stale", 8);
        session(dir, "fresh", 3);

        SessionAutoArchiver.Result r =
                SessionAutoArchiver.archiveStale(dir, WEEK, NOW, id -> false);

        assertThat(r.scanned()).isEqualTo(2);
        assertThat(r.archived()).containsExactly("stale");
        assertThat(archivedExists(dir, "stale")).isTrue();
        assertThat(liveExists(dir, "stale")).isFalse();
        // 未超期的保持原位
        assertThat(liveExists(dir, "fresh")).isTrue();
        assertThat(archivedExists(dir, "fresh")).isFalse();
    }

    @Test
    void exactlyAtCutoffIsNotArchived() throws Exception {
        Path dir = tmp.resolve("sessions");
        session(dir, "border", 7);

        SessionAutoArchiver.Result r =
                SessionAutoArchiver.archiveStale(dir, WEEK, NOW, id -> false);

        assertThat(r.archived()).isEmpty();
        assertThat(liveExists(dir, "border")).isTrue();
    }

    @Test
    void skipsSessionsWithActiveStream() throws Exception {
        Path dir = tmp.resolve("sessions");
        session(dir, "busy", 30);
        session(dir, "idle", 30);

        SessionAutoArchiver.Result r =
                SessionAutoArchiver.archiveStale(dir, WEEK, NOW, id -> "busy".equals(id));

        assertThat(r.archived()).containsExactly("idle");
        assertThat(r.skippedActive()).containsExactly("busy");
        // 活动中的会话必须原地不动，否则其后续写入会落到旧路径
        assertThat(liveExists(dir, "busy")).isTrue();
    }

    @Test
    void movesSidecarMetaAlongWithSession() throws Exception {
        Path dir = tmp.resolve("sessions");
        session(dir, "s1", 10);
        Files.writeString(dir.resolve("s1.meta.json"), "{\"title\":\"旧会话\"}");

        SessionAutoArchiver.archiveStale(dir, WEEK, NOW, id -> false);

        assertThat(Files.isRegularFile(dir.resolve(".archive").resolve("s1.meta.json"))).isTrue();
        assertThat(Files.exists(dir.resolve("s1.meta.json"))).isFalse();
    }

    @Test
    void isIdempotentAcrossRuns() throws Exception {
        Path dir = tmp.resolve("sessions");
        session(dir, "stale", 10);

        SessionAutoArchiver.Result first =
                SessionAutoArchiver.archiveStale(dir, WEEK, NOW, id -> false);
        SessionAutoArchiver.Result second =
                SessionAutoArchiver.archiveStale(dir, WEEK, NOW, id -> false);

        assertThat(first.archived()).containsExactly("stale");
        // 第二次扫描不到它了（已不在 sessions/ 下）
        assertThat(second.scanned()).isZero();
        assertThat(second.archived()).isEmpty();
    }

    @Test
    void nullRetentionOrMissingDirIsNoop() {
        assertThat(SessionAutoArchiver.archiveStale(tmp.resolve("nope"), WEEK, NOW, id -> false))
                .isEqualTo(new SessionAutoArchiver.Result(0, java.util.List.of(), java.util.List.of(), java.util.List.of()));
        assertThat(SessionAutoArchiver.archiveStale(tmp, null, NOW, id -> false).scanned())
                .isZero();
        assertThat(
                        SessionAutoArchiver.archiveStale(tmp, Duration.ZERO, NOW, id -> false)
                                .scanned())
                .isZero();
    }

    @Test
    void listsSessionFilesWithMtimeAndSize() throws Exception {
        Path dir = tmp.resolve("sessions");
        session(dir, "old", 10);
        session(dir, "new", 1);

        var files = SessionStore.listSessionFiles(dir);

        assertThat(files).extracting(SessionStore.SessionFile::id).containsExactly("new", "old");
        assertThat(files.get(0).sizeBytes()).isPositive();
        assertThat(files.get(0).lastModifiedMillis())
                .isGreaterThan(files.get(1).lastModifiedMillis());
    }

    @Test
    void listsOnlyJsonlNotMetaFiles() throws Exception {
        Path dir = tmp.resolve("sessions");
        session(dir, "s1", 1);
        Files.writeString(dir.resolve("s1.meta.json"), "{}");

        assertThat(SessionStore.listSessionFiles(dir))
                .extracting(SessionStore.SessionFile::id)
                .containsExactly("s1");
    }

    @Test
    void archivedFilesAreListedFromArchiveDir() throws Exception {
        Path dir = tmp.resolve("sessions");
        session(dir, "s1", 10);
        SessionAutoArchiver.archiveStale(dir, WEEK, NOW, id -> false);

        assertThat(SessionStore.listArchivedFiles(dir))
                .extracting(SessionStore.SessionFile::id)
                .containsExactly("s1");
        assertThat(SessionStore.listSessionFiles(dir)).isEmpty();
    }

    @Test
    void handlesManySessionsWithoutLosingAny() throws Exception {
        Path dir = tmp.resolve("sessions");
        for (int i = 0; i < 5; i++) session(dir, "s" + i, 10 + i);

        SessionAutoArchiver.Result r =
                SessionAutoArchiver.archiveStale(dir, WEEK, NOW, id -> false);

        assertThat(Set.copyOf(r.archived())).hasSize(5);
        assertThat(SessionStore.listArchivedFiles(dir)).hasSize(5);
    }
}
