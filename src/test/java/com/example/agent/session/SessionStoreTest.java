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
}
