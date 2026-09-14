package com.example.agent.web.api.voice;

import com.example.agent.web.api.dto.VoiceCorrectionRequest;
import com.example.agent.web.api.dto.VoiceCorrectionResponse;

/**
 * 语音纠错服务接口（improve-voice-accuracy T7）。
 *
 * <p>实现位于 {@link DeepSeekVoiceCorrectionService}；Controller 通过此接口解耦，
 * 测试时可注入 stub 覆盖 LLM 调用路径。
 */
public interface VoiceCorrectionService {
    /**
     * 纠错返回 corrected text。
     *
     * <p>行为契约：
     * <ul>
     *   <li>DeepSeek 返回有效文本 → 写入 5 分钟缓存，返回 corrected
     *   <li>DeepSeek 超时 / 5xx / 空响应 → 走降级，返回 {@code corrected=null}
     *   <li>5 分钟内同 (rawText, recentTurns 哈希) → 命中缓存
     *   <li>超过 sessionId 令牌桶限流（5 req/s）→ 抛 {@link VoiceRateLimitException}
     * </ul>
     *
     * @param req 纠错请求
     * @return 响应（{@code corrected} 可能为 null 表示降级）
     * @throws VoiceRateLimitException 超限（前端 fetch 收到 429）
     */
    VoiceCorrectionResponse correct(VoiceCorrectionRequest req);
}