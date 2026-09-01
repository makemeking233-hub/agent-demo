/**
 * 语音播放封装（add-voice-interaction）：浏览器 speechSynthesis 朗读助手文本，支持静音/打断。
 */

/** 朗读器接口（便于测试注入 mock）。 */
export interface VoiceReader {
  /** 朗读一段文本（追加到队列）。 */
  speak(text: string): void;
  /** 打断当前朗读（静音/停止时调用）。 */
  cancel(): void;
  /** 是否静音。 */
  readonly muted: boolean;
  /** 设置静音；设为 true 时立即打断。 */
  setMuted(muted: boolean): void;
}

/** 创建基于浏览器 speechSynthesis 的朗读器。 */
export function createVoice(defaultMuted = false): VoiceReader {
  let muted = defaultMuted;
  const synth: SpeechSynthesis | undefined =
    typeof window !== "undefined" ? window.speechSynthesis : undefined;

  return {
    speak(text) {
      if (muted || !text || !synth) return;
      try {
        const u = new SpeechSynthesisUtterance(text);
        u.lang = "zh-CN";
        synth.speak(u);
      } catch {
        /* 朗读失败不阻断 */
      }
    },
    cancel() {
      try {
        synth?.cancel();
      } catch {
        /* ignore */
      }
    },
    get muted() {
      return muted;
    },
    setMuted(m) {
      muted = m;
      if (m) synth?.cancel();
    },
  };
}
