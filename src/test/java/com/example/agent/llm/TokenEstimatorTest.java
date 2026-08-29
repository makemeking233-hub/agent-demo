package com.example.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenEstimatorTest {
    private final TokenEstimator estimator = new TokenEstimator();

    @Test
    void estimatesEnglishText() {
        int t = estimator.estimate("Hello, world!");
        assertTrue(t > 0 && t < 10, "应为 1~9 tokens");
    }

    @Test
    void estimatesChineseText() {
        int t = estimator.estimate("你好，世界！");
        // 中文每个字大约 1 token（o200k_base）
        assertTrue(t >= 4 && t <= 8, "中文 token 估算应在 4~8 范围");
    }

    @Test
    void emptyStringReturnsZero() {
        assertEquals(0, estimator.estimate(""));
    }

    @Test
    void nullReturnsZero() {
        assertEquals(0, estimator.estimate(null));
    }
}
