import { useCallback, useRef, useState } from "react";
import type { VoiceReader } from "./voice";
import type { Stt } from "./stt";

export type VoiceState = "idle" | "loading" | "listening" | "sending";

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
      runningRef.current = false;
      sttRef.current?.stop();
      setState("sending");
      onSubmit(t);
    },
    [onSubmit, canSubmit],
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

  /** 本轮结束：若仍在自由语音循环中，重新监听。 */
  const onTurnEnd = useCallback(() => {
    if (runningRef.current) {
      setState("listening");
      const stt = sttRef.current;
      if (!stt) {
        setState("idle");
        return;
      }
      Promise.resolve(stt.start((t) => onFinalRef.current(t))).catch(() => {
        runningRef.current = false;
        setState("idle");
      });
    } else {
      setState("idle");
    }
  }, []);

  return { state, start, stop, onAssistantDelta, onTurnEnd };
}
