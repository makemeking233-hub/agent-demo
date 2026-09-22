package com.example.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 消息反馈的 sidecar 存储（add-message-feedback）。
 *
 * <p>文件格式：{@code <feedbackDir>/<sessionId>.json}，schema v1：
 *
 * <pre>{@code
 * {
 *   "version": 1,
 *   "session_id": "<sessionId>",
 *   "items": {
 *     "<messageUuid>": { "rating": "up", "version": 3, "updated_at": 1736700000000 }
 *   }
 * }
 * }</pre>
 *
 * <p>关键设计：
 *
 * <ul>
 *   <li>**对模型不可见**：sidecar 不参与 {@code AgentLoop.toRequest()}；Agent 永远看不到 feedback
 *   <li>**per-item version CAS**：`ifVersion == null` 表示「必须不存在」（首次创建），
 *       `ifVersion == N` 表示「必须等于当前 version N」（更新/取消）；冲突抛
 *       {@link MessageFeedbackVersionConflict}，携带 {@code current}（可能为 null）
 *   <li>**写操作加文件锁**：{@code <sessionId>.lock} 的 {@link FileLock}（POSIX advisory lock
 *       + Windows 默认 fs semantics）；同 JVM 用 retry 退避，跨 JVM 由 OS 保证串行
 *   <li>**文件权限 0600，目录 0700**：POSIX 设置，Windows 跳过
 * </ul>
 *
 * <p>线程安全：所有写方法内部加锁 + CAS；多线程并发对同一 messageId 的 PUT 会**串行化**
 *（后者拿到前者的 version，触发 409 冲突 → 多标签页不互相覆盖）。
 */
public class MessageFeedbackStore {

    private static final Logger log = LoggerFactory.getLogger(MessageFeedbackStore.class);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int SCHEMA_VERSION = 1;
    private static final int LOCK_RETRY_MAX = 20;
    private static final long LOCK_RETRY_SLEEP_MS = 25L;
    private static final String FILE_PREFIX = "feedback-";
    private static final String LOCK_SUFFIX = ".lock";

    private final Path feedbackDir;

    /**
     * 构造反馈 sidecar 存储（首次实例化某会话时会懒创建文件）。
     *
     * @param feedbackDir sidecar 所在目录（{@code <agentHome>/feedback}）
     */
    public MessageFeedbackStore(Path feedbackDir) {
        if (feedbackDir == null) {
            throw new IllegalArgumentException("feedbackDir == null");
        }
        this.feedbackDir = feedbackDir;
        ensureDir();
    }

    // ---------- 读（不加锁）----------

    /**
     * 取某会话全部 feedback 项（顺序：最近更新在前；无 sidecar → 空 snapshot）。
     *
     * @param sessionId 会话 id
     * @return snapshot（绝不 {@code null}）
     */
    public FeedbackSnapshot getAll(String sessionId) {
        if (!isValidId(sessionId)) return FeedbackSnapshot.empty();
        Path file = sidecarFile(sessionId);
        if (!Files.isRegularFile(file)) return FeedbackSnapshot.empty(sessionId);
        try {
            JsonNode root = JSON.readTree(Files.readString(file));
            return parseSnapshot(sessionId, root);
        } catch (Exception e) {
            log.warn("读取 feedback sidecar 失败：session={} 视为空", sessionId, e);
            return FeedbackSnapshot.empty(sessionId);
        }
    }

    /**
     * 取某条消息的 feedback；不存在返 null。
     *
     * @param sessionId 会话 id
     * @param messageId 消息 uuid
     * @return feedback 项；无则 {@code null}
     */
    public FeedbackItem get(String sessionId, String messageId) {
        return getAll(sessionId).items().get(messageId);
    }

    // ---------- 写（加锁 + CAS）----------

    /**
     * 创建或更新 feedback 项。
     *
     * <p>CAS 语义：
     *
     * <ul>
     *   <li>{@code ifVersion == null}：要求该 message 尚无任何记录 → 否则抛
     *       {@link MessageFeedbackVersionConflict}，{@code current} 为现有项
     *   <li>{@code ifVersion == N}：要求现有 version == N → 否则抛冲突
     * </ul>
     *
     * @param sessionId 会话 id
     * @param messageId 消息 uuid
     * @param rating    {@link Rating#UP} / {@link Rating#DOWN}
     * @param ifVersion 期望版本（{@code null} = 必须不存在）
     * @return 写入后的新 feedback 项（含新 version）
     * @throws MessageFeedbackVersionConflict CAS 冲突，携带 {@code current}
     * @throws IllegalArgumentException       sessionId / messageId / rating 非法
     */
    public FeedbackItem put(String sessionId, String messageId, Rating rating, Long ifVersion) {
        requireValid(sessionId, messageId);
        if (rating == null) {
            throw new IllegalArgumentException("rating == null");
        }
        return withLock(
                sessionId,
                () -> mutatePut(sessionId, messageId, rating, ifVersion));
    }

    /**
     * 删除 feedback 项（== 取消 👍/👎）。
     *
     * <p>CAS 语义同 {@link #put}：{@code ifVersion} 必须匹配；缺失时 {@code current == null}。
     *
     * @param sessionId 会话 id
     * @param messageId 消息 uuid
     * @param ifVersion 期望版本（{@code null} = 必须不存在）
     * @throws MessageFeedbackVersionConflict CAS 冲突，{@code current} 可能为 {@code null}
     */
    public void delete(String sessionId, String messageId, Long ifVersion) {
        requireValid(sessionId, messageId);
        withLock(sessionId,
                () -> {
                    mutateDelete(sessionId, messageId, ifVersion);
                    return null;
                });
    }

    // ---------- 内部 ----------

    /** 反馈值（与 spec §feedback sidecar schema / 👍/👎 两态 对齐）。 */
    public enum Rating {
        UP("up"),
        DOWN("down");

        private final String wire;

        Rating(String wire) {
            this.wire = wire;
        }

        /** 与 sidecar JSON 互转的字面量（{@code "up"} / {@code "down"}）。 */
        public String wire() {
            return wire;
        }

        /** 把 wire 字面量解析回枚举；非法返 null（让上层决定怎么报错）。 */
        public static Rating fromWire(String s) {
            if (s == null) return null;
            for (Rating r : values()) {
                if (r.wire.equals(s)) return r;
            }
            return null;
        }
    }

    /** 一条 feedback 项（写入侧即返回）。 */
    public record FeedbackItem(Rating rating, long version, long updatedAt) {
        public FeedbackItem {
            if (rating == null) throw new IllegalArgumentException("rating == null");
            if (version < 1) throw new IllegalArgumentException("version < 1");
        }
    }

    /** 整会话 snapshot（来自 GET 端点）。 */
    public record FeedbackSnapshot(String sessionId, Map<String, FeedbackItem> items) {
        public FeedbackSnapshot {
            items = items == null ? Map.of() : Collections.unmodifiableMap(items);
        }

        public static FeedbackSnapshot empty() {
            return new FeedbackSnapshot(null, Map.of());
        }

        public static FeedbackSnapshot empty(String sessionId) {
            return new FeedbackSnapshot(sessionId, Map.of());
        }
    }

    /** CAS 冲突：当前真实状态（{@code null} 表示「不存在」）。 */
    public static final class MessageFeedbackVersionConflict extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final FeedbackItem current;

        public MessageFeedbackVersionConflict(FeedbackItem current) {
            super("message_feedback_version_conflict: current=" + (current == null ? "<absent>" : current.version()));
            this.current = current;
        }

        /** 冲突时的真实状态；可能为 {@code null}（表示对方已删除）。 */
        public FeedbackItem current() {
            return current;
        }
    }

    private Path sidecarFile(String sessionId) {
        return feedbackDir.resolve(FILE_PREFIX + sessionId + ".json");
    }

    private Path lockFile(String sessionId) {
        return feedbackDir.resolve(FILE_PREFIX + sessionId + LOCK_SUFFIX);
    }

    private void ensureDir() {
        try {
            Files.createDirectories(feedbackDir);
            set0700Dir(feedbackDir);
        } catch (IOException e) {
            throw new RuntimeException("create feedback dir failed: " + feedbackDir, e);
        }
    }

    private static void set0700Dir(Path dir) {
        try {
            Files.setPosixFilePermissions(
                    dir,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Windows: no-op
        } catch (IOException e) {
            log.warn("set 0700 on {} failed: {}", dir, e.toString());
        }
    }

    private static void set0600File(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    file,
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows: no-op
        }
    }

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

    private static void requireValid(String sessionId, String messageId) {
        if (!isValidId(sessionId)) {
            throw new IllegalArgumentException("invalid sessionId");
        }
        if (!isValidId(messageId)) {
            throw new IllegalArgumentException("invalid messageId");
        }
    }

    /**
     * 取文件锁后执行 mutation；锁释放由 try-with-resources 保证。
     *
     * <p>同 JVM 多线程并发时锁可能立刻被同一 JVM 内其他线程持有 —— {@link FileLock} 跨线程不重叠；
     * 这里用 sleep 退避重试若干次（最多 {@value #LOCK_RETRY_MAX} 次 = ~500ms 总预算）。
     * 跨 JVM 由 OS 保证（POSIX advisory lock / Windows 默认 fs）。
     */
    private <T> T withLock(String sessionId, IOOp<T> op) {
        Path lock = lockFile(sessionId);
        for (int i = 0; i < LOCK_RETRY_MAX; i++) {
            try (RandomAccessFile raf = new RandomAccessFile(lock.toFile(), "rw");
                    FileChannel ch = raf.getChannel();
                    FileLock fl = ch.tryLock()) {
                if (fl == null) {
                    sleepBeforeRetry(i);
                    continue;
                }
                try {
                    return op.run();
                } finally {
                    fl.release();
                }
            } catch (OverlappingFileLockException e) {
                sleepBeforeRetry(i);
            } catch (IOException e) {
                throw new RuntimeException("feedback lock failed: " + lock, e);
            }
        }
        throw new RuntimeException("feedback lock contention: " + lock);
    }

    private static void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(LOCK_RETRY_SLEEP_MS * (1L + Math.min(attempt, 4)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while waiting for feedback lock", ie);
        }
    }

    @FunctionalInterface
    private interface IOOp<T> {
        T run() throws IOException;
    }

    /** 当前快照（读侧；不消耗 lock） */
    private FeedbackSnapshot readSnapshot(String sessionId) throws IOException {
        Path file = sidecarFile(sessionId);
        if (!Files.isRegularFile(file)) return FeedbackSnapshot.empty(sessionId);
        try {
            JsonNode root = JSON.readTree(Files.readString(file));
            return parseSnapshot(sessionId, root);
        } catch (Exception e) {
            log.warn("解析 feedback sidecar 失败：session={} 视为空", sessionId, e);
            return FeedbackSnapshot.empty(sessionId);
        }
    }

    /** 解析 sidecar → snapshot；schema 不兼容时返空（fail-open 读）。 */
    private static FeedbackSnapshot parseSnapshot(String sessionId, JsonNode root) {
        if (root == null || !root.isObject()) return FeedbackSnapshot.empty(sessionId);
        JsonNode v = root.get("version");
        if (v == null || !v.canConvertToInt() || v.intValue() != SCHEMA_VERSION) {
            log.warn("feedback sidecar schema version != {} (session={})", SCHEMA_VERSION, sessionId);
            return FeedbackSnapshot.empty(sessionId);
        }
        JsonNode items = root.get("items");
        if (items == null || !items.isObject()) return FeedbackSnapshot.empty(sessionId);
        // 按 updated_at 降序（最近更新在前）
        Map<String, FeedbackItem> parsed = new LinkedHashMap<>();
        items.fields()
                .forEachRemaining(e -> {
                    JsonNode n = e.getValue();
                    Rating r = Rating.fromWire(text(n.get("rating")));
                    if (r == null) return; // 跳过非法项
                    long ver = n.path("version").asLong(0);
                    long ts = n.path("updated_at").asLong(0);
                    if (ver < 1) return;
                    parsed.put(e.getKey(), new FeedbackItem(r, ver, ts));
                });
        // 排序：按 version 降序（version 单调递增，等价于按 updated_at）
        Map<String, FeedbackItem> sorted = new LinkedHashMap<>();
        parsed.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().version(), a.getValue().version()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return new FeedbackSnapshot(sessionId, sorted);
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }

    /** mutate: 校验 CAS → 改 items → 原子写回（先 .tmp 再 rename）。 */
    private FeedbackItem mutatePut(String sessionId, String messageId, Rating rating, Long ifVersion) throws IOException {
        FeedbackSnapshot snap = readSnapshot(sessionId);
        FeedbackItem existing = snap.items().get(messageId);
        casCheck(existing, ifVersion);
        Map<String, FeedbackItem> next = new LinkedHashMap<>(snap.items());
        long nextVersion = (existing == null ? 0 : existing.version()) + 1;
        FeedbackItem written = new FeedbackItem(rating, nextVersion, System.currentTimeMillis());
        next.put(messageId, written);
        writeSidecar(sessionId, next);
        return written;
    }

    /** mutate: 校验 CAS → 删除 entry → 写回（空 items 仍写一遍：避免下次 CAS 把空视为"未存在"。 */
    private void mutateDelete(String sessionId, String messageId, Long ifVersion) throws IOException {
        FeedbackSnapshot snap = readSnapshot(sessionId);
        FeedbackItem existing = snap.items().get(messageId);
        casCheck(existing, ifVersion);
        if (existing == null) {
            // 已不存在：CAS(null, ifVersion=null) → 显式抛冲突（让前端从 current==null 路径删本地）
            throw new MessageFeedbackVersionConflict(null);
        }
        Map<String, FeedbackItem> next = new LinkedHashMap<>(snap.items());
        next.remove(messageId);
        writeSidecar(sessionId, next);
    }

    /** 把 ifVersion 与 existing 比对：null = 必须不存在；N = 必须 == N；不匹配抛冲突。 */
    private static void casCheck(FeedbackItem existing, Long ifVersion) {
        if (ifVersion == null) {
            if (existing != null) {
                throw new MessageFeedbackVersionConflict(existing);
            }
            return;
        }
        long v = ifVersion;
        if (existing == null || existing.version() != v) {
            throw new MessageFeedbackVersionConflict(existing);
        }
    }

    /** 原子写：写 .tmp → rename（避免读到半截 JSON）。 */
    private void writeSidecar(String sessionId, Map<String, FeedbackItem> items) throws IOException {
        Path target = sidecarFile(sessionId);
        Path tmp = feedbackDir.resolve(FILE_PREFIX + sessionId + ".json.tmp");
        ObjectNode root = JSON.createObjectNode();
        root.put("version", SCHEMA_VERSION);
        root.put("session_id", sessionId);
        ObjectNode item = root.putObject("items");
        items.forEach((k, v) -> {
            ObjectNode n = item.putObject(k);
            n.put("rating", v.rating().wire());
            n.put("version", v.version());
            n.put("updated_at", v.updatedAt());
        });
        Files.writeString(tmp, JSON.writeValueAsString(root));
        set0600File(tmp);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** 标记 UUID-like id 字符集；反馈写入端可复用 {@link #isValidId}。 */
    public static boolean isValidUuid(String s) {
        return isValidId(s);
    }

    /** 用 {@link Set#of()} 反向导出 {@code "up"} / {@code "down"} 给前端 / 测试。 */
    public static Set<String> allowedRatingWires() {
        return Set.of(Rating.UP.wire(), Rating.DOWN.wire());
    }
}