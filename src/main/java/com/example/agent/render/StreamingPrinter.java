package com.example.agent.render;

/** v0.1 简化版流式打印机：把流式 chunk 直接打到 stdout。 v0.2 升级为 INLINE / CODE_FENCE 两态状态机（详见 design.md §2.3）。 */
public class StreamingPrinter {
  /**
   * 打印增量文本（不换行）。
   *
   * @param text 文本片段
   */
  public void onTextDelta(String text) {
    System.out.print(text);
  }

  /**
   * 工具调用开始：换行 + 打印 {@code [tool] <name> }。
   *
   * @param id 工具调用 ID
   * @param name 工具名
   */
  public void onToolCallStart(String id, String name) {
    System.out.println();
    System.out.print("[tool] " + name + " ");
  }

  /**
   * 工具调用参数增量（流式 JSON 片段，不换行）。
   *
   * @param id 工具调用 ID
   * @param argsDelta 参数 JSON 片段
   */
  public void onToolCallArgs(String id, String argsDelta) {
    System.out.print(argsDelta);
  }

  /**
   * 工具调用结束：换行 + 打印参数。
   *
   * @param id 工具调用 ID
   * @param name 工具名
   * @param args 完整参数 JSON
   */
  public void onToolCallEnd(String id, String name, String args) {
    System.out.println();
    System.out.println("  args: " + args);
  }

  /** 模型流结束：换行 */
  public void onFinished() {
    System.out.println();
  }

  /**
   * 错误输出（写到 stderr）。
   *
   * @param message 错误信息
   */
  public void onError(String message) {
    System.err.println("\n[error] " + message);
  }
}
