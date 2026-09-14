package com.example.agent.web.api.dto;

import java.util.List;

/**
 * 语音纠错请求载荷（improve-voice-accuracy T7）。
 *
 * <p>前端在用户提交 partial / final 后异步触发此端点；后端调用 DeepSeek 对 {@code rawText}
 * 做语义纠错（结合近几轮对话上下文）。后端默认启用 5 分钟响应缓存以避免重复纠错同一段文字。
 *
 * @param rawText      Vosk 原始识别文本（必填，去首尾空白后非空）
 * @param sessionId    会话 id（必填，限流维度）
 * @param recentTurns  最近几轮对话上下文（用于消歧；可为空列表但不能为 null）
 */
public record VoiceCorrectionRequest(String rawText, String sessionId, List<RecentTurn> recentTurns) {
    /**
     * 单轮对话上下文（用户消息 + 助手回复对）。
     *
     * @param user      用户说的话（原始，未纠错）
     * @param assistant 助手回复（可空 = 该轮助手未回复）
     */
    public record RecentTurn(String user, String assistant) {}
}