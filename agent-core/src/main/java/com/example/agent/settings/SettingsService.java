package com.example.agent.settings;

import com.example.agent.settings.exception.SettingsConflictException;
import com.example.agent.settings.exception.SettingsValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * settings 读写 + revision 乐观锁（add-settings-foundation M1）。
 *
 * <p>单写者写锁 + revision 单调递增；写完后通过 {@link SettingsChangeBroadcaster} 广播。
 */
public class SettingsService {
    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    /**
     * 旧 3 档 wire value → 新 4 档 dsh 命名（rewrite-permission-mode-dsh T12.1）。
     *
     * <p>settings.yaml 升级时 read() 检测到旧值则 normalize + INFO 日志 + 写回 YAML。
     */
    private static final Map<String, String> LEGACY_PERMISSION_MODE_MIGRATION = Map.of(
            "read_only", "plan",
            "workspace_write", "ask",
            "full_access", "danger-full");

    private final SettingsFile file;
    private final SettingsChangeBroadcaster broadcaster;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ObjectMapper yaml;

    private volatile long revision = 0L;

    public SettingsService(SettingsFile file, SettingsChangeBroadcaster broadcaster) {
        this.file = file;
        this.broadcaster = broadcaster;
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        this.yaml = mapper;
    }

    /** 读取完整 settings；不存在则返回默认值；保留未知字段 */
    public SettingsView read() throws IOException {
        SettingsView view = readInternal();
        // T12.1: 检测到旧 3 档 wire value 时, 单独在 writeLock 下迁移写回
        if (needsMigration(view)) {
            view = migrateAndRewrite();
        }
        return view;
    }

    /** 读取 settings（仅读锁, 不修改磁盘）。 */
    private SettingsView readInternal() throws IOException {
        lock.readLock().lock();
        try {
            file.ensureFile();
            String content = Files.readString(file.file());
            if (content.isBlank()) {
                return new SettingsView(1, SettingsView.defaultGeneral(), revision);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = yaml.readValue(content, Map.class);
            SettingsView view = toView(raw);
            view.setRevision(revision);
            return view;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 迁移并写回 (writeLock 一次性读 + 改 + 写)。{@code readInternal} 已有检查,
     * 写回时直接 overwrite。
     */
    private SettingsView migrateAndRewrite() throws IOException {
        lock.writeLock().lock();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = yaml.readValue(Files.readString(file.file()), Map.class);
            String oldValue = detectLegacyPermissionMode(raw);
            if (oldValue == null) {
                // 另一线程已迁移; 重新 read
                SettingsView v = toView(raw);
                v.setRevision(revision);
                return v;
            }
            String newValue = LEGACY_PERMISSION_MODE_MIGRATION.get(oldValue);
            log.info("migrated permission mode from '{}' to '{}' in settings.yaml", oldValue, newValue);
            applyMigration(raw, oldValue, newValue);
            SettingsView view = toView(raw);
            return writeAtomic(view);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 检测 view 的 general.permission.mode 是否为旧 3 档（无需锁, 读已 immutable view）。 */
    @SuppressWarnings("unchecked")
    private static boolean needsMigration(SettingsView view) {
        if (view == null || view.getGeneral() == null) return false;
        Map<String, Object> permission = (Map<String, Object>) view.getGeneral().get("permission");
        if (permission == null) return false;
        Object mode = permission.get("mode");
        return mode instanceof String s && LEGACY_PERMISSION_MODE_MIGRATION.containsKey(s);
    }

    /** 检测 general.permission.mode 是否为旧 3 档；是则返回旧值（用于日志），否则 null。 */
    @SuppressWarnings("unchecked")
    private static String detectLegacyPermissionMode(Map<String, Object> raw) {
        Map<String, Object> general = (Map<String, Object>) raw.get("general");
        if (general == null) return null;
        Map<String, Object> permission = (Map<String, Object>) general.get("permission");
        if (permission == null) return null;
        Object modeObj = permission.get("mode");
        if (!(modeObj instanceof String mode)) return null;
        return LEGACY_PERMISSION_MODE_MIGRATION.containsKey(mode) ? mode : null;
    }

    /** 原地替换 general.permission.mode = newValue。 */
    @SuppressWarnings("unchecked")
    private static void applyMigration(Map<String, Object> raw, String oldValue, String newValue) {
        Map<String, Object> general = (Map<String, Object>) raw.get("general");
        if (general == null) return;
        Map<String, Object> permission = (Map<String, Object>) general.get("permission");
        if (permission == null) return;
        permission.put("mode", newValue);
    }

    /** 原子写入；写前调用校验；写完广播 */
    public SettingsView writeAtomic(SettingsView view) throws IOException {
        lock.writeLock().lock();
        try {
            file.ensureFile();
            SettingsValidator.validateSchema(view);
            // 保留未知字段（仅校验 schema，不丢任何顶层 key）
            Map<String, Object> toWrite = new LinkedHashMap<>();
            toWrite.put("version", view.getVersion());
            toWrite.put("general", view.getGeneral());
            Path tmp = file.file().resolveSibling(".settings.yaml.tmp");
            yaml.writeValue(tmp.toFile(), toWrite);
            try {
                Files.move(tmp, file.file(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception atomicFail) {
                // 某些 FS 不支持 ATOMIC_MOVE（Windows 部分情况）—— 退化为 REPLACE_EXISTING
                Files.move(tmp, file.file(), StandardCopyOption.REPLACE_EXISTING);
            }
            revision++;
            view.setRevision(revision);
            log.info("[settings] 写入成功 revision={}", revision);
            broadcaster.broadcast(view);
            return view;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 应用 PATCH（带 revision 乐观锁）。
     *
     * @param path dot-notation 路径
     * @param value 新值
     * @param clientRevision 客户端 GET 时拿到的 revision；为 null 跳过校验
     * @return 写入后的 SettingsView
     */
    public SettingsView patch(String path, Object value, Long clientRevision) throws IOException {
        lock.writeLock().lock();
        try {
            SettingsView current = read();
            if (clientRevision != null && clientRevision != revision) {
                throw new SettingsConflictException("revision_mismatch");
            }
            String[] segments = SettingsPath.parse(path);
            SettingsValidator.validateField(path, value);
            // 路径若以 "general." 开头，去掉前缀（在 general 内部操作）
            int startIdx = segments.length > 0 && segments[0].equals("general") ? 1 : 0;
            String[] inner = Arrays.copyOfRange(segments, startIdx, segments.length);
            if (inner.length == 0) {
                throw new com.example.agent.settings.exception.SettingsNotFoundException("path_too_short");
            }
            SettingsPath.set(current.getGeneral(), inner, value);
            return writeAtomic(current);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 测试用：重置 revision 与磁盘内容 */
    public void resetForTest() {
        lock.writeLock().lock();
        try {
            revision = 0L;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private SettingsView toView(Map<String, Object> raw) {
        int version = raw.containsKey("version") ? ((Number) raw.get("version")).intValue() : 1;
        Map<String, Object> general = (Map<String, Object>) raw.getOrDefault("general", SettingsView.defaultGeneral());
        return new SettingsView(version, general, revision);
    }
}
