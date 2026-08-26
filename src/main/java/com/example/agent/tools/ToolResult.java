package com.example.agent.tools;

/**
 * 工具执行结果（sealed interface）。
 *
 * <p>{@link Ok} 携带输出 + toolCallId；{@link Err} 携带错误信息。回流给模型前由 AgentLoop 统一截断。
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

  /** 构造成功结果 */
  static <O> ToolResult<O> ok(O output, String toolCallId) {
    return new Ok<>(toolCallId, output, false);
  }

  /** 构造错误结果 */
  static <O> ToolResult<O> error(String message) {
    return new Err<>(null, message, true);
  }

  /**
   * 转成模型可读字符串（tool_result 回流前）。
   *
   * @return 错误时为 {@code [ERROR] <message>}；成功时为 output 字符串
   */
  default String toModelContent() {
    return isError() ? "[ERROR] " + ((Err<?>) this).message() : String.valueOf(output());
  }

  /** 成功结果 record */
  record Ok<O>(String toolCallId, O output, boolean isError) implements ToolResult<O> {}

  /** 错误结果 record */
  record Err<O>(String toolCallId, String message, boolean isError) implements ToolResult<O> {
    @Override
    public O output() {
      return null;
    }
  }
}
