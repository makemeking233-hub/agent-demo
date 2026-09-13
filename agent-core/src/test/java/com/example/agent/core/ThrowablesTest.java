package com.example.agent.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link Throwables}（improve-failure-observability）：JVM 致命错误与可降级错误的判据。
 *
 * <p>这个判据是可观测性的地基——它决定订阅侧边界能否把 {@code LinkageError} 降级上报。若误把
 * {@code LinkageError} 当致命（照抄 Reactor {@code Exceptions.throwIfFatal} 的语义），
 * 2026-09-13 那类"工具类缺失"会再次无声撕掉会话。
 */
class ThrowablesTest {

    @Test
    void reraisesVirtualMachineError() {
        assertThatThrownBy(() -> Throwables.reraiseIfJvmFatal(new OutOfMemoryError("simulated")))
                .isInstanceOf(OutOfMemoryError.class);
        assertThatThrownBy(() -> Throwables.reraiseIfJvmFatal(new StackOverflowError()))
                .isInstanceOf(StackOverflowError.class);
    }

    @Test
    void doesNotReraiseLinkageError() {
        // 关键：LinkageError 必须留给调用方降级上报，不能照抄 Reactor 的 throwIfFatal
        assertThatCode(
                        () ->
                                Throwables.reraiseIfJvmFatal(
                                        new NoClassDefFoundError(
                                                "com/example/agent/tools/file/EditFileTool$Input")))
                .doesNotThrowAnyException();
    }

    @Test
    void doesNotReraiseOrdinaryExceptions() {
        assertThatCode(() -> Throwables.reraiseIfJvmFatal(new IllegalStateException("boom")))
                .doesNotThrowAnyException();
        assertThatCode(() -> Throwables.reraiseIfJvmFatal(new Error("plain error")))
                .doesNotThrowAnyException();
    }

    @Test
    void nullIsNoop() {
        assertThatCode(() -> Throwables.reraiseIfJvmFatal(null)).doesNotThrowAnyException();
    }

    @Test
    void isJvmFatalClassifies() {
        assertThat(Throwables.isJvmFatal(new OutOfMemoryError())).isTrue();
        assertThat(Throwables.isJvmFatal(new NoClassDefFoundError())).isFalse();
        assertThat(Throwables.isJvmFatal(new RuntimeException())).isFalse();
        assertThat(Throwables.isJvmFatal(null)).isFalse();
    }
}
