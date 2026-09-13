package com.example.agent.stats;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话级累计统计（add-session-stats-bar）。
 *
 * <p>累计口径：
 *
 * <ul>
 *   <li>{@code turns} 回合数（每次 {@code processTurn} +1）
 *   <li>{@code steps} 工具调用次数（含失败）
 *   <li>{@code tokensIn}/{@code tokensOut}/{@code reasoningTokens} 累计 token
 *   <li>{@code llmMillis} 累计 {@code streamChat} 区间耗时；{@code toolMillis} 累计工具执行耗时
 *   <li>{@code ttftMillis}/{@code ttftSamples} 首 token 延迟累计与样本数
 *   <li>{@code cacheHitTokens}/{@code cacheMissTokens} 前缀缓存命中/未命中；{@code cacheSeen}
 *       标记是否曾收到过缓存字段（为 false 时缓存命中率按 N/A 处理）
 * </ul>
 *
 * <p>派生指标（不可用返回 {@code null}）：
 *
 * <ul>
 *   <li>{@code avgTtftMs = ttftMillis / ttftSamples}
 *   <li>{@code tokPerSec = tokensOut / (纯生成耗时秒)}，纯生成耗时 = {@code (llmMillis - ttftMillis)}
 *   <li>{@code cacheHitRate = cacheHitTokens / (cacheHitTokens + cacheMissTokens)}
 * </ul>
 *
 * @param turns 回合数
 * @param steps 工具调用次数
 * @param tokensIn 累计 prompt token
 * @param tokensOut 累计 completion token（不含 reasoning）
 * @param reasoningTokens 累计 reasoning token
 * @param llmMillis 累计 LLM 耗时（毫秒）
 * @param toolMillis 累计工具耗时（毫秒）
 * @param ttftMillis 首 token 延迟累计（毫秒）
 * @param ttftSamples 首 token 延迟样本数
 * @param cacheHitTokens 累计缓存命中 token
 * @param cacheMissTokens 累计缓存未命中 token
 * @param cacheSeen 是否曾收到缓存字段（决定命中率是否 N/A）
 */
public record SessionStats(
        long turns,
        long steps,
        long tokensIn,
        long tokensOut,
        long reasoningTokens,
        long llmMillis,
        long toolMillis,
        long ttftMillis,
        long ttftSamples,
        long cacheHitTokens,
        long cacheMissTokens,
        boolean cacheSeen) {

    /** 全零初始值。 */
    public static SessionStats empty() {
        return new SessionStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    /**
     * 累加一轮增量：{@code turns + 1}，其余字段按 {@link TurnDelta} 相加。
     *
     * @param d 本轮增量（不可空）
     * @return 累加后的新实例
     */
    public SessionStats plus(TurnDelta d) {
        if (d == null) return this;
        boolean seen = cacheSeen || d.cacheHitTokens() != null || d.cacheMissTokens() != null;
        return new SessionStats(
                turns + 1,
                steps + d.steps(),
                tokensIn + d.tokensIn(),
                tokensOut + d.tokensOut(),
                reasoningTokens + d.reasoningTokens(),
                llmMillis + d.llmMillis(),
                toolMillis + d.toolMillis(),
                ttftMillis + d.ttftMillis(),
                ttftSamples + d.ttftSamples(),
                cacheHitTokens + (d.cacheHitTokens() == null ? 0 : d.cacheHitTokens()),
                cacheMissTokens + (d.cacheMissTokens() == null ? 0 : d.cacheMissTokens()),
                seen);
    }

    /** 首 token 平均延迟（毫秒）；无样本返回 {@code null}。 */
    public Double avgTtftMs() {
        return ttftSamples == 0 ? null : (double) ttftMillis / ttftSamples;
    }

    /**
     * 输出吞吐（token/秒）：分母为<b>纯生成耗时</b>（{@code llmMillis - ttftMillis}），贴近真实解码速率。
     * 分母 ≤ 0 时返回 {@code null}。
     */
    public Double tokPerSec() {
        long genMillis = llmMillis - ttftMillis;
        return genMillis <= 0 ? null : tokensOut * 1000.0 / genMillis;
    }

    /** 缓存命中率（0~1）；从未收到缓存字段或总数为 0 时返回 {@code null}（前端显示 N/A）。 */
    public Double cacheHitRate() {
        if (!cacheSeen) return null;
        long total = cacheHitTokens + cacheMissTokens;
        return total == 0 ? null : (double) cacheHitTokens / total;
    }

    /** 序列化为侧车 JSON 字段（保持字段名稳定，供落盘与 API 复用）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("turns", turns);
        m.put("steps", steps);
        m.put("tokens_in", tokensIn);
        m.put("tokens_out", tokensOut);
        m.put("reasoning_tokens", reasoningTokens);
        m.put("llm_ms", llmMillis);
        m.put("tool_ms", toolMillis);
        m.put("ttft_ms", ttftMillis);
        m.put("ttft_samples", ttftSamples);
        m.put("cache_hit_tokens", cacheHitTokens);
        m.put("cache_miss_tokens", cacheMissTokens);
        m.put("cache_seen", cacheSeen);
        return m;
    }

    /**
     * 从侧车 JSON map 还原（缺字段视为 0；整份为 null 时返回 {@link #empty()}）。
     *
     * @param m 反序列化后的 map（可空）
     * @return 还原的统计
     */
    public static SessionStats fromMap(Map<String, Object> m) {
        if (m == null) return empty();
        return new SessionStats(
                asLong(m.get("turns")),
                asLong(m.get("steps")),
                asLong(m.get("tokens_in")),
                asLong(m.get("tokens_out")),
                asLong(m.get("reasoning_tokens")),
                asLong(m.get("llm_ms")),
                asLong(m.get("tool_ms")),
                asLong(m.get("ttft_ms")),
                asLong(m.get("ttft_samples")),
                asLong(m.get("cache_hit_tokens")),
                asLong(m.get("cache_miss_tokens")),
                Boolean.TRUE.equals(m.get("cache_seen")));
    }

    private static long asLong(Object o) {
        return (o instanceof Number n) ? n.longValue() : 0L;
    }
}
