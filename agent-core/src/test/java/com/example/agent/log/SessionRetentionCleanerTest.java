package com.example.agent.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * SessionRetentionCleaner 测试：过期清理、数量上限、删除失败跳过。
 */
class SessionRetentionCleanerTest {
    @TempDir Path tmp;

    private Path sessionsRoot() throws Exception {
        return Files.createDirectories(tmp.resolve("sessions"));
    }

    private Path makeSession(String id) throws Exception {
        Path p = Files.createDirectories(sessionsRoot().resolve(id));
        Files.writeString(p.resolve("session.jsonl"), "{}\n");
        return p;
    }

    @Test
    void removesExpiredDirectories() throws Exception {
        Path root = sessionsRoot();
        Path old = makeSession("old-session");
        Path fresh = makeSession("fresh-session");
        // 把 old-session 的 mtime 改到 40 天前
        Files.setLastModifiedTime(
                old, java.nio.file.attribute.FileTime.from(Instant.now().minus(40, ChronoUnit.DAYS)));

        new SessionRetentionCleaner(root, 30, 50).clean();

        assertTrue(!Files.exists(old), "过期目录应被删除");
        assertTrue(Files.exists(fresh), "新目录应保留");
    }

    @Test
    void enforcesKeepSessionsLimit() throws Exception {
        Path root = sessionsRoot();
        for (int i = 0; i < 5; i++) {
            Path p = makeSession("sess-" + i);
            Files.setLastModifiedTime(
                    p,
                    java.nio.file.attribute.FileTime.from(
                            Instant.now().minus(i * 2, ChronoUnit.DAYS)));
        }
        new SessionRetentionCleaner(root, 30, 3).clean();

        long remaining = Files.list(root).count();
        assertEquals(3, remaining, "超上限应删最旧，剩余 3 个");
    }

    @Test
    void missingRootIsNoOp() {
        // 根目录不存在时静默跳过
        new SessionRetentionCleaner(tmp.resolve("nope"), 30, 50).clean();
    }
}
