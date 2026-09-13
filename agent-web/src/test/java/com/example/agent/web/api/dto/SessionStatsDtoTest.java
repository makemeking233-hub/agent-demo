package com.example.agent.web.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agent.stats.SessionStats;
import com.example.agent.stats.TurnDelta;
import org.junit.jupiter.api.Test;

/** SessionStatsDto（add-session-stats-bar）：领域统计 → DTO，含 null 兜底分支。 */
class SessionStatsDtoTest {

    @Test
    void mapsDomainStats() {
        SessionStats s =
                SessionStats.empty()
                        .plus(new TurnDelta(2, 100, 50, 0, 2000, 300, 500, 1, 80, 20));
        SessionStatsDto dto = SessionStatsDto.from(s);
        assertThat(dto.turns()).isEqualTo(1);
        assertThat(dto.steps()).isEqualTo(2);
        assertThat(dto.tokensIn()).isEqualTo(100);
        assertThat(dto.tokensOut()).isEqualTo(50);
        assertThat(dto.llmMs()).isEqualTo(2000);
        assertThat(dto.toolMs()).isEqualTo(300);
        assertThat(dto.avgTtftMs()).isEqualTo(500.0);
        assertThat(dto.cacheHitRate()).isEqualTo(0.8);
    }

    @Test
    void nullStatsFallsBackToEmpty() {
        SessionStatsDto dto = SessionStatsDto.from(null);
        assertThat(dto.turns()).isZero();
        assertThat(dto.avgTtftMs()).isNull();
        assertThat(dto.tokPerSec()).isNull();
        assertThat(dto.cacheHitRate()).isNull();
    }
}
