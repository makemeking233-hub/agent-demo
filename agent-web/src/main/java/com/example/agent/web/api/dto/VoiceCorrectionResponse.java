package com.example.agent.web.api.dto;

/**
 * 语音纠错响应（improve-voice-accuracy T7）。
 *
 * <p>{@code corrected} 为 null 表示后端不可用或纠错失败；前端应保留 rawText 不降级阻塞提交。
 *
 * @param corrected   纠错后的文本（null = 降级，前端用 rawText）
 * @param cached      是否命中 5 分钟缓存
 * @param latencyMs   后端处理耗时（毫秒）
 */
public record VoiceCorrectionResponse(String corrected, boolean cached, long latencyMs) {
    public static VoiceCorrectionResponse of(String corrected) {
        return new VoiceCorrectionResponse(corrected, false, 0L);
    }

    public static VoiceCorrectionResponse cached(String corrected) {
        return new VoiceCorrectionResponse(corrected, true, 0L);
    }
}