package com.example.agent.web.wecom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 企业微信流式 markdown 回复推送（add-wecom-channel task 5.1）。
 *
 * <p>订阅 AgentLoop 的 chunk 流，按限频累积并 flush 到企业微信：
 *
 * <ul>
 *   <li>累积 ≥ {@code reply.flush-min-chars} → 立即 flush
 *   <li>累积 ≥ {@code reply.max-chars} → 截断发新条（避免单条超 4096 字节）
 *   <li>tick() 推进时间过 {@code flush-interval-ms} 且 buffer 非空 → flush
 *   <li>errcode=45009（限频）→ {@link RetryableWecomException} → 退避
 *       {@code retry-backoff-ms} 期间合并后续 chunk
 * </ul>
 *
 * <p>线程安全：每 userId 独立 {@link UserState}（{@link ConcurrentHashMap}）。
 */
public class WecomReplyPusher {

    private static final Logger log = LoggerFactory.getLogger(WecomReplyPusher.class);

    private final WecomMessageSender sender;
    private final WecomConfigProperties.Reply config;
    private final LongSupplier clock;
    private final Map<String, UserState> states = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public WecomReplyPusher(WecomMessageSender sender,
                              WecomConfigProperties.Reply config,
                              LongSupplier clock) {
        this.sender = sender;
        this.config = config;
        this.clock = clock;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wecom-reply-pusher");
            t.setDaemon(true);
            return t;
        });
        // 每 flush-interval-ms tick 一次（驱动累积窗口）
        long interval = config.flushIntervalMs();
        scheduler.scheduleAtFixedRate(this::tickSafely, interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * 追加 chunk 到 buffer；若 ≥ minChars 立即 flush。
     */
    public void append(String userId, String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        UserState state = states.computeIfAbsent(userId, k -> new UserState(clock.getAsLong()));
        synchronized (state) {
            state.buffer.append(chunk);
            // 立即 flush 触发：>= minChars
            if (state.buffer.length() >= config.flushMinChars()) {
                flushLocked(state, userId);
            }
        }
    }

    /**
     * 周期性 tick：处理累积窗口到 + 退避到期的 userId。
     */
    public void tick() {
        long now = clock.getAsLong();
        for (Map.Entry<String, UserState> entry : states.entrySet()) {
            UserState state = entry.getValue();
            synchronized (state) {
                if (state.buffer.length() == 0) continue;
                if (state.backoffUntil > now) continue; // 退避中
                if (now - state.lastFlush >= config.flushIntervalMs()) {
                    flushLocked(state, entry.getKey());
                }
            }
        }
    }

    /** 测试 / shutdown 用。 */
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void tickSafely() {
        try {
            tick();
        } catch (Exception e) {
            log.warn("wecom reply pusher tick 异常：{}", e.getMessage());
        }
    }

    private void flushLocked(UserState state, String userId) {
        if (state.buffer.length() == 0) return;

        // 超长截断：分多条发
        int max = config.maxChars();
        while (state.buffer.length() > max) {
            String chunk = state.buffer.substring(0, max);
            state.buffer.delete(0, max);
            sendOne(userId, chunk, state);
            if (state.backoffUntil > 0) return; // 限频后退出
        }
        sendOne(userId, state.buffer.toString(), state);
        if (state.backoffUntil == 0) {
            state.buffer.setLength(0);
            state.lastFlush = clock.getAsLong();
        }
        // 退避中保留 buffer，等下次 tick 重发
    }

    private void sendOne(String userId, String content, UserState state) {
        try {
            sender.sendMarkdown(userId, content);
            state.backoffUntil = 0;
        } catch (RetryableWecomException e) {
            state.backoffUntil = clock.getAsLong() + config.retryBackoffMs();
            log.warn("wecom send 限频（errcode=45009），userId={} 进入 backoff {}ms：{}",
                    userId, config.retryBackoffMs(), e.getMessage());
            // 限频：buffer 保留（不截断/不清空），下次 tick 合并发送
        } catch (RuntimeException e) {
            state.backoffUntil = clock.getAsLong() + config.retryBackoffMs();
            log.warn("wecom send 永久错（userId={}）：{}，进入 backoff",
                    userId, e.getMessage());
            state.buffer.setLength(0); // 永久错：丢 buffer（避免重试死循环）
        }
    }

    /** 防止 scheduler 引用泄漏。 */
    @SuppressWarnings("unused")
    private static ScheduledFuture<?> noop() {
        return null;
    }

    /** 每 userId 的累积状态（独立锁）。 */
    static final class UserState {
        final StringBuilder buffer = new StringBuilder();
        /** 首次创建时的 clock 值（避免 real-time / test-clock 错位） */
        long lastFlush;
        long backoffUntil = 0;

        UserState(long initialClock) {
            this.lastFlush = initialClock;
        }
    }
}
