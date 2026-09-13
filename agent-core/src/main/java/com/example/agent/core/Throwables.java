package com.example.agent.core;

/**
 * 异常分类辅助（improve-failure-observability）。
 *
 * <p>回合执行可能在 Reactor **之外**的边界（订阅侧）捕获到任意 {@code Throwable}。是否该继续、
 * 是否该上报，需要一个统一判据：真正无法继续的只有 JVM 级致命错误。
 *
 * <p>注意与 Reactor {@code Exceptions.throwIfFatal} 的区别——后者把 {@link LinkageError} 也当致命
 * 直接 rethrow，而那恰恰是本项目要**降级上报**的一类失败（工具类加载失败意味"这个工具不可用"，
 * 不是"进程不可用"）。因此这里只认 {@link VirtualMachineError} 与 {@link ThreadDeath}。
 */
public final class Throwables {

    private Throwables() {}

    /**
     * JVM 级致命错误原样抛出；其余异常（含 {@link LinkageError}）正常返回给调用方处理。
     *
     * @param t 待判定异常（{@code null} 时无操作）
     */
    public static void reraiseIfJvmFatal(Throwable t) {
        if (t == null) {
            return;
        }
        if (t instanceof VirtualMachineError vm) {
            throw vm;
        }
        if (t instanceof ThreadDeath td) {
            throw td;
        }
    }

    /**
     * 是否为 JVM 级致命错误（可用于日志文案分类）。
     *
     * @param t 待判定异常（{@code null} 返回 {@code false}）
     * @return {@code true} 表示 {@link #reraiseIfJvmFatal} 会重新抛出
     */
    public static boolean isJvmFatal(Throwable t) {
        return t instanceof VirtualMachineError || t instanceof ThreadDeath;
    }
}
