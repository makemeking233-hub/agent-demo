/**
 * 语音输入（STT）封装（add-voice-interaction）。
 *
 * 定义 {@link Stt} 接口，并提供一个基于 Vosk（浏览器离线 WASM）的实现：
 * - 运行时从 CDN/打包动态加载 `vosk-browser`（`@vite-ignore`，不阻塞构建）
 * - 模型地址默认指向应用自托管的 `/vosk-model/`（随 web 打包），可用 `VITE_VOSK_MODEL_URL` 覆盖
 * - 麦克风授权 / 模型加载失败时抛错，由上层降级到纯文本
 */

export interface Stt {
  /** 开始监听；每次得到一句 final 文本调用 onFinal。 */
  start(onFinal: (text: string) => void): Promise<void>;
  /** 停止监听。 */
  stop(): void;
}

/** vosk-browser 运行时模块 URL（CDN +esm）；不打包，运行时加载。 */
const VOSK_LIB_URL =
  (import.meta.env.VITE_VOSK_LIB_URL as string | undefined) ??
  "https://cdn.jsdelivr.net/npm/vosk-browser@0.0.8/+esm";

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
    vosk = await import(/* @vite-ignore */ VOSK_LIB_URL);
  } catch {
    throw new Error("vosk-browser 加载失败（需在运行时可达）");
  }
  const model = await vosk.Model.fromUri(modelUrl);
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
