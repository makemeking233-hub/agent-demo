package com.example.agent.core.exception;

/**
 * 单轮工具调用次数超过上限时抛出。 v0.1 上限 = 25（来自 design.md §7 maxToolIterations）。
 */
public class MaxIterationsExceededException extends RuntimeException {
    /**
     * 触发熔断时的迭代次数
     */
    private final int iterations;

    /**
     * @param iterations 触发熔断时的实际迭代次数
     */
    public MaxIterationsExceededException(int iterations) {
        super("工具调用超过最大迭代次数 " + iterations);
        this.iterations = iterations;
    }

    /**
     * @return 触发熔断时的迭代次数
     */
    public int iterations() {
        return iterations;
    }
}
