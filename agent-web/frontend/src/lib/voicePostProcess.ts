/**
 * 语音识别后处理（improve-voice-accuracy T3）。
 *
 * <p>Vosk 离线小模型对长句、专有名词、口音识别率有限；助手朗读用户错听后的内容又会被 Vosk
 * 听回来叠加污染。本模块在 Vosk final 文本送进 useVoiceChat.onFinal 之前，做三道轻量处理：
 *
 * <ol>
 *   <li>{@link filterShort}：丢掉 <2 字或纯语气词的识别结果（如"嗯""啊"），避免空触发提交。</li>
 *   <li>{@link dedupeRepeats}：检测连续重复模式（如"凶手凶手凶手" → "凶手"），降低 Vosk 句末渐进修正错误。</li>
 *   <li>{@link contextCorrect}：调后端 {@code /api/chat/voice-correction} 用 DeepSeek 做语义级纠错，
 *       结合会话最近 3 轮上下文。失败时降级到原始文本（不阻断主流程）。</li>
 * </ol>
 *
 * <p>配置开关 {@code voice.postProcess.enabled=false} 时 {@link contextCorrect} 直接返回原始
 * 文本，跳过 API 调用。
 */

import { correctVoiceText } from "./VoiceApi";

/** 纯语气词集合（这些词不作为提交触发）。 */
const FILLER_WORDS = new Set(["嗯", "啊", "哦", "呃", "唉", "哎", "欸", "呵"]);

/**
 * 过滤短文本或纯语气词（improve-voice-accuracy T3.3）。
 *
 * <p>阈值：归一化后 ≤ 1 字 或 全部字符都在 {@link FILLER_WORDS} 里 → 返回 null（应丢弃）。
 *
 * @param raw Vosk 原始 final 文本（未 trim）
 * @returns 保留的文本；null 表示应丢弃
 */
export function filterShort(raw: string | null | undefined): string | null {
  if (raw == null) return null;
  const trimmed = raw.trim();
  if (trimmed.length < 2) return null;
  // 检查是否全部是语气词（单字符遍历）
  let allFiller = true;
  for (const ch of trimmed) {
    if (!FILLER_WORDS.has(ch)) {
      allFiller = false;
      break;
    }
  }
  return allFiller ? null : trimmed;
}

/**
 * 去连续重复模式（improve-voice-accuracy T3.1）。
 *
 * <p>Vosk 句末渐进修正经常出现"重复 + 扩展"模式（如"凶手" → "凶手凶手" → "凶手凶手凶手升级"），
 * 这种重复通常是识别错误。检测连续 ≥ 2 次重复的子串并截断到只保留 1 次。
 *
 * <p>算法：
 * <ul>
 *   <li>检测从位置 0 开始，长度 ≥ 2 的最长前缀，其后紧跟 ≥ 1 次相同内容重复</li>
 *   <li>将整个输入截到「1 次该前缀 + 后续非重复部分」</li>
 * </ul>
 *
 * <p>示例：
 * <ul>
 *   <li>"凶手凶手凶手" → "凶手"</li>
 *   <li>"凶手凶手升级" → "凶手升级"</li>
 *   <li>"现在看再正常不过了" → "现在看再正常不过了"（无重复，不动）</li>
 * </ul>
 *
 * @param raw Vosk 原始文本（已 trim）
 * @returns 去重后的文本；若无重复则原样返回
 */
export function dedupeRepeats(raw: string): string {
  if (!raw) return raw;
  const len = raw.length;
  // 检测最长重复前缀 + 连续重复次数。长度从 len/2 递减（防止 prefixLen 超过 len/2 时一定不会重复）
  for (let prefixLen = Math.floor(len / 2); prefixLen >= 1; prefixLen--) {
    const prefix = raw.substring(0, prefixLen);
    // 从位置 prefixLen 起扫描，统计 prefix 连续重复了多少次（直到不匹配或到末尾）
    let repeatCount = 0;
    let pos = prefixLen;
    while (pos + prefixLen <= len && raw.substring(pos, pos + prefixLen) === prefix) {
      repeatCount++;
      pos += prefixLen;
    }
    if (repeatCount >= 1) {
      // 至少 1 次重复：截到「1 次 prefix + 剩余非重复部分」。
      // 单次扫描处理多次重复（如"凶手凶手凶手" → "凶手"，repeatCount=2）。
      return prefix + raw.substring(pos);
    }
  }
  return raw;
}

/** `contextCorrect` 的可配置项。 */
export interface ContextCorrectOptions {
  /** 是否启用（false 时直接返回 raw，不调 API） */
  enabled: boolean;
  /** 会话最近 N 轮（user + assistant 交替），给 LLM 做纠错上下文 */
  recentTurns?: Array<{ role: "user" | "assistant"; content: string }>;
  /** API 超时（毫秒）；默认 2000ms */
  timeoutMs?: number;
}

/**
 * 调后端 `/api/chat/voice-correction` 用 DeepSeek 纠错（improve-voice-accuracy T3.2）。
 *
 * <p>降级路径：
 * <ul>
 *   <li>{@code enabled=false} → 直接返回 raw，不调 API</li>
 *   <li>fetch 超时（AbortError）→ 返回 raw</li>
 *   <li>HTTP 5xx → 返回 raw</li>
 *   <li>4xx（参数错误）→ 返回 raw</li>
 *   <li>网络错误 → 返回 raw</li>
 * </ul>
 *
 * <p>不抛异常——语音流程不能让 LLM 纠错拖后腿。
 *
 * @param raw Vosk 原始文本（已 dedupeRepeats + filterShort 通过）
 * @param sessionId 当前会话 id
 * @param opts 配置 + 上下文
 * @returns 纠错后文本（失败时为 raw 原值）
 */
export async function contextCorrect(
  raw: string,
  sessionId: string,
  opts: ContextCorrectOptions,
): Promise<string> {
  if (!opts.enabled) return raw;
  if (!raw) return raw;
  try {
    const corrected = await correctVoiceText({
      rawText: raw,
      sessionId,
      recentTurns: opts.recentTurns ?? [],
      timeoutMs: opts.timeoutMs ?? 2000,
    });
    return corrected || raw;
  } catch {
    // 降级：超时 / 5xx / 网络错 / 4xx 都返回原文
    return raw;
  }
}
