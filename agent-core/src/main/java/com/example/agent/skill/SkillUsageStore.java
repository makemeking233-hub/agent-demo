package com.example.agent.skill;

import com.example.agent.config.AgentPaths;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 技能使用频次存储（add-skill-demand-loading T2）。
 *
 * <p>持久化到 {@code ~/.agent-demo/skill-usage.json}，结构为
 * {@code {"skill-name": {"count": 12, "lastUsedMs": 1758000000000}}}。
 *
 * <p><b>为什么不复用 settings.yaml</b>：{@code SettingsService.patch} 每次写都广播
 * {@code settings.changed} SSE；频次是高频写（每次工具调用），会打爆 SSE。
 *
 * <p><b>写入策略</b>：内存累积 + 延迟写——每 {@value #AUTO_FLUSH_EVERY} 次 {@link #record} 或
 * 显式 {@link #flush()} / {@link #close()} 才落盘。原子写（tmp + {@code ATOMIC_MOVE}）。
 *
 * <p><b>得分</b>：{@code count × exp(-lambda × 距上次使用天数)}，lambda 缺省 0.05
 * （半衰期 {@code ln2/0.05 ≈ 13.9} 天）。让三个月前的常用技能自然下沉。
 */
public final class SkillUsageStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SkillUsageStore.class);

    /** 每多少次 record 自动 flush 一次。 */
    static final int AUTO_FLUSH_EVERY = 10;

    /** 一天的毫秒数（得分衰减用）。 */
    private static final double MILLIS_PER_DAY = 86_400_000.0;

    private final ObjectMapper json = new ObjectMapper();

    /** 持久化文件路径。 */
    private final Path file;

    /** skillName -> 使用记录（并发安全；写入频次远低于读）。 */
    private final Map<String, SkillUsage> usage = new ConcurrentHashMap<>();

    /** 距上次 flush 后的 record 次数。 */
    private final AtomicInteger pending = new AtomicInteger(0);

    /**
     * 单条使用记录。
     *
     * @param count      累计成功使用次数
     * @param lastUsedMs 上次使用时间（毫秒时间戳）
     */
    public record SkillUsage(int count, long lastUsedMs) {}

    /**
     * 构造（并从磁盘加载既有记录）。
     *
     * @param file 持久化文件路径（不存在 / 损坏时从空开始，记 WARN）
     */
    public SkillUsageStore(Path file) {
        this.file = file;
        load();
    }

    /**
     * 使用默认路径构造（{@code <agentHome>/skill-usage.json}）。
     *
     * @return 实例
     */
    public static SkillUsageStore defaultStore() {
        return new SkillUsageStore(AgentPaths.agentHome().resolve("skill-usage.json"));
    }

    /**
     * 记录一次成功使用（计数 +1，刷新 lastUsed）。
     *
     * @param skillName 技能名（{@code null} / 空白忽略）
     */
    public void record(String skillName) {
        recordAt(skillName, System.currentTimeMillis());
    }

    /**
     * 在指定时间点记录一次使用（测试用，可构造衰减场景）。
     *
     * @param skillName 技能名（{@code null} / 空白忽略）
     * @param atMs      使用时间（毫秒）
     */
    public void recordAt(String skillName, long atMs) {
        if (skillName == null || skillName.isBlank()) return;
        usage.merge(
                skillName,
                new SkillUsage(1, atMs),
                (old, add) -> new SkillUsage(old.count() + 1, Math.max(old.lastUsedMs(), atMs)));
        if (pending.incrementAndGet() >= AUTO_FLUSH_EVERY) {
            flush();
        }
    }

    /**
     * @param skillName 技能名
     * @return 累计使用次数（未知返回 0）
     */
    public int count(String skillName) {
        SkillUsage u = usage.get(skillName);
        return u == null ? 0 : u.count();
    }

    /**
     * @param skillName 技能名
     * @return 上次使用毫秒时间戳（未知返回 0）
     */
    public long lastUsedMs(String skillName) {
        SkillUsage u = usage.get(skillName);
        return u == null ? 0L : u.lastUsedMs();
    }

    /**
     * 计算单个技能的衰减得分。
     *
     * @param skillName 技能名
     * @param lambda    衰减系数（{@code <= 0} 视为无衰减）
     * @param nowMs     当前时间（毫秒）
     * @return 得分（未知技能返回 0）
     */
    public double score(String skillName, double lambda, long nowMs) {
        SkillUsage u = usage.get(skillName);
        if (u == null) return 0.0;
        double days = Math.max(0.0, (nowMs - u.lastUsedMs()) / MILLIS_PER_DAY);
        double factor = lambda > 0 ? Math.exp(-lambda * days) : 1.0;
        return u.count() * factor;
    }

    /**
     * 计算全部技能的衰减得分快照。
     *
     * @param lambda 衰减系数
     * @param nowMs  当前时间（毫秒）
     * @return skillName -> 得分（按名字升序，便于稳定迭代）
     */
    public Map<String, Double> scores(double lambda, long nowMs) {
        Map<String, Double> out = new LinkedHashMap<>();
        usage.keySet().stream()
                .sorted()
                .forEach(name -> out.put(name, score(name, lambda, nowMs)));
        return out;
    }

    /**
     * 原子写盘（tmp + {@code ATOMIC_MOVE}）；失败只记 WARN 不抛。
     */
    public synchronized void flush() {
        pending.set(0);
        if (file == null) return;
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = Files.createTempFile(
                    parent != null ? parent : Path.of("."), "skill-usage", ".json.tmp");
            json.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), usage);
            try {
                Files.move(
                        tmp,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                // 某些文件系统（如跨卷）不支持 ATOMIC_MOVE，退回普通替换
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("写入 skill-usage 失败: {}", file, e);
        }
    }

    /** 从磁盘加载；文件缺失或内容损坏时从空开始（自愈）。 */
    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            Map<String, SkillUsage> loaded =
                    json.readValue(file.toFile(), new TypeReference<Map<String, SkillUsage>>() {});
            if (loaded != null) {
                loaded.forEach(
                        (k, v) -> {
                            if (k != null && v != null) usage.put(k, v);
                        });
            }
        } catch (IOException | RuntimeException e) {
            log.warn("读取 skill-usage 失败，从空开始: {}", file, e);
            usage.clear();
        }
    }

    /** 关闭时 flush 未落盘记录。 */
    @Override
    public void close() {
        flush();
    }
}