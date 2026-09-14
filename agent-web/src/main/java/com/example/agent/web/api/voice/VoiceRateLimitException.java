package com.example.agent.web.api.voice;

/**
 * 语音纠错端点 sessionId 限流异常（improve-voice-accuracy T7.5）。
 *
 * <p>Controller 捕获后返回 HTTP 429 Too Many Requests，前端 fetch 降级用 rawText 提交。
 */
public class VoiceRateLimitException extends RuntimeException {
    public VoiceRateLimitException(String message) {
        super(message);
    }
}