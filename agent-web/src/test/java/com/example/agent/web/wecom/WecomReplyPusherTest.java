package com.example.agent.web.wecom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * WecomReplyPusher 测试（add-wecom-channel task 5.2）。
 *
 * <p>用 {@link FakeClient} 记录发送历史（不依赖 WireMock）。
 * 时间相关逻辑通过 {@code now} 字段手动推进，避免依赖 Thread.sleep。
 */
class WecomReplyPusherTest {

    private FakeClient client;
    private WecomReplyPusher pusher;

    @BeforeEach
    void setUp() {
        client = new FakeClient();
        WecomConfigProperties.Reply reply = new WecomConfigProperties.Reply(
                3000L,    // flush-interval-ms
                500,      // flush-min-chars
                4000,     // max-chars
                30000L);  // retry-backoff-ms
        pusher = new WecomReplyPusher(client, reply, () -> currentTimeMillis);
    }

    @AfterEach
    void tearDown() {
        pusher.shutdown();
    }

    private long currentTimeMillis = 1_000_000L;

    @Test
    void accumulateBelowThresholdFlushesAfterInterval() {
        pusher.append("user1", "今天");
        pusher.append("user1", "天气");
        pusher.append("user1", "晴"); // total 5 chars, < 500
        // 时间推进到 flush-interval-ms 后
        currentTimeMillis += 3001L;
        pusher.tick();
        assertEquals(1, client.sends.size());
        assertEquals("user1", client.sends.get(0).userId);
        assertEquals("今天天气晴", client.sends.get(0).content);
    }

    @Test
    void exceedingMinCharsTriggersImmediateFlush() {
        String big = "a".repeat(500);
        pusher.append("user2", big);
        // 立即 flush（不需等 interval）
        assertEquals(1, client.sends.size());
        assertEquals(big, client.sends.get(0).content);
    }

    @Test
    void flushingTwiceSendsTwice() {
        String chunk1 = "a".repeat(500);
        String chunk2 = "b".repeat(500);
        pusher.append("user3", chunk1);
        pusher.append("user3", chunk2);
        assertEquals(2, client.sends.size());
        assertEquals(chunk1, client.sends.get(0).content);
        assertEquals(chunk2, client.sends.get(1).content);
    }

    @Test
    void maxCharsTruncatesAndSplitsIntoTwoMessages() {
        String long1 = "a".repeat(4000);
        String long2 = "b".repeat(2000);
        pusher.append("user4", long1);
        pusher.append("user4", long2);
        // long1 触发 flush（>=500）；long2 累加直到 >=500 chars 触发
        assertTrue(client.sends.size() >= 2);
        // 第一条正好 4000（== maxChars，全部发送）
        assertEquals(4000, client.sends.get(0).content.length());
    }

    @Test
    void rateLimitTriggersBackoffAndMergesContent() {
        client.failNextWith45009(); // 下一次 send 抛 RetryableWecomException
        pusher.append("user5", "a".repeat(500)); // 触发 flush
        // 此时应进入 backoff，发送内容留在 buffer
        assertEquals(0, client.sends.size(), "backoff 期间不发送（counts only successful）");

        // backoff 期间继续 append → 累计到 buffer
        pusher.append("user5", "b".repeat(500));

        // backoff 到期（30000ms 后）
        currentTimeMillis += 30001L;
        pusher.tick();

        // 应该 send 一次（合并两次 500 chars = 1000 chars）
        assertEquals(1, client.sends.size());
        assertEquals(1000, client.sends.get(0).content.length());
    }

    @Test
    void differentUsersAreIsolated() {
        pusher.append("uA", "a".repeat(500));
        pusher.append("uB", "b".repeat(500));
        assertEquals(2, client.sends.size());
        assertEquals("uA", client.sends.get(0).userId);
        assertEquals("uB", client.sends.get(1).userId);
    }

    @Test
    void idleTickDoesNothing() {
        // append 后尚未到 flush-interval-ms（< 3000ms）→ tick 不 flush
        pusher.append("userQ", "small");
        currentTimeMillis += 500L; // < flushIntervalMs(3000)
        pusher.tick();
        assertEquals(0, client.sends.size());
    }

    @Test
    void secondImmediateFlushAfterBackoffWorks() {
        client.failNextWith45009();
        pusher.append("userR", "a".repeat(500));
        assertEquals(0, client.sends.size());
        currentTimeMillis += 30001L;
        pusher.tick(); // backoff 到期 → 重发
        assertEquals(1, client.sends.size());

        // 第二次 append 不在 backoff，应正常发送
        pusher.append("userR", "c".repeat(500));
        assertEquals(2, client.sends.size());
    }

    /** 测试替身：WecomMessageSender 简化版（不依赖 WireMock） */
    static class FakeClient implements WecomMessageSender {
        final List<Send> sends = new ArrayList<>();
        private final AtomicInteger rateLimitHits = new AtomicInteger();

        void failNextWith45009() {
            rateLimitHits.incrementAndGet();
        }

        @Override
        public String sendMarkdown(String userId, String content) {
            if (rateLimitHits.getAndDecrement() > 0) {
                throw new RetryableWecomException("wecom sendMarkdown 限频：errcode=45009");
            }
            sends.add(new Send(userId, content));
            return "MID-" + sends.size();
        }

        @Override
        public String getAccessToken() {
            return "T-FIXED";
        }

        record Send(String userId, String content) {}
    }
}
