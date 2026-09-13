package com.example.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link ToolClassLoadingException}（harden-tool-error-boundary）：类型、消息与 cause 保真。 */
class ToolClassLoadingExceptionTest {

    @Test
    void carriesToolNameStageAndCause() {
        NoClassDefFoundError cause =
                new NoClassDefFoundError("com/example/agent/tools/file/EditFileTool$Input");

        ToolClassLoadingException e =
                new ToolClassLoadingException("EditFile", "入参解析", cause);

        assertThat(e.toolName()).isEqualTo("EditFile");
        assertThat(e.stage()).isEqualTo("入参解析");
        assertThat(e).hasCause(cause);
        // 消息要能直接定位是哪个工具的哪个阶段、缺了哪个类
        assertThat(e.getMessage())
                .contains("EditFile")
                .contains("入参解析")
                .contains("EditFileTool$Input");
    }

    @Test
    void isRuntimeExceptionSoExistingErrorPathCanCatchIt() {
        // 必须是 RuntimeException：AgentLoop 的 onErrorResume 只能接住被 Reactor 转成
        // onError 信号的异常，Checked 异常会强制改签名
        assertThat(new ToolClassLoadingException("t", "s", new ExceptionInInitializerError()))
                .isInstanceOf(RuntimeException.class);
    }
}
