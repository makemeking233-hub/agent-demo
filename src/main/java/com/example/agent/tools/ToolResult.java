package com.example.agent.tools;

/**
 * 工具执行结果（sealed interface）。
 *
 * <p>Ok&lt;O&gt; 携带输出 + toolCallId；Err&lt;O&gt; 携带错误信息。回流给模型前由 AgentLoop 统一截断。
 */
public sealed interface ToolResult<O> {
    String toolCallId();
    O output();
    boolean isError();

    static <O> ToolResult<O> ok(O output, String toolCallId) {
        return new Ok<>(toolCallId, output, false);
    }

    static <O> ToolResult<O> error(String message) {
        return new Err<>(null, message, true);
    }

    /** 转成模型可读字符串（tool_result 回流前） */
    default String toModelContent() {
        return isError() ? "[ERROR] " + ((Err<?>) this).message() : String.valueOf(output());
    }

    record Ok<O>(String toolCallId, O output, boolean isError) implements ToolResult<O> {}

    record Err<O>(String toolCallId, String message, boolean isError) implements ToolResult<O> {
        @Override public O output() { return null; }
    }
}