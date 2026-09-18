package com.example.agent.web.wecom;

/**
 * 企业微信 API 可重试异常（add-wecom-channel task 3.1）。
 *
 * <p>当前触发条件：errcode=45009（API 频次超限）。
 * 调用方应按 {@code reply.retry-backoff-ms} 退避后重试。
 */
public class RetryableWecomException extends RuntimeException {

    public RetryableWecomException(String message) {
        super(message);
    }

    public RetryableWecomException(String message, Throwable cause) {
        super(message, cause);
    }
}
