package com.example.agent.session;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 超期会话自动归档（auto-archive-stale-sessions）。
 *
 * <p>把会话目录下**最后活动时间早于保留期**的会话移入归档（复用 {@link SessionStore#archive}，
 * 仍是软删除、可恢复）。判定基准是文件 mtime —— 即会话最后一条记录写入时间，也就是"最后活动"。
 * 用创建时间判定会把"昨天刚聊过但创建于三周前"的活跃会话误归档。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li><b>跳过进行中的会话</b>：由调用方提供判据（web 侧来自 {@code ChatStreamService}）。
 *       边界情况下用户可能把页面开着一周以上，此时它的流仍活着，搬走文件会破坏后续写入。
 *   <li><b>不自己取系统时间</b>：{@code nowMillis} 显式传入，保留期判定是全功能的正确性核心，
 *       测试必须能精确构造"恰好 7 天 / 8 天"的边界。
 *   <li><b>单个失败不影响其余</b>：整理是低频运维动作，不应因一个损坏的会话中断整轮。
 * </ul>
 */
public final class SessionAutoArchiver {

    private static final Logger log = LoggerFactory.getLogger(SessionAutoArchiver.class);

    /**
     * 一次整理的结果。
     *
     * @param scanned 扫描到的会话数
     * @param archived 实际被归档的会话 id
     * @param skippedActive 因存在活动流而跳过的会话 id
     * @param failed 归档失败的会话 id
     */
    public record Result(
            int scanned, List<String> archived, List<String> skippedActive, List<String> failed) {}

    private SessionAutoArchiver() {}

    /**
     * 归档会话目录中所有超期且不在活动中的会话。
     *
     * @param sessionsDir 会话目录（不存在时返回空结果）
     * @param retention 保留期（距今超过它即归档）；{@code null} 或非正数时不做任何事
     * @param nowMillis 当前时间（毫秒），由调用方提供以便测试
     * @param isActive 判据：会话是否仍在进行中（可为 {@code null}，表示无此约束）
     * @return 整理结果
     */
    public static Result archiveStale(
            Path sessionsDir, Duration retention, long nowMillis, Predicate<String> isActive) {
        if (sessionsDir == null || retention == null || retention.isZero() || retention.isNegative()) {
            return new Result(0, List.of(), List.of(), List.of());
        }
        List<SessionStore.SessionFile> files = SessionStore.listSessionFiles(sessionsDir);
        long cutoffMillis = nowMillis - retention.toMillis();

        List<String> archived = new ArrayList<>();
        List<String> skippedActive = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (SessionStore.SessionFile file : files) {
            // 未超期：保留（严格早于 cutoff 才归档，恰好等于不归档）
            if (file.lastModifiedMillis() >= cutoffMillis) continue;
            if (isActive != null && isActive.test(file.id())) {
                skippedActive.add(file.id());
                continue;
            }
            if (SessionStore.archive(sessionsDir, file.id())) {
                archived.add(file.id());
            } else {
                failed.add(file.id());
            }
        }

        if (!archived.isEmpty() || !failed.isEmpty() || !skippedActive.isEmpty()) {
            log.info(
                    "自动归档完成 dir={} 扫描={} 归档={} 跳过(活动中)={} 失败={}",
                    sessionsDir,
                    files.size(),
                    archived.size(),
                    skippedActive.size(),
                    failed.size());
            if (!archived.isEmpty()) {
                log.info("自动归档会话列表 dir={} ids={}", sessionsDir, archived);
            }
        }
        return new Result(files.size(), archived, skippedActive, failed);
    }
}
