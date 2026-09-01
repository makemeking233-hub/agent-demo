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
import java.nio.file.StandardCopyOption;
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

    /**
     * JSONL 文件路径
     */
    private final Path file;

    /**
     * 待写入条目队列（生产者-消费者模型）
     */
    private final BlockingQueue<SessionEntry> queue = new LinkedBlockingQueue<>();

    /**
     * 定时 flush 调度器（daemon 单线程）
     */
    private final ScheduledExecutorService flushScheduler;

    /**
     * 队列达到此大小立即触发 flush
     */
    private final int flushBatchSize;

    /**
     * 后台 flush 间隔（毫秒）
     */
    private final long flushIntervalMs;

    /**
     * JSON 序列化器
     */
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 文件通道（append 模式）
     */
    private final FileChannel channel;

    /**
     * 写锁（保证 flushAsync + syncFlush 不并发写）
     */
    private final Object writeLock = new Object();

    /**
     * 已持久化的字节偏移（外部可观察的进度）
     */
    private final AtomicLong lastSyncedOffset = new AtomicLong(0);

    /**
     * 是否已关闭（关闭后不再 flush）
     */
    private volatile boolean closed = false;

    /**
     * 构造会话存储：建父目录、设置 0700/0600 权限、打开 append 通道、启动定时 flush。
     *
     * @param file            JSONL 文件路径
     * @param flushBatchSize  队列阈值（达到立即 flush）
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
                        file,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
        try {
            Files.setPosixFilePermissions(
                    file,
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
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

    /**
     * 关键节点同步落盘（详见 design.md §10 sync flush）
     */
    public void syncFlush() {
        List<SessionEntry> drained = new ArrayList<>();
        queue.drainTo(drained);
        synchronized (writeLock) {
            writeIfAny(drained);
        }
    }

    /**
     * 后台定时 flush（{@link #flushScheduler} 周期调用）
     */
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
     * 读取指定 sessions 目录下 mtime 最新的 .jsonl 文件，反序列化为所有 entry（v0.2 /resume 命令用）。
     *
     * <p>行为：
     *
     * <ul>
     *   <li>目录不存在 / 目录为空 / 没有 .jsonl 文件 → 返回空 list
     *   <li>多个 .jsonl 文件 → 选 {@link java.nio.file.attribute.FileTime} 最大的（mtime 排序）
     *   <li>读取所有非空行，反序列化为 {@link SessionEntry}
     * </ul>
     *
     * @param sessionsDir sessions 目录路径（如 {@code ~/.agent-demo/sessions/}）
     * @return entry 列表（按文件中出现顺序）；无文件或异常时返回空 list
     */
    /**
     * 列出 sessions 目录下的所有会话 id（文件名去 {@code .jsonl}），按 mtime 降序（最近会话在前）。
     *
     * <p>列出现实会话，供前端侧边栏展示（add-session-switch change）。目录不存在 / 无 .jsonl /
     * 异常时返回空列表。
     *
     * @param sessionsDir sessions 目录路径（如 {@code ~/.agent-demo/sessions/}）
     * @return 会话 id 列表（按 mtime 降序）；无则空
     */
    public static List<String> listSessions(Path sessionsDir) {
        if (sessionsDir == null || !Files.isDirectory(sessionsDir)) return List.of();
        return listSessionIdsIn(sessionsDir);
    }

    /**
     * 列出某会话目录下所有 {@code *.jsonl} 的会话 id（文件名去 {@code .jsonl}），按 mtime 降序。
     *
     * @param dir 待扫目录（{@code sessions/} 或 {@code sessions/.archive/}）
     * @return 会话 id 列表（按 mtime 降序）；目录不存在/异常返回空
     */
    private static List<String> listSessionIdsIn(Path dir) {
        try (var stream = Files.list(dir)) {
            var files =
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                            .toList();
            return files.stream()
                    .sorted(
                            java.util.Comparator.comparing(
                                            (Path p) -> {
                                                try {
                                                    return Files.getLastModifiedTime(p);
                                                } catch (IOException e) {
                                                    return java.nio.file.attribute.FileTime.fromMillis(0);
                                                }
                                            })
                                    .reversed())
                    .map(p -> p.getFileName().toString().replace(".jsonl", ""))
                    .toList();
        } catch (IOException e) {
            log.warn("读取会话目录失败: {}", dir, e);
            return List.of();
        }
    }

    /**
     * 列出归档会话 id（{@code sessions/.archive/*.jsonl}），按 mtime 降序（最近归档在前）。
     *
     * <p>供「归档/回收站」视图展示（add-session-management change）。目录不存在/异常返回空。
     *
     * @param sessionsDir sessions 目录路径
     * @return 归档会话 id 列表；无则空
     */
    public static List<String> listArchived(Path sessionsDir) {
        if (sessionsDir == null) return List.of();
        Path archiveDir = sessionsDir.resolve(".archive");
        if (!Files.isDirectory(archiveDir)) return List.of();
        return listSessionIdsIn(archiveDir);
    }

    /**
     * 归档（软删除）一个会话：把 {@code sessions/<id>.jsonl} 移到 {@code sessions/.archive/<id>.jsonl}。
     *
     * <p>幂等（目标已存在则覆盖）；{@code id} 非法或源文件不存在返回 {@code false}。
     *
     * @param sessionsDir sessions 目录路径
     * @param id          会话 id
     * @return 是否归档成功
     */
    public static boolean archive(Path sessionsDir, String id) {
        if (sessionsDir == null || !isValidId(id)) return false;
        Path source = sessionsDir.resolve(id + ".jsonl");
        if (!Files.isRegularFile(source)) return false;
        try {
            Path archiveDir = sessionsDir.resolve(".archive");
            Files.createDirectories(archiveDir);
            Path target = archiveDir.resolve(id + ".jsonl");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.warn("归档会话失败: id={}", id, e);
            return false;
        }
    }

    /**
     * 恢复一个归档会话：把 {@code sessions/.archive/<id>.jsonl} 移回 {@code sessions/<id>.jsonl}。
     *
     * <p>幂等（目标已存在则覆盖）；{@code id} 非法或源不存在返回 {@code false}。
     *
     * @param sessionsDir sessions 目录路径
     * @param id          会话 id
     * @return 是否恢复成功
     */
    public static boolean restore(Path sessionsDir, String id) {
        if (sessionsDir == null || !isValidId(id)) return false;
        Path archiveDir = sessionsDir.resolve(".archive");
        Path source = archiveDir.resolve(id + ".jsonl");
        if (!Files.isRegularFile(source)) return false;
        try {
            Files.createDirectories(sessionsDir);
            Path target = sessionsDir.resolve(id + ".jsonl");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.warn("恢复会话失败: id={}", id, e);
            return false;
        }
    }

    /** 会话 id 白名单（防路径穿越）：仅字母/数字/下划线/连字符。 */
    private static boolean isValidId(String id) {
        if (id == null || id.isBlank()) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!allowed) return false;
        }
        return true;
    }

    public static List<SessionEntry> loadLatest(Path sessionsDir) {
        if (sessionsDir == null || !Files.isDirectory(sessionsDir)) {
            return List.of();
        }
        try (var stream = Files.list(sessionsDir)) {
            var files =
                    stream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                            .toList();
            if (files.isEmpty()) {
                return List.of();
            }
            // 找 mtime 最大的文件
            Path latest = null;
            java.nio.file.attribute.FileTime latestMtime = null;
            for (Path f : files) {
                java.nio.file.attribute.FileTime mt = Files.getLastModifiedTime(f);
                if (latestMtime == null || mt.compareTo(latestMtime) > 0) {
                    latest = f;
                    latestMtime = mt;
                }
            }
            if (latest == null) {
                return List.of();
            }
            return readEntries(latest);
        } catch (IOException e) {
            log.warn("loadLatest 读取 sessions 目录失败: {}", sessionsDir, e);
            return List.of();
        }
    }

    /**
     * 读取指定 sessions 目录下的单个会话存档（v0.3 web 会话重进恢复用）。
     *
     * <p>与 {@link #loadLatest} 不同，这里按会话 id 精确定位 {@code <sessionId>.jsonl}，而非取最新文件。
     * 文件不存在 / 目录不存在 / sessionId 为空时返回空 list。
     *
     * @param sessionsDir sessions 目录路径（如 {@code ~/.agent-demo/sessions/}）
     * @param sessionId   会话 id（对应 {@code <sessionId>.jsonl}）
     * @return entry 列表（按文件中出现顺序）；无文件或异常时返回空 list
     */
    public static List<SessionEntry> loadById(Path sessionsDir, String sessionId) {
        if (sessionsDir == null || sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        Path file = sessionsDir.resolve(sessionId + ".jsonl");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        return readEntries(file);
    }

    /**
     * 读取指定归档会话（{@code sessions/.archive/<id>.jsonl}）的条目（add-session-management 用）。
     *
     * @param sessionsDir sessions 目录路径
     * @param sessionId   会话 id
     * @return entry 列表；不存在/非法 id 返回空 list
     */
    public static List<SessionEntry> loadArchivedById(Path sessionsDir, String sessionId) {
        if (sessionsDir == null || !isValidId(sessionId)) return List.of();
        Path file = sessionsDir.resolve(".archive").resolve(sessionId + ".jsonl");
        if (!Files.isRegularFile(file)) return List.of();
        return readEntries(file);
    }

    /**
     * 反序列化单个 JSONL 会话文件的所有条目（跳过无法解析的空白/非法行）。
     *
     * @param file 会话 JSONL 文件
     * @return entry 列表（按文件行序）；文件不可读时返回空 list
     */
    private static List<SessionEntry> readEntries(Path file) {
        ObjectMapper mapper = new ObjectMapper();
        List<SessionEntry> result = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file)) {
                if (line == null || line.isBlank()) continue;
                try {
                    result.add(mapper.readValue(line, SessionEntry.class));
                } catch (Exception parseEx) {
                    log.warn("跳过无法解析的 session 行: {}", line, parseEx);
                }
            }
        } catch (IOException e) {
            log.warn("读取 session 文件失败: {}", file, e);
        }
        return result;
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
