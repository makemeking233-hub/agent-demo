/**
 * 语音输入（STT）封装（add-voice-interaction）。
 *
 * 定义 {@link Stt} 接口，并提供一个基于 Vosk（浏览器离线 WASM）的实现：
 * - `vosk-browser` 与模型均已随 web 打包（自托管），离线自包含；也可用 env 覆盖
 * - 麦克风授权失败时抛错，由上层降级到纯文本
 */

export interface Stt {
  /** 开始监听；每次得到一句 final 文本调用 onFinal。 */
  start(onFinal: (text: string) => void): Promise<void>;
  /** 停止监听。 */
  stop(): void;
}

/** vosk-browser 运行时模块 URL：默认自托管 `/vosk-browser/vosk-browser.js`，可用 `VITE_VOSK_LIB_URL` 覆盖。 */
function voskLibUrl(): string {
  const env = import.meta.env.VITE_VOSK_LIB_URL as string | undefined;
  if (env) return env;
  const origin = typeof window !== "undefined" ? window.location.origin : "";
  return `${origin}/vosk-browser/vosk-browser.js`;
}

/** 默认模型地址：优先 VITE_VOSK_MODEL_URL，否则指向应用自托管的 /vosk-model/（随 web 打包）。 */
function defaultModelUrl(): string {
  const env = import.meta.env.VITE_VOSK_MODEL_URL as string | undefined;
  if (env) return env;
  const origin = typeof window !== "undefined" ? window.location.origin : "";
  return `${origin}/vosk-model/`;
}

/**
 * 创建 Vosk 后端 STT。
 *
 * @param modelUrl Vosk 中文模型目录 URL（含 conf/model.conf/am/… 等）。默认自托管 `/vosk-model/`。
 * @returns 一个可 start/stop 的 {@link Stt} 实例。
 */
export async function createVoskStt(modelUrl: string = defaultModelUrl()): Promise<Stt> {
  if (!modelUrl) {
    throw new Error("未配置 Vosk 模型地址（请设置 VITE_VOSK_MODEL_URL）");
  }
  let vosk: any;
  try {
    vosk = await import(/* @vite-ignore */ voskLibUrl());
  } catch (e) {
    throw new Error("vosk-browser 加载失败（请检查 /vosk-browser/vosk-browser.js 是否可达）");
  }
  let model: any;
  try {
    model = await vosk.Model.fromUri(modelUrl);
  } catch (e) {
    throw new Error("Vosk 模型加载失败（请检查 /vosk-model/ 是否可达）");
  }
  const recognizer = new vosk.Recognizer({ model, sampleRate: 16000 });

  let onFinalRef: ((text: string) => void) | null = null;
  let stream: MediaStream | null = null;
  let ctx: AudioContext | null = null;
  let source: MediaStreamAudioSourceNode | null = null;

  recognizer.on("result", (msg: any) => {
    const res = msg?.result;
    if (res?.final && onFinalRef) onFinalRef(res.text ?? "");
  });
  recognizer.on("error", (e: any) => console.error("[stt] vosk error", e));

  return {
    async start(onFinal) {
      onFinalRef = onFinal;
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error("当前浏览器不支持麦克风（getUserMedia 不可用）");
      }
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      ctx = new AudioContext();
      source = ctx.createMediaStreamSource(stream);
      // vosk-browser 通过 AudioWorklet 的 port 接收媒体流
      source.connect(recognizer.port);
    },
    stop() {
      onFinalRef = null;
      try {
        source?.disconnect();
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
    },
  };
}
