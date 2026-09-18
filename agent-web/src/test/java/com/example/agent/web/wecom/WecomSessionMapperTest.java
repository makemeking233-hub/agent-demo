package com.example.agent.web.wecom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * WecomSessionMapper 测试（add-wecom-channel task 4.2）。
 *
 * <p>验证 userId → sessionId 1:1 映射；持久化到 {@code wecom/sessions.json} 后 reload
 * 仍能恢复。
 */
class WecomSessionMapperTest {

    @Test
    void getOrCreateReturnsConsistentSessionId(@TempDir Path tmp) {
        WecomSessionMapper mapper = new WecomSessionMapper(tmp.resolve("wecom"));
        String s1 = mapper.getOrCreate("user123");
        String s2 = mapper.getOrCreate("user123");
        assertEquals(s1, s2);
        assertEquals("wecom:user123", s1, "sessionId 默认带 wecom: 前缀");
    }

    @Test
    void differentUsersGetDifferentSessions(@TempDir Path tmp) {
        WecomSessionMapper mapper = new WecomSessionMapper(tmp.resolve("wecom"));
        assertEquals("wecom:alice", mapper.getOrCreate("alice"));
        assertEquals("wecom:bob", mapper.getOrCreate("bob"));
    }

    @Test
    void persistenceRoundtrip(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("wecom");
        WecomSessionMapper m1 = new WecomSessionMapper(dir);
        m1.getOrCreate("user123");
        m1.getOrCreate("user456");

        // 验证文件存在
        Path file = dir.resolve("sessions.json");
        assertTrue(Files.exists(file));

        // 重新构造（模拟重启）→ 映射应恢复
        WecomSessionMapper m2 = new WecomSessionMapper(dir);
        assertEquals("wecom:user123", m2.findSessionId("user123").orElseThrow());
        assertEquals("wecom:user456", m2.findSessionId("user456").orElseThrow());
        // 拿回同样的 sessionId
        assertEquals("wecom:user123", m2.getOrCreate("user123"));
    }

    @Test
    void emptyDirReturnsEmptyForUnknownUser(@TempDir Path tmp) {
        WecomSessionMapper mapper = new WecomSessionMapper(tmp.resolve("wecom"));
        assertTrue(mapper.findSessionId("nobody").isEmpty());
        // getOrCreate 仍然会建一个
        assertEquals("wecom:nobody", mapper.getOrCreate("nobody"));
    }

    @Test
    void concurrentGetOrCreateSameUserReturnsSameSessionId(@TempDir Path tmp) throws Exception {
        WecomSessionMapper mapper = new WecomSessionMapper(tmp.resolve("wecom"));
        int threadCount = 16;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                latch.await();
                return mapper.getOrCreate("hot-user");
            }));
        }
        latch.countDown();
        java.util.Set<String> distinct = new java.util.HashSet<>();
        for (var f : futures) {
            distinct.add(f.get());
        }
        pool.shutdown();
        assertEquals(1, distinct.size(), "16 个并发线程对同一 userId 应返回同一 sessionId");
        assertTrue(distinct.contains("wecom:hot-user"));
    }
}
