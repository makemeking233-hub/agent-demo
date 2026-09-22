package com.example.agent.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.agent.session.MessageFeedbackStore.FeedbackItem;
import com.example.agent.session.MessageFeedbackStore.FeedbackSnapshot;
import com.example.agent.session.MessageFeedbackStore.MessageFeedbackVersionConflict;
import com.example.agent.session.MessageFeedbackStore.Rating;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * MessageFeedbackStore 单元测试（add-message-feedback F1.5）。
 *
 * <p>严格使用 {@code @TempDir}（全局规则 §10：不写真实 {@code ~/.agent-demo/feedback/}）。
 */
class MessageFeedbackStoreTest {

    @TempDir Path tmp;

    private MessageFeedbackStore store() {
        return new MessageFeedbackStore(tmp);
    }

    private static String u(String s) {
        return "u-" + s;
    }

    @Test
    void putCreatesWithVersionOne() {
        MessageFeedbackStore s = store();
        FeedbackItem item = s.put("s-1", u("a"), Rating.UP, null);
        assertThat(item.rating()).isEqualTo(Rating.UP);
        assertThat(item.version()).isEqualTo(1L);
        assertThat(item.updatedAt()).isPositive();
    }

    @Test
    void putDuplicateWithoutIfVersionConflicts() {
        MessageFeedbackStore s = store();
        s.put("s-1", u("a"), Rating.UP, null);
        assertThatThrownBy(() -> s.put("s-1", u("a"), Rating.UP, null))
                .isInstanceOf(MessageFeedbackVersionConflict.class)
                .extracting(t -> ((MessageFeedbackVersionConflict) t).current())
                .isNotNull();
    }

    @Test
    void putWithMatchingIfVersionSucceedsAndBumpsVersion() {
        MessageFeedbackStore s = store();
        FeedbackItem first = s.put("s-1", u("a"), Rating.UP, null);
        FeedbackItem second = s.put("s-1", u("a"), Rating.DOWN, first.version());
        assertThat(second.version()).isEqualTo(2L);
        assertThat(second.rating()).isEqualTo(Rating.DOWN);
    }

    @Test
    void putWithMismatchedIfVersionConflicts() {
        MessageFeedbackStore s = store();
        FeedbackItem first = s.put("s-1", u("a"), Rating.UP, null);
        assertThatThrownBy(() -> s.put("s-1", u("a"), Rating.DOWN, 99L))
                .isInstanceOf(MessageFeedbackVersionConflict.class)
                .extracting(t -> ((MessageFeedbackVersionConflict) t).current())
                .extracting(FeedbackItem::version)
                .isEqualTo(first.version());
    }

    @Test
    void deleteWithMatchingVersionRemoves() {
        MessageFeedbackStore s = store();
        FeedbackItem first = s.put("s-1", u("a"), Rating.UP, null);
        s.delete("s-1", u("a"), first.version());
        assertThat(s.get("s-1", u("a"))).isNull();
    }

    @Test
    void deleteAbsentThrowsWithNullCurrent() {
        MessageFeedbackStore s = store();
        assertThatThrownBy(() -> s.delete("s-1", u("nope"), null))
                .isInstanceOf(MessageFeedbackVersionConflict.class)
                .extracting(t -> ((MessageFeedbackVersionConflict) t).current())
                .isEqualTo(null);
    }

    @Test
    void getAllListsItemsSortedByVersionDescending() {
        MessageFeedbackStore s = store();
        s.put("s-1", u("a"), Rating.UP, null);
        s.put("s-1", u("b"), Rating.UP, null);
        s.put("s-1", u("a"), Rating.DOWN, 1L); // a 升到 v2
        FeedbackSnapshot snap = s.getAll("s-1");
        assertThat(snap.sessionId()).isEqualTo("s-1");
        assertThat(snap.items()).containsOnlyKeys(u("a"), u("b"));
        // a 先于 b（v2 > v1）
        assertThat(snap.items()).containsExactly(
                java.util.Map.entry(u("a"), new FeedbackItem(Rating.DOWN, 2L, snap.items().get(u("a")).updatedAt())),
                java.util.Map.entry(u("b"), new FeedbackItem(Rating.UP, 1L, snap.items().get(u("b")).updatedAt())));
    }

    @Test
    void rejectsInvalidIds() {
        MessageFeedbackStore s = store();
        assertThatThrownBy(() -> s.put("../escape", u("a"), Rating.UP, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.put("s-1", "../escape", Rating.UP, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.put("", u("a"), Rating.UP, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.put(null, u("a"), Rating.UP, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRating() {
        MessageFeedbackStore s = store();
        assertThatThrownBy(() -> s.put("s-1", u("a"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAllForUnknownSessionReturnsEmpty() {
        MessageFeedbackStore s = store();
        FeedbackSnapshot snap = s.getAll("nope");
        assertThat(snap.items()).isEmpty();
    }

    @Test
    void writesAreSerializedByFileLock() throws Exception {
        // 多线程并发 PUT 同一 messageId：后者拿到前者的 version → 409 冲突
        // （§feedback storage 并发安全 场景）
        final MessageFeedbackStore s = store();
        final int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> unexpected = new AtomicReference<>();
            java.util.concurrent.atomic.AtomicInteger conflicts = new java.util.concurrent.atomic.AtomicInteger();
            Thread[] workers = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                workers[i] = new Thread(() -> {
                    try {
                        start.await();
                        s.put("s-1", u("shared"), Rating.UP, null);
                    } catch (MessageFeedbackVersionConflict conflict) {
                        // 期望：除第一个赢的线程外，其它都拿到 CAS 冲突
                        conflicts.incrementAndGet();
                    } catch (Throwable t) {
                        unexpected.compareAndSet(null, t);
                    }
                }, "fb-put-" + i);
                workers[i].start();
            }
            start.countDown();
            for (Thread w : workers) w.join(5000);
            assertThat(unexpected.get()).as("unexpected exception").isNull();
            assertThat(conflicts.get()).as("CAS conflicts from losing threads").isEqualTo(threads - 1);
            FeedbackItem finalItem = s.get("s-1", u("shared"));
            assertThat(finalItem).isNotNull();
            assertThat(finalItem.version()).isEqualTo(1L); // 只有第一个赢
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void writesAreSerializedByFileLockAcrossPool() throws Exception {
        // 大量并发 PUT 不同 messageId：全部成功（无 CAS 冲突，因为各自 message 不同）
        final MessageFeedbackStore s = store();
        final int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        s.put("s-1", u("msg-" + idx), Rating.UP, null);
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            FeedbackSnapshot snap = s.getAll("s-1");
            assertThat(snap.items()).hasSize(threads);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void perSessionSidecarsAreIndependent() {
        MessageFeedbackStore s = store();
        s.put("s-A", u("a"), Rating.UP, null);
        s.put("s-B", u("a"), Rating.DOWN, null);
        assertThat(s.get("s-A", u("a")).rating()).isEqualTo(Rating.UP);
        assertThat(s.get("s-B", u("a")).rating()).isEqualTo(Rating.DOWN);
    }
}