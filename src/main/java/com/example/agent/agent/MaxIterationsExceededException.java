package com.example.agent.agent;

/**
 * 单轮工具调用次数超过上限时抛出。
 * v0.1 上限 = 25（来自 design.md §7 maxToolIterations）。
 */
public class MaxIterationsExceededException extends RuntimeException {
    private final int iterations;

    public MaxIterationsExceededException(int iterations) {
        super("工具调用超过最大迭代次数 " + iterations);
        this.iterations = iterations;
    }

    public int iterations() { return iterations; }
}