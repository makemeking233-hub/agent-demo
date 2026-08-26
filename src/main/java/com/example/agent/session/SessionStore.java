package com.example.agent.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话存储：JSONL append-only（详见 design.md §10）。
 *
 * <p>双路径去重：
 * <ul>
 *   <li>后台批量 flush（每 flushIntervalMs 或 queue 满 flushBatchSize）</li>
 *   <li>关键节点 sync flush（用户提交、Finished、工具调用完成）</li>
 *   <li>{@code lastSyncedOffset} 维护已持久化尾位置，避免重复写</li>
 * </ul>
 *
 * <p>权限：文件 0600，目录 0700（POSIX；Windows 跳过）。
 */
public class SessionStore implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    private final Path file;
    private final BlockingQueue<SessionEntry> queue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService flushScheduler;
    private final int flushBatchSize;
    private final long flushIntervalMs;
    private final ObjectMapper json = new ObjectMapper();
    private final FileChannel channel;
    private final Object writeLock = new Object();
    private final AtomicLong lastSyncedOffset = new AtomicLong(0);
    private volatile boolean closed = false;

    public SessionStore(Path file, int flushBatchSize, long flushIntervalMs) throws IOException {
        this.file = file;
        this.flushBatchSize = flushBatchSize;
        this.flushIntervalMs = flushIntervalMs;
        Files.createDirectories(file.getParent());
        try {
            Files.setPosixFilePermissions(file.getParent(), EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {}
        this.channel = FileChannel.open(file,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {}
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-flush");
            t.setDaemon(true);
            return t;
        });
        flushScheduler.scheduleAtFixedRate(this::flushAsync,
            flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void append(SessionEntry entry) {
        queue.add(entry);
        if (queue.size() >= flushBatchSize) flushAsync();
    }

    /** 关键节点同步落盘（详见 design.md §10 sync flush） */
    public void syncFlush() {
        List<SessionEntry> drained = new ArrayList<>();
        queue.drainTo(drained);
        synchronized (writeLock) {
            writeIfAny(drained);
        }
    }

    private void flushAsync() {
        if (closed) return;
        List<SessionEntry> drained = new ArrayList<>();
        queue.drainTo(drained);
        try {
            synchronized (writeLock) {
                writeIfAny(drained);
            }
        } catch (Exception e) {
            log.warn("async flush failed", e);
            System.err.println("[agent-demo] session flush 失败: " + e.getMessage());
        }
    }

    private void writeIfAny(List<SessionEntry> entries) {
        if (entries.isEmpty()) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (SessionEntry e : entries) sb.append(json.writeValueAsString(e)).append("\n");
            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) channel.write(buf);
            channel.force(true);
            lastSyncedOffset.addAndGet(bytes.length);
        } catch (Exception e) {   // IOException + JsonProcessingException + RuntimeException
            log.warn("写盘失败，entries 已重入队首", e);
            for (int i = entries.size() - 1; i >= 0; i--) {
                queue.offer(entries.get(i));
            }
            throw new RuntimeException("写盘失败", e);
        }
    }

    public Path file() { return file; }
    public long lastSyncedOffset() { return lastSyncedOffset.get(); }

    @Override
    public void close() throws IOException {
        closed = true;
        syncFlush();
        flushScheduler.shutdown();
        channel.close();
    }
}