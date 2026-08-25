package com.example.agent.agent;

/** 上下文压缩连续失败（>=3 次）时抛出；用户应 /clear 清空会话。 */
public class CompactCircuitBrokenException extends RuntimeException {
    public CompactCircuitBrokenException() {
        super("上下文压缩连续失败，已熔断；请 /clear 清空会话");
    }
}