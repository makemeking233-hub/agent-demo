/**
 * 语音后处理 API 客户端（improve-voice-accuracy T3.5）。
 *
 * <p>封装对后端 {@code POST /api/chat/voice-correction} 的调用：发送 Vosk final 文本 +
 * 会话最近 N 轮，返回 DeepSeek 纠错后文本。
 *
 * <p>纯前端 fetcher；不引入 axios / ky 等额外依赖。
 */

export interface CorrectVoiceTextRequest {
  /** Vosk 原始 final 文本 */
  rawText: string;
  /** 当前会话 id（后端按会话做 5 分钟缓存 + 令牌桶限流） */
  sessionId: string;
  /** 会话最近 N 轮（user + assistant 交替），给 LLM 做纠错上下文；可空 */
  recentTurns: Array<{ role: "user" | "assistant"; content: string }>;
  /** 超时（毫秒）；默认 2000ms */
  timeoutMs?: number;
}

export interface CorrectVoiceTextResponse {
  /** 纠错后文本（LLM 降级时为原始 rawText） */
  corrected: string;
  /** 是否命中后端 5 分钟缓存 */
  cached: boolean;
}

/**
 * 调用 {@code POST /api/chat/voice-correction}（improve-voice-accuracy T3.5）。
 *
 * <p>错误处理：
 * <ul>
 *   <li>超时 → 抛 AbortError</li>
 *   <li>5xx → 抛 Error(message)</li>
 *   <li>4xx → 抛 Error(message)</li>
 *   <li>网络错 → 抛 TypeError</li>
 * </ul>
 *
 * @throws AbortError 超时；Error HTTP 非 2xx；TypeError 网络错误
 */
export async function correctVoiceText(
  req: CorrectVoiceTextRequest,
): Promise<string> {
  const controller = new AbortController();
  const timeoutMs = req.timeoutMs ?? 2000;
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch("/api/chat/voice-correction", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        rawText: req.rawText,
        sessionId: req.sessionId,
        recentTurns: req.recentTurns ?? [],
      }),
      signal: controller.signal,
    });
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`voice_correction_http_${res.status}: ${text}`);
    }
    const data = (await res.json()) as CorrectVoiceTextResponse;
    return data.corrected;
  } finally {
    clearTimeout(timer);
  }
}
