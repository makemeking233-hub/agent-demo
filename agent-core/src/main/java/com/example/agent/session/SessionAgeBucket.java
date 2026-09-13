package com.example.agent.session;

import java.time.Duration;

/**
 * 归档会话的时间分档（auto-archive-stale-sessions）。
 *
 * <p>按「最后活动时间距今的相对天数」分档，与自动归档的保留期**同源**：
 *
 * <ul>
 *   <li>{@link #RECENT}：不足 7 天 —— 手动归档随时可发生，这类会话必须有自己的档，
 *       否则只能塞进「上周」而显示成"上周"却明明是今天。
 *   <li>{@link #LAST_WEEK}：7–14 天
 *   <li>{@link #WITHIN_MONTH}：14–30 天
 *   <li>{@link #EARLIER}：30 天以上
 * </ul>
 *
 * <p>为什么不用自然周/自然月：归档阈值是"7 天"，若分档按自然月，会出现"10 天前的会话（落在上个月）
 * 与半年前的会话同档"的断裂。相对天数与阈值同源、边界连续不重叠。
 *
 * <p>区间语义：左闭右开（恰好 7 天归「上周」，恰好 30 天归「更早」）。
 */
public enum SessionAgeBucket {

    /** 距今不足 7 天。 */
    RECENT("recent", Duration.ofDays(7)),

    /** 距今 7–14 天（用户口中的「上周」）。 */
    LAST_WEEK("last_week", Duration.ofDays(14)),

    /** 距今 14–30 天（用户口中的「本月」）。 */
    WITHIN_MONTH("within_month", Duration.ofDays(30)),

    /** 距今 30 天以上（用户口中的「更早」）。 */
    EARLIER("earlier", null);

    private final String key;
    private final Duration upperBoundExclusive;

    SessionAgeBucket(String key, Duration upperBoundExclusive) {
        this.key = key;
        this.upperBoundExclusive = upperBoundExclusive;
    }

    /**
     * @return 对外暴露的档位标识（JSON 字段值）
     */
    public String key() {
        return key;
    }

    /**
     * 按最后活动时间分档。
     *
     * @param lastModifiedMillis 最后活动时间（会话文件 mtime）
     * @param nowMillis 当前时间
     * @return 所属档位；时间在未来时归 {@link #RECENT}
     */
    public static SessionAgeBucket of(long lastModifiedMillis, long nowMillis) {
        long ageMillis = Math.max(0L, nowMillis - lastModifiedMillis);
        for (SessionAgeBucket bucket : values()) {
            if (bucket.upperBoundExclusive != null
                    && ageMillis < bucket.upperBoundExclusive.toMillis()) {
                return bucket;
            }
        }
        return EARLIER;
    }
}
