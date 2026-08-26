package com.example.agent;

/**
 * 中断信号：长时工具在 sleep/IO 等待时定期检查这个标志（详见 design.md §17.1）。
 * v0.1 实现：M9 InterruptController 接入 Ctrl+C。
 */
@FunctionalInterface
public interface AbortSignal {
    /**
     * @return true=已中断（工具应立即停止并返回 isError=true）
     */
    boolean isAborted();
}