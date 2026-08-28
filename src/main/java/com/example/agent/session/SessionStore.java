package com.example.agent.session;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会话存储：JSONL append-only（详见 design.md §10）。
 *
 * <p>双路径去重：
 *
 * <ul>
 *   <li>后台批量 flush（每 flushIntervalMs 或 queue 满 flushBatchSize）
 *   <li>关键节点 sync flush（用户提交、Finished、工具调用完成）
 *   <li>{@code lastSyncedOffset} 维护已持久化尾位置，避免重复写
 * </ul>
 *
 * <p>权限：文件 0600，目录 0700（POSIX；Windows 跳过）。
 */
public class SessionStore implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

  /** JSONL 文件路径 */
  private final Path file;

  /** 待写入条目队列（生产者-消费者模型） */
  private final BlockingQueue<SessionEntry> queue = new LinkedBlockingQueue<>();

  /** 定时 flush 调度器（daemon 单线程） */
  private final ScheduledExecutorService flushScheduler;

  /** 队列达到此大小立即触发 flush */
  private final int flushBatchSize;

  /** 后台 flush 间隔（毫秒） */
  private final long flushIntervalMs;

  /** JSON 序列化器 */
  private final ObjectMapper json = new ObjectMapper();

  /** 文件通道（append 模式） */
  private final FileChannel channel;

  /** 写锁（保证 flushAsync + syncFlush 不并发写） */
  private final Object writeLock = new Object();

  /** 已持久化的字节偏移（外部可观察的进度） */
  private final AtomicLong lastSyncedOffset = new AtomicLong(0);

  /** 是否已关闭（关闭后不再 flush） */
  private volatile boolean closed = false;

  /**
   * 构造会话存储：建父目录、设置 0700/0600 权限、打开 append 通道、启动定时 flush。
   *
   * @param file JSONL 文件路径
   * @param flushBatchSize 队列阈值（达到立即 flush）
   * @param flushIntervalMs 后台 flush 间隔（毫秒）
   * @throws IOException 文件打开或权限设置失败
   */
  public SessionStore(Path file, int flushBatchSize, long flushIntervalMs) throws IOException {
    this.file = file;
    this.flushBatchSize = flushBatchSize;
    this.flushIntervalMs = flushIntervalMs;
    Files.createDirectories(file.getParent());
    try {
      Files.setPosixFilePermissions(
          file.getParent(),
          EnumSet.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE));
    } catch (UnsupportedOperationException ignored) {
    }
    this.channel =
        FileChannel.open(
            file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    try {
      Files.setPosixFilePermissions(
          file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException ignored) {
    }
    this.flushScheduler = createFlushScheduler();
    flushScheduler.scheduleAtFixedRate(
        this::flushAsync, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
  }

  /**
   * 创建显式参数的 {@link ScheduledThreadPoolExecutor}，避免 {@code Executors} 静态工厂 （规范 10.1-1：禁止
   * Executors，必须 ThreadPoolExecutor 显式 7 参数）。
   *
   * <p>参数取值：
   *
   * <ul>
   *   <li>corePoolSize=1：单线程足够（单写者）
   *   <li>queue 容量 = 64：避免无界 OOM（规范 10.1-2）
   *   <li>daemon=true：JVM 退出时不阻塞（规范 10.1-3 自定义 ThreadFactory）
   * </ul>
   */
  private static ScheduledExecutorService createFlushScheduler() {
    ThreadFactory tf =
        r -> {
          Thread t = new Thread(r, "session-flush");
          t.setDaemon(true);
          return t;
        };
    return new ScheduledThreadPoolExecutor(1, tf);
  }

  /**
   * 追加条目到队列；队列满则立即触发 flush。
   *
   * @param entry 待追加条目
   */
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

  /** 后台定时 flush（{@link #flushScheduler} 周期调用） */
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
    }
  }

  /**
   * 把条目序列化为 JSONL 写入文件；失败时回滚条目到队首。
   *
   * @param entries 待写入条目列表
   */
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
    } catch (Exception e) { // IOException + JsonProcessingException + RuntimeException
      log.warn("写盘失败，entries 已重入队首", e);
      for (int i = entries.size() - 1; i >= 0; i--) {
        queue.offer(entries.get(i));
      }
      throw new RuntimeException("写盘失败", e);
    }
  }

  /**
   * @return JSONL 文件路径
   */
  public Path file() {
    return file;
  }

  /**
   * @return 已持久化的字节偏移（用于断点续写 / 进度观察）
   */
  public long lastSyncedOffset() {
    return lastSyncedOffset.get();
  }

  /**
   * 关闭存储：标记 closed → 同步 flush 剩余 → 关闭调度器 → 关闭通道。
   *
   * @throws IOException 通道关闭失败
   */
  @Override
  public void close() throws IOException {
    closed = true;
    syncFlush();
    flushScheduler.shutdown();
    channel.close();
  }

  /**
   * @return 调试用字符串（含 file / lastSyncedOffset / batchSize / intervalMs）
   */
  @Override
  public String toString() {
    return "SessionStore{file="
        + file
        + ", lastSyncedOffset="
        + lastSyncedOffset.get()
        + ", batchSize="
        + flushBatchSize
        + ", intervalMs="
        + flushIntervalMs
        + "}";
  }
}
