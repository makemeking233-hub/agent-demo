import { useCallback, useRef, useState } from "react";
import type { VoiceReader } from "./voice";
import type { Stt } from "./stt";

export type VoiceState = "idle" | "loading" | "listening" | "sending";

/**
 * 朗读结束到重新开麦之间的静音余量（毫秒）。
 *
 * <p>覆盖扬声器混响尾巴：TTS 的 onend 触发时房间里往往还有余音，立刻开麦会被识别到。
 */
export const ECHO_GUARD_MS = 400;

export interface UseVoiceOptions {
  /** 异步获取 STT 实例（Vosk 需先加载模型，故用工厂 + 缓存）。 */
  getStt: () => Promise<Stt>;
  voice: VoiceReader;
  /** 把一句语音文本作为用户消息提交。 */
  onSubmit: (text: string) => void;
  /** 当前是否可提交（如非 busy）。 */
  canSubmit: () => boolean;
}

/**
 * 自由语音对话循环（add-voice-interaction）：监听 → final → 提交 → 朗读回复 → 再监听。
 * 与手动打字共存；stop() 停止监听与朗读。
 */
export function useVoiceChat({ getStt, voice, onSubmit, canSubmit }: UseVoiceOptions) {
  const [state, setState] = useState<VoiceState>("idle");
  const sttRef = useRef<Stt | null>(null);
  const onFinalRef = useRef<(t: string) => void>(() => {});
  const runningRef = useRef(false);

  const onFinal = useCallback(
    (text: string) => {
      const t = text.trim();
      if (!t || !canSubmit()) return;
      // 回声防护（improve-voice-readout）：朗读期间收到的识别结果多半是助手自己的声音
      // （扬声器 → 麦克风）。麦克风本应在朗读期间关闭，这里是第二道保险，防止时序缝隙
      // 把助手的话当成用户输入提交、进而形成自我循环。
      if (voice.isSpeaking()) return;
      // 只暂停监听（本轮处理中不捕捉声音），但循环仍“武装”，每轮结束由 onTurnEnd 恢复监听
      sttRef.current?.stop();
      setState("sending");
      onSubmit(t);
    },
    [onSubmit, canSubmit, voice],
  );
  onFinalRef.current = onFinal;

  /** 开始（或恢复）自由语音：懒加载 STT 并开始监听。 */
  const start = useCallback(async () => {
    runningRef.current = true;
    setState("loading");
    try {
      const stt = (sttRef.current ??= await getStt());
      setState("listening");
      await stt.start((t) => onFinalRef.current(t));
    } catch (e) {
      runningRef.current = false;
      setState("idle");
      throw e;
    }
  }, [getStt]);

  /** 停止监听与朗读。 */
  const stop = useCallback(() => {
    runningRef.current = false;
    sttRef.current?.stop();
    voice.cancel();
    setState("idle");
  }, [voice]);

  /** SSE 流式助手文本到达时朗读（在自由语音循环中）。 */
  const onAssistantDelta = useCallback(
    (text: string) => {
      if (text && runningRef.current) voice.speak(text);
    },
    [voice],
  );

  /**
   * 本轮结束：先把朗读尾巴读掉，**等播放彻底结束**再重新监听。
   *
   * <p>回声防护的核心（improve-voice-readout）：此前这里是立刻 `stt.start()`，而 TTS 队列还在播，
   * 麦克风开着 → Vosk 把助手自己的声音识别成用户输入 → 提交成新一轮 → 助手回复自己，形成循环。
   * 现在改为等 `voice.waitUntilIdle()` 并额外留一小段静音余量（覆盖房间混响尾巴）后才开麦。
   */
  const onTurnEnd = useCallback(() => {
    // 流式结束时缓冲通常还剩最后半句（模型最后一段常无句末标点），不 flush 就会漏念最后一句。
    voice.flush();
    if (!runningRef.current) {
      setState("idle");
      return;
    }
    void voice
      .waitUntilIdle()
      .then(() => new Promise((r) => setTimeout(r, ECHO_GUARD_MS)))
      .then(() => {
        // 等待期间用户可能已经关掉语音
        if (!runningRef.current) return;
        const stt = sttRef.current;
        if (!stt) {
          setState("idle");
          return;
        }
        setState("listening");
        return Promise.resolve(stt.start((t) => onFinalRef.current(t))).catch(() => {
          runningRef.current = false;
          setState("idle");
        });
      });
  }, [voice]);

  return { state, start, stop, onAssistantDelta, onTurnEnd };
}
