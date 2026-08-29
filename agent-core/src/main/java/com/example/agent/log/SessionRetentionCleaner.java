package com.example.agent.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 会话日志保留策略清理器（observability 设计 D4）。
 *
 * <p>规则：
 *
 * <ul>
 *   <li>删除 mtime 超过 {@code maxAgeDays} 天的会话目录
 *   <li>剩余目录数超过 {@code keepSessions} 时按 mtime 删除最旧的
 *   <li>单目录删除失败只 WARN 并继续（不阻断会话启动）
 * </ul>
 *
 * <p>由 {@link SessionLogger} 构造（新会话创建）时调用；CLI 为短生命周期进程，
 * 启动时清理足够，不引入调度器。
 */
public class SessionRetentionCleaner {
    private static final Logger log = LoggerFactory.getLogger(SessionRetentionCleaner.class);

    /** 会话日志根目录（{@code logs/sessions/}） */
    private final Path sessionsRoot;

    /** 过期天数阈值（超过删除） */
    private final int maxAgeDays;

    /** 保留目录数量上限（超限删最旧） */
    private final int keepSessions;

    /**
     * 构造清理器。
     *
     * @param sessionsRoot 会话日志根目录（可能不存在）
     * @param maxAgeDays   过期天数阈值
     * @param keepSessions 数量上限
     */
    public SessionRetentionCleaner(Path sessionsRoot, int maxAgeDays, int keepSessions) {
        this.sessionsRoot = sessionsRoot;
        this.maxAgeDays = maxAgeDays;
        this.keepSessions = keepSessions;
    }

    /**
     * 执行清理：过期删除 + 数量上限（幂等，可重复调用）。
     */
    public void clean() {
        if (sessionsRoot == null || !Files.isDirectory(sessionsRoot)) return;
        try (Stream<Path> dirs = Files.list(sessionsRoot)) {
            var sessionDirs =
                    dirs.filter(Files::isDirectory)
                            .sorted(Comparator.comparingLong(SessionRetentionCleaner::mtime))
                            .toList();
            for (Path dir : sessionDirs) {
                if (isExpired(dir)) deleteQuietly(dir);
            }
            // 数量上限：mtime 升序，删除最旧的（跳过刚删的）
            long remaining = countRemaining();
            long toRemove = remaining - keepSessions;
            if (toRemove > 0) {
                try (Stream<Path> again = Files.list(sessionsRoot)) {
                    again.filter(Files::isDirectory)
                            .sorted(Comparator.comparingLong(SessionRetentionCleaner::mtime))
                            .limit(toRemove)
                            .forEach(this::deleteQuietly);
                }
            }
        } catch (IOException e) {
            log.warn("会话日志保留清理失败: {}", e.getMessage());
        }
    }

    private boolean isExpired(Path dir) {
        return mtime(dir) < Instant.now().minus(maxAgeDays, ChronoUnit.DAYS).toEpochMilli();
    }

    private static long mtime(Path dir) {
        try {
            return Files.getLastModifiedTime(dir).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private long countRemaining() {
        try (Stream<Path> dirs = Files.list(sessionsRoot)) {
            return dirs.filter(Files::isDirectory).count();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private void deleteQuietly(Path dir) {
        try {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("删除日志文件失败: {}", p);
                    }
                });
            }
        } catch (IOException e) {
            log.warn("删除日志目录失败: {}", dir);
        }
    }
}
