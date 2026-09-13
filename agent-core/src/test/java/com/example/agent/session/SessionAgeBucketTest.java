package com.example.agent.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SessionAgeBucket}（auto-archive-stale-sessions）：分档边界。
 *
 * <p>边界是本功能的正确性核心，逐个锁定（左闭右开）。
 */
class SessionAgeBucketTest {

    private static final long NOW = Instant.parse("2026-09-13T12:00:00Z").toEpochMilli();

    private static SessionAgeBucket ageOfDays(long days) {
        return SessionAgeBucket.of(NOW - Duration.ofDays(days).toMillis(), NOW);
    }

    @Test
    void bucketsByRelativeDays() {
        assertThat(ageOfDays(0)).isEqualTo(SessionAgeBucket.RECENT);
        assertThat(ageOfDays(3)).isEqualTo(SessionAgeBucket.RECENT);
        assertThat(ageOfDays(10)).isEqualTo(SessionAgeBucket.LAST_WEEK);
        assertThat(ageOfDays(20)).isEqualTo(SessionAgeBucket.WITHIN_MONTH);
        assertThat(ageOfDays(100)).isEqualTo(SessionAgeBucket.EARLIER);
    }

    @Test
    void boundariesAreLeftClosedRightOpen() {
        // 恰好 7 天 → 上周（右开：7 天不再属于"最近"
        assertThat(ageOfDays(7)).isEqualTo(SessionAgeBucket.LAST_WEEK);
        // 恰好 14 天 → 本月
        assertThat(ageOfDays(14)).isEqualTo(SessionAgeBucket.WITHIN_MONTH);
        // 恰好 30 天 → 更早
        assertThat(ageOfDays(30)).isEqualTo(SessionAgeBucket.EARLIER);
        // 差一毫秒仍在上一档
        assertThat(SessionAgeBucket.of(NOW - Duration.ofDays(7).toMillis() + 1, NOW))
                .isEqualTo(SessionAgeBucket.RECENT);
    }

    @Test
    void manuallyArchivedFreshSessionGoesToRecent() {
        // 手动归档随时可发生：刚聊完就归档，绝不能显示成"上周"
        assertThat(ageOfDays(0)).isEqualTo(SessionAgeBucket.RECENT);
    }

    @Test
    void futureTimestampTreatedAsRecent() {
        // 时钟漂移/文件时间在未来时不应崩到 EARLIER
        assertThat(SessionAgeBucket.of(NOW + Duration.ofDays(2).toMillis(), NOW))
                .isEqualTo(SessionAgeBucket.RECENT);
    }

    @Test
    void keysAreStableForApi() {
        assertThat(
                        List.of(
                                SessionAgeBucket.RECENT.key(),
                                SessionAgeBucket.LAST_WEEK.key(),
                                SessionAgeBucket.WITHIN_MONTH.key(),
                                SessionAgeBucket.EARLIER.key()))
                .containsExactly("recent", "last_week", "within_month", "earlier");
    }
}
