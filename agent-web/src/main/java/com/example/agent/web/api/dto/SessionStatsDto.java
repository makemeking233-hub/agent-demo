package com.example.agent.web.api.dto;

import com.example.agent.stats.SessionStats;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /api/sessions/{id}/stats 响应（add-session-stats-bar）。
 *
 * <p>字段名与 SSE {@code turn_stats} 事件保持一致（snake_case），派生指标不可用时为 {@code null}。
 */
public record SessionStatsDto(
        @JsonProperty("turns") long turns,
        @JsonProperty("steps") long steps,
        @JsonProperty("tokens_in") long tokensIn,
        @JsonProperty("tokens_out") long tokensOut,
        @JsonProperty("llm_ms") long llmMs,
        @JsonProperty("tool_ms") long toolMs,
        @JsonProperty("avg_ttft_ms") Double avgTtftMs,
        @JsonProperty("tok_per_sec") Double tokPerSec,
        @JsonProperty("cache_hit_rate") Double cacheHitRate) {

    /** 由领域统计构造。 */
    public static SessionStatsDto from(SessionStats s) {
        SessionStats v = s != null ? s : SessionStats.empty();
        return new SessionStatsDto(
                v.turns(),
                v.steps(),
                v.tokensIn(),
                v.tokensOut(),
                v.llmMillis(),
                v.toolMillis(),
                v.avgTtftMs(),
                v.tokPerSec(),
                v.cacheHitRate());
    }
}
