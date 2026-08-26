package com.example.agent.agent;

/** 上下文压缩连续失败（&gt;=3 次）时抛出；用户应 /clear 清空会话。 */
public class CompactCircuitBrokenException extends RuntimeException {
  /** 构造熔断异常（消息固定为"已熔断"） */
  public CompactCircuitBrokenException() {
    super("上下文压缩连续失败，已熔断；请 /clear 清空会话");
  }
}
