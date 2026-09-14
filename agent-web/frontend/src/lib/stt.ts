/**
 * 语音输入（STT）封装（add-voice-interaction）。
 *
 * 定义 {@link Stt} 接口，并提供一个基于 Vosk（浏览器离线 WASM）的实现：
 * - `vosk-browser` 作为 npm 依赖，由 Vite **按需动态 import**（代码分割，首次用语音才加载）
 * - 模型随 web 打包为 gzipped tar（默认 `/vosk-model/model.tar.gz`），离线自包含
 * - 麦克风授权失败时抛错，由上层降级到纯文本
 */

export interface Stt {
  /**
   * 开始监听。
   *
   * <p>improve-voice-accuracy T2.1：在 final 回调基础上增加可选的 partial 回调。
   * partial 由 Vosk `partialresult` 事件触发，每次识别引擎更新中间结果都会调用，
   * 频率高于 final（可能每几百毫秒一次），用于 UI 实时显示与（T4）渐进期稳定性判定。
   *
   * @param onFinal 一句 final 文本就绪时调用（用于提交）
   * @param onPartial （可选）每次 partial result 更新时调用（用于实时 UI）
   */
  start(
    onFinal: (text: string) => void,
    onPartial?: (text: string) => void,
  ): Promise<void>;
  /** 停止监听。 */
  stop(): void;
}

/** 默认模型地址：优先 VITE_VOSK_MODEL_URL，否则指向自托管 `/vosk-model/model.tar.gz`（vosk-browser 需要 gzipped tar）。 */
function defaultModelUrl(): string {
  const env = import.meta.env.VITE_VOSK_MODEL_URL as string | undefined;
  if (env) return env;
  const origin = typeof window !== "undefined" ? window.location.origin : "";
  return `${origin}/vosk-model/model.tar.gz`;
}

/**
 * 创建 Vosk 后端 STT。
 *
 * @param modelUrl Vosk 模型 gzipped tar 的 URL（默认自托管 `/vosk-model/model.tar.gz`）。
 * @returns 一个可 start/stop 的 {@link Stt} 实例。
 */
export async function createVoskStt(modelUrl: string = defaultModelUrl()): Promise<Stt> {
  if (!modelUrl) {
    throw new Error("未配置 Vosk 模型地址（请设置 VITE_VOSK_MODEL_URL）");
  }
  // 动态 import：Vite 代码分割 vosk-browser，首次用语音时才加载该 chunk
  const vosk: any = await import("vosk-browser");
  let model: any;
  try {
    model = await vosk.createModel(modelUrl);
  } catch {
    throw new Error("Vosk 模型加载失败（请检查 /vosk-model/model.tar.gz 是否可达）");
  }

  let recognizer: any | null = null;
  let onFinalRef: ((text: string) => void) | null = null;
  let onPartialRef: ((text: string) => void) | null = null;
  let stream: MediaStream | null = null;
  let ctx: AudioContext | null = null;
  let source: MediaStreamAudioSourceNode | null = null;
  let processor: ScriptProcessorNode | null = null;
  let muteGain: GainNode | null = null;

  return {
    async start(onFinal, onPartial) {
      onFinalRef = onFinal;
      onPartialRef = onPartial ?? null;
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error("当前浏览器不支持麦克风（getUserMedia 不可用）");
      }
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, channelCount: 1, sampleRate: 16000 },
      });
      // vosk-browser 的 KaldiRecognizer 需显式传 sampleRate，否则 wasm 把 undefined 转 float 失败
      recognizer = new model.KaldiRecognizer(16000);
      recognizer.on("result", (msg: any) => {
        const text = msg?.result?.text;
        if (text && onFinalRef) onFinalRef(text);
      });
      // improve-voice-accuracy T2.2：partial result 回调 onPartial
      recognizer.on("partialresult", (msg: any) => {
        const text = msg?.result?.partial;
        if (text && onPartialRef) onPartialRef(text);
      });
      ctx = new AudioContext();
      // 确保 AudioContext 处于 running（Chrome 在非用户手势时可能 suspended）
      try {
        await ctx.resume();
      } catch {
        /* resume 失败不阻断，继续 */
      }
      source = ctx.createMediaStreamSource(stream);
      processor = ctx.createScriptProcessor(4096, 1, 1);
      processor.onaudioprocess = (event) => {
        try {
          recognizer?.acceptWaveform(event.inputBuffer);
        } catch {
          /* 忽略单帧错误 */
        }
      };
      source.connect(processor);
      // 接到零增益输出：驱动 ScriptProcessor 被拉取（否则 onaudioprocess 可能不触发），且无回放
      muteGain = ctx.createGain();
      muteGain.gain.value = 0;
      processor.connect(muteGain);
      muteGain.connect(ctx.destination);
    },
    stop() {
      onFinalRef = null;
      onPartialRef = null;
      try {
        muteGain?.disconnect();
      } catch {
        /* ignore */
      }
      try {
        processor?.disconnect();
      } catch {
        /* ignore */
      }
      try {
        source?.disconnect();
      } catch {
        /* ignore */
      }
      try {
        recognizer?.remove?.();
      } catch {
        /* ignore */
      }
      try {
        stream?.getTracks().forEach((t) => t.stop());
      } catch {
        /* ignore */
      }
      try {
        ctx?.close();
      } catch {
        /* ignore */
      }
      recognizer = null;
    },
  };
}
