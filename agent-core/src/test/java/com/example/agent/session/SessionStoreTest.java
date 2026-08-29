package com.example.agent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class SessionStoreTest {
    @TempDir Path tmp;

    @Test
    void writesAndSyncs() throws Exception {
        var store = new SessionStore(tmp.resolve("test.jsonl"), 50, 200);
        store.append(SessionEntry.user("hello", null));
        store.syncFlush();
        store.close();
        List<String> lines = Files.readAllLines(tmp.resolve("test.jsonl"));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("hello"));
    }

    @Test
    void asyncFlushBySize() throws Exception {
        var store = new SessionStore(tmp.resolve("test.jsonl"), 3, 60_000);
        for (int i = 0; i < 5; i++) store.append(SessionEntry.user("msg-" + i, null));
        Thread.sleep(500);
        store.close();
        assertTrue(Files.size(tmp.resolve("test.jsonl")) > 0);
    }

    @Test
    void dedupeOffset() throws Exception {
        // sync flush 期间批量 flush 同时触发，验证同一 entry 只写一次
        var store = new SessionStore(tmp.resolve("test.jsonl"), 2, 60_000);
        for (int i = 0; i < 4; i++) store.append(SessionEntry.user("msg-" + i, null));
        store.syncFlush();
        store.close();
        // 4 条 entry 都该写入（sync 全部 flush）
        List<String> lines = Files.readAllLines(tmp.resolve("test.jsonl"));
        assertEquals(4, lines.size());
    }

    @Test
    void loadLatestReturnsMostRecentFile() throws Exception {
        // 创建两个 session 文件，第二个 mtime 更新
        Path sessionsDir = tmp.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Path older = sessionsDir.resolve("old-session.jsonl");
        Path newer = sessionsDir.resolve("new-session.jsonl");
        writeEntries(older, SessionEntry.user("older", null));
        writeEntries(newer, SessionEntry.user("newer1", null), SessionEntry.user("newer2", null));
        // 确保 newer 的 mtime 比 older 晚（setLastModifiedTime 防止系统时间分辨率问题）
        Files.setLastModifiedTime(older, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 60_000));
        Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));

        List<SessionEntry> entries = SessionStore.loadLatest(sessionsDir);

        assertEquals(2, entries.size());
        assertEquals("newer1", entries.get(0).content());
        assertEquals("newer2", entries.get(1).content());
    }

    @Test
    void loadLatestReturnsEmptyListWhenNoFiles() throws Exception {
        Path sessionsDir = tmp.resolve("empty-sessions");
        Files.createDirectories(sessionsDir);

        List<SessionEntry> entries = SessionStore.loadLatest(sessionsDir);

        assertTrue(entries.isEmpty());
    }

    @Test
    void loadLatestReturnsEmptyListWhenDirMissing() throws Exception {
        Path sessionsDir = tmp.resolve("does-not-exist");

        List<SessionEntry> entries = SessionStore.loadLatest(sessionsDir);

        assertTrue(entries.isEmpty());
    }

    private static void writeEntries(Path file, SessionEntry... entries) throws Exception {
        var store = new SessionStore(file, 50, 60_000);
        for (SessionEntry e : entries) store.append(e);
        store.syncFlush();
        store.close();
    }
}
