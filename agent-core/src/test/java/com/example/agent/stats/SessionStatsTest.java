package com.example.agent.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.Map;

/** SessionStats（add-session-stats-bar）：累加 + 派生指标 + 边界。 */
class SessionStatsTest {

    private static TurnDelta delta(long steps, long in, long out, long llm, long tool, long ttft, long samples) {
        return new TurnDelta(steps, in, out, 0, llm, tool, ttft, samples, null, null);
    }

    @Test
    void emptyHasZeroAndNullDerived() {
        SessionStats s = SessionStats.empty();
        assertEquals(0, s.turns());
        assertNull(s.avgTtftMs());
        assertNull(s.tokPerSec());
        assertNull(s.cacheHitRate());
    }

    @Test
    void plusAccumulatesTurnsAndFields() {
        SessionStats s = SessionStats.empty().plus(delta(2, 100, 50, 1000, 300, 200, 1));
        assertEquals(1, s.turns());
        assertEquals(2, s.steps());
        assertEquals(100, s.tokensIn());
        assertEquals(50, s.tokensOut());
        assertEquals(1000, s.llmMillis());
        assertEquals(300, s.toolMillis());

        s = s.plus(delta(1, 10, 5, 500, 100, 100, 1));
        assertEquals(2, s.turns());
        assertEquals(3, s.steps());
        assertEquals(110, s.tokensIn());
        assertEquals(55, s.tokensOut());
        assertEquals(1500, s.llmMillis());
        assertEquals(400, s.toolMillis());
    }

    @Test
    void avgTtftIsMeanOverSamples() {
        SessionStats s = SessionStats.empty().plus(delta(0, 0, 0, 1000, 0, 200, 1)).plus(delta(0, 0, 0, 1000, 0, 400, 1));
        assertEquals(300.0, s.avgTtftMs(), 1e-9);
    }

    @Test
    void tokPerSecUsesPureGenerationTime() {
        // llm=2000, ttft=500 → 生成耗时 1500ms；tokensOut=300 → 200 tok/s
        SessionStats s = SessionStats.empty().plus(delta(0, 0, 300, 2000, 0, 500, 1));
        assertEquals(200.0, s.tokPerSec(), 1e-9);
    }

    @Test
    void tokPerSecNullWhenGenerationTimeNonPositive() {
        SessionStats s = SessionStats.empty().plus(delta(0, 0, 10, 100, 0, 100, 1));
        assertNull(s.tokPerSec());
    }

    @Test
    void cacheHitRateNullWhenNeverSeen() {
        SessionStats s = SessionStats.empty().plus(delta(0, 0, 0, 1, 0, 0, 0));
        assertNull(s.cacheHitRate());
    }

    @Test
    void cacheHitRateComputedWhenSeen() {
        TurnDelta d = new TurnDelta(0, 100, 10, 0, 1000, 0, 100, 1, 80, 20);
        SessionStats s = SessionStats.empty().plus(d);
        assertEquals(0.8, s.cacheHitRate(), 1e-9);
        assertEquals(Integer.valueOf(80), 80);
    }

    @Test
    void cacheSeenStaysTrueWithPartialFields() {
        TurnDelta onlyHit = new TurnDelta(0, 10, 1, 0, 100, 0, 10, 1, 5, null);
        SessionStats s = SessionStats.empty().plus(onlyHit);
        assertEquals(1.0, s.cacheHitRate(), 1e-9);
    }

    @Test
    void mapRoundTrip() {
        SessionStats s = SessionStats.empty()
                .plus(delta(2, 100, 50, 1000, 300, 200, 1))
                .plus(new TurnDelta(1, 10, 5, 0, 500, 100, 100, 1, 8, 2));
        Map<String, Object> m = s.toMap();
        SessionStats back = SessionStats.fromMap(m);
        assertEquals(s.turns(), back.turns());
        assertEquals(s.steps(), back.steps());
        assertEquals(s.tokensIn(), back.tokensIn());
        assertEquals(s.cacheHitTokens(), back.cacheHitTokens());
        assertEquals(s.cacheSeen(), back.cacheSeen());
        assertEquals(s.cacheHitRate(), back.cacheHitRate());
    }

    @Test
    void fromMapHandlesNullAndMissing() {
        assertEquals(0, SessionStats.fromMap(null).turns());
        assertEquals(0, SessionStats.fromMap(Map.of()).turns());
        assertNull(SessionStats.fromMap(Map.of()).cacheHitRate());
    }
}
