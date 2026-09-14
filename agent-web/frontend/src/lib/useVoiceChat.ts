import { useCallback, useRef, useState } from "react";
import type { VoiceReader } from "./voice";
import { looksLikeEcho } from "./voice";
import type { Stt } from "./stt";
import { contextCorrect, dedupeRepeats, filterShort } from "./voicePostProcess";

export type VoiceState = "idle" | "loading" | "listening" | "sending";

/**
 * Partial 稳定性判定（improve-voice-accuracy T4）：Vosk partial result 连续 3 次相同时
 * 提前触发 final 提交（不等 Vosk `result` 事件），缩短用户停顿→提交之间的延迟。
 * 2s 内未达稳定则提交当前最新 partial（兜底）。
 */
const PARTIAL_STABLE_COUNT = 3;
const PARTIAL_TIMEOUT_MS = 2000;

/**
 * 字符差异比例（improve-voice-accuracy T5.2）：位置对比 + 长度差的归一化结果。
 * 用于判断 LLM 纠错前后是否"改太多"——超过 50% 视为可疑，写 localStorage 供诊断。
 */
function computeCharDiffRatio(a: string, b: string): number {
  if (!a || !b) return 0;
  const longer = Math.max(a.length, b.length);
  if (longer === 0) return 0;
  let diff = 0;
  const minLen = Math.min(a.length, b.length);
  for (let i = 0; i < minLen; i++) {
    if (a[i] !== b[i]) diff++;
  }
  diff += Math.abs(a.length - b.length);
  return diff / longer;
}

/**
 * 朗读结束到重新开麦之间的静音余量（毫秒）。
 *
 * <p>覆盖扬声器混响尾巴与"onend 早于音频真正播完"的情况：TTS 的 onend 触发时房间里往往还有
 * 余音，立刻开麦会被识别到。improve-voice-accuracy T1.1：700ms 仍能听到扬声器尾巴，放宽到 1500ms
 * 覆盖笔记本自带麦+喇叭的物理混响。
 */
export const ECHO_GUARD_MS = 1500;

export interface UseVoiceOptions {
  /** 异步获取 STT 实例（Vosk 需先加载模型，故用工厂 + 缓存）。 */
  getStt: () => Promise<Stt>;
  voice: VoiceReader;
  /** 把一句语音文本作为用户消息提交。 */
  onSubmit: (text: string) => void;
  /** 当前是否可提交（如非 busy）。 */
  canSubmit: () => boolean;
  /** 会话 id（improve-voice-accuracy T5.1：用于 contextCorrect 后端缓存键） */
  sessionId?: string;
  /**
   * 助手最近 N 轮对话（improve-voice-accuracy T5.1：contextCorrect 给 LLM 用的上下文）。
   * 不传时退化为空数组（仅做 dedupe + filterShort，不调 LLM 纠错）。
   */
  recentTurns?: Array<{ role: "user" | "assistant"; content: string }>;
}

/**
 * 自由语音对话循环（add-voice-interaction）：监听 → final → 提交 → 朗读回复 → 再监听。
 * 与手动打字共存；stop() 停止监听与朗读。
 */
export function useVoiceChat({ getStt, voice, onSubmit, canSubmit, sessionId, recentTurns }: UseVoiceOptions) {
  const [state, setState] = useState<VoiceState>("idle");
  // improve-voice-accuracy T2.3：partial result 通过 React state 暴露给 UI（T6 partial UI 用）。
  // T4 会在 useVoiceChat 加 partial 稳定性判定；T2 阶段只暴露给消费者。
  const [lastPartial, setLastPartial] = useState<string>("");
  const sttRef = useRef<Stt | null>(null);
  const onFinalRef = useRef<(t: string) => void>(() => {});
  const onPartialRef = useRef<(t: string) => void>(() => {});
  const runningRef = useRef(false);
  // T4：partial 状态机——最近 N 个 partial 文本（滑动窗口）+ 兜底超时器 + 防重复提交标记
  const partialBufferRef = useRef<string[]>([]);
  const partialTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Vosk final 到达时若已被 partial 触发，丢弃（避免重复提交）
  const submittedRef = useRef(false);
  // T5.3：ASR 后处理开关（默认开）。Composer partial UI 在 T6 提供切换按钮。
  const [postProcessEnabled, setPostProcessEnabled] = useState<boolean>(true);

  /**
   * 把 final 文本提交（improve-voice-accuracy T4 + T5）：
   * 1) filterShort 过滤掉语气词 / <2 字
   * 2) dedupeRepeats 去连续重复模式
   * 3) 回声检查 + stop + setState + onSubmit
   * 4) 后台 fire-and-forget 调 LLM 纠错，仅用于诊断（T5.2 差异过大时记 localStorage）
   *
   * <p>设计取舍：contextCorrect 是 async（典型 300-800ms），如果阻塞提交会拖慢用户交互体验；
   * 改为 fire-and-forget：用户提交 deduped 原文，LLM 纠错用于诊断下次优化。
   * 大部分错误已被 dedupeRepeats 解决；少数需要语义纠错的场景（如"邪念 眼角舍小"→"现在我明白了"）
   * 走增量改进路线，不阻塞主流程。
   *
   * <p>拆出来是为了让 partial 触发的提交和 Vosk final 触发的提交**复用同一套提交逻辑**，
   * 又能在 Vosk final 路径上去重（partial 已提交的同一文本）。
   */
  const processFinalSubmission = useCallback(
    (text: string) => {
      const t0 = text.trim();
      if (!t0 || !canSubmit()) return;

      // T3.3：纯语气词或 <2 字直接丢弃（避免空触发）
      const passed = filterShort(t0);
      if (passed === null) return;
      // T3.1：去连续重复
      const deduped = dedupeRepeats(passed);

      // 回声防护（improve-voice-readout）：朗读期间收到的识别结果多半是助手自己的声音
      // （扬声器 → 麦克风）。麦克风本应在朗读期间关闭，这里是第二道保险，防止时序缝隙
      // 把助手的话当成用户输入提交、进而形成自我循环。
      if (voice.isSpeaking()) return;
      // 第三道保险：朗读刚结束时收到、且与我们刚说过的话高度重合的识别结果，同样按回声丢弃。
      // 时序防护挡不住"扬声器余音落进刚打开的麦克风"与"onend 早于音频播完"两种情况；
      // 语音识别对回声的转写往往是残缺错字的，靠字符重合率判定比整句比对可靠。
      const recent = voice.recentSpeech();
      if (recent && looksLikeEcho(deduped, recent)) return;
      // 提交后清掉 partial UI（improve-voice-accuracy T6：partial UI 在提交后立即清空）
      setLastPartial("");
      // 只暂停监听（本轮处理中不捕捉声音），但循环仍“武装”，每轮结束由 onTurnEnd 恢复监听
      sttRef.current?.stop();
      setState("sending");
      onSubmit(deduped);

      // T5.1 + T5.2：fire-and-forget 调 LLM 纠错，仅用于诊断
      if (postProcessEnabled && sessionId) {
        contextCorrect(deduped, sessionId, {
          enabled: true,
          recentTurns: recentTurns ?? [],
        })
          .then((corrected) => {
            if (computeCharDiffRatio(corrected, deduped) > 0.5) {
              try {
                const diag = {
                  ts: Date.now(),
                  raw: t0,
                  deduped,
                  corrected,
                  sessionId,
                };
                const key = "agent-demo:voice-correction-diag";
                const raw = window.localStorage.getItem(key) ?? "[]";
                const arr = JSON.parse(raw) as unknown[];
                arr.push(diag);
                const trimmed = arr.slice(-20);
                window.localStorage.setItem(key, JSON.stringify(trimmed));
              } catch {
                /* localStorage 不可用，忽略 */
              }
            }
          })
          .catch(() => {
            /* contextCorrect 内部已经降级处理；这里仅吞 Promise rejection */
          });
      }
    },
    [onSubmit, canSubmit, voice, postProcessEnabled, sessionId, recentTurns],
  );

  /** Vosk final 路径：若已被 partial 触发（submittedRef=true），丢弃，避免重复提交。 */
  const onFinal = useCallback(
    (text: string) => {
      if (submittedRef.current) {
        submittedRef.current = false;
        return;
      }
      processFinalSubmission(text);
    },
    [processFinalSubmission],
  );
  onFinalRef.current = onFinal;

  /**
   * 把 partial 提交为 final（improve-voice-accuracy T4）：
   * 清理缓冲 + 标记 submitted（让 Vosk final 来时去重）+ 走 processFinalSubmission。
   */
  const submitPartialAsFinal = useCallback(
    (text: string) => {
      if (partialTimerRef.current) {
        clearTimeout(partialTimerRef.current);
        partialTimerRef.current = null;
      }
      partialBufferRef.current = [];
      submittedRef.current = true;
      processFinalSubmission(text);
    },
    [processFinalSubmission],
  );

  // partial 回调（T4）：更新 state + 状态机 + 超时兜底
  const onPartial = useCallback((text: string) => {
    setLastPartial(text);
    // 已被触发过提交（partial 或 Vosk final），后续 partial 不再处理
    if (submittedRef.current) return;

    const buf = partialBufferRef.current;
    buf.push(text);
    if (buf.length > PARTIAL_STABLE_COUNT) buf.shift();

    // T4.1：buf 满且 3 个元素都等于最新 partial → 触发提交
    if (buf.length === PARTIAL_STABLE_COUNT && buf.every((t) => t === text)) {
      submitPartialAsFinal(text);
      return;
    }

    // T4.2：重置 2s 兜底超时——超时则提交当前最新 partial
    if (partialTimerRef.current) clearTimeout(partialTimerRef.current);
    partialTimerRef.current = setTimeout(() => {
      const latest = partialBufferRef.current[partialBufferRef.current.length - 1];
      if (latest) submitPartialAsFinal(latest);
    }, PARTIAL_TIMEOUT_MS);
  }, [submitPartialAsFinal]);
  onPartialRef.current = onPartial;

  /** 开始（或恢复）自由语音：懒加载 STT 并开始监听。 */
  const start = useCallback(async () => {
    runningRef.current = true;
    setState("loading");
    try {
      const stt = (sttRef.current ??= await getStt());
      setState("listening");
      await stt.start(
        (t) => onFinalRef.current(t),
        (p) => onPartialRef.current(p),
      );
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
    setLastPartial("");
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
        return Promise.resolve(
          stt.start(
            (t) => onFinalRef.current(t),
            (p) => onPartialRef.current(p),
          ),
        ).catch(() => {
          runningRef.current = false;
          setState("idle");
        });
      });
  }, [voice]);

  return {
    state,
    lastPartial,
    postProcessEnabled,
    setPostProcessEnabled,
    start,
    stop,
    onAssistantDelta,
    onTurnEnd,
  };
}
