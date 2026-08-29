package com.example.agent.tools;

/**
 * 工具执行结果（sealed interface）。
 *
 * <p>{@link Ok} 携带输出 + toolCallId；{@link Err} 携带错误信息。回流给模型前由 AgentLoop 统一截断。
 *
 * <p>{@link #toModelContent()} 在两个 record 中各自实现，避免默认方法内的 instanceof 强转。
 *
 * @param <O> 输出类型
 */
public sealed interface ToolResult<O> {
    /** 关联的 toolCallId（用于回流给模型时关联） */
    String toolCallId();

    /** 工具输出（Err 时为 null） */
    O output();

    /** 是否为错误结果 */
    boolean isError();

    /**
     * 转成模型可读字符串（tool_result 回流前）。
     *
     * @return 成功时为 {@code String.valueOf(output)}；错误时为 {@code "[ERROR] <message>"}
     */
    String toModelContent();

    /** 构造成功结果（toolCallId 为 {@code null}；回流给模型前由 AgentLoop 补全调用 id） */
    static <O> ToolResult<O> ok(O output) {
        return new Ok<>(null, output, false);
    }

    /** 构造成功结果 */
    static <O> ToolResult<O> ok(O output, String toolCallId) {
        return new Ok<>(toolCallId, output, false);
    }

    /** 构造错误结果（toolCallId 为 {@code null}；回流给模型前由 AgentLoop 补全调用 id） */
    static <O> ToolResult<O> error(String message) {
        return new Err<>(null, message, true);
    }

    /**
     * 构造错误结果（带调用 id）。
     *
     * <p>回流给模型的 {@code tool_result} 消息要求 {@code tool_call_id} 非空（DeepSeek 对 null
     * 返回 400），工具内部产生的 error 结果由 AgentLoop 用本工厂补全。
     *
     * @param toolCallId 关联的工具调用 ID
     * @param message 错误信息
     */
    static <O> ToolResult<O> error(String toolCallId, String message) {
        return new Err<>(toolCallId, message, true);
    }

    /**
     * 成功结果。
     *
     * @param toolCallId 关联的工具调用 ID（用于回流给模型时匹配）
     * @param output 工具输出
     * @param isError 恒为 {@code false}
     */
    record Ok<O>(String toolCallId, O output, boolean isError) implements ToolResult<O> {
        @Override
        public String toModelContent() {
            return String.valueOf(output);
        }
    }

    /**
     * 错误结果。
     *
     * @param toolCallId 关联的工具调用 ID（{@link #error(String)} 构造时为 {@code null}）
     * @param message 错误信息
     * @param isError 恒为 {@code true}
     */
    record Err<O>(String toolCallId, String message, boolean isError) implements ToolResult<O> {
        @Override
        public O output() {
            return null;
        }

        @Override
        public String toModelContent() {
            return "[ERROR] " + message;
        }
    }
}
