/**
 * 语音播放封装：浏览器 speechSynthesis 朗读助手文本，支持静音/打断。
 *
 * <p>improve-voice-readout 两个关键点：
 *
 * <ul>
 *   <li><b>按句聚合</b>：此前把 SSE 的每个流式增量（常只有 1-3 字）单独 speak 一次，TTS 对每段
 *       都要重新起停，韵律碎裂、听感很慢。现在增量先进缓冲，攒到"像一句话"再朗读。
 *   <li><b>清洗</b>：原始模型输出含 emoji 与 markdown，直接进 TTS 会被逐字念出来（emoji 被念成
 *       名称、反引号与竖线也照念）。朗读前统一剥离。
 * </ul>
 */

/** 朗读器接口（便于测试注入 mock）。 */
export interface VoiceReader {
  /** 朗读一段文本（内部按句聚合后交给语音合成）。 */
  speak(text: string): void;
  /** 把缓冲里不足一句的残余立刻读掉（一轮结束时调用，否则最后半句永远不念）。 */
  flush(): void;
  /** 打断当前朗读（静音/停止时调用）。 */
  cancel(): void;
  /** 是否静音。 */
  readonly muted: boolean;
  /** 设置静音；设为 true 时立即打断。 */
  setMuted(muted: boolean): void;
  /**
   * 是否仍有语音在排队或播放中。
   *
   * <p>用于回声防护：朗读期间麦克风若打开，STT 会把助手自己的声音识别成用户输入，
   * 提交后变成新一轮对话 —— 形成自我循环。
   */
  isSpeaking(): boolean;
  /**
   * 等到语音彻底播完（含超时兜底），供恢复监听前调用。
   *
   * @param timeoutMs 最长等待（默认 {@link DEFAULT_IDLE_TIMEOUT_MS}），避免浏览器不触发 onend 时卡死
   * @returns 播放结束后 resolve
   */
  waitUntilIdle(timeoutMs?: number): Promise<void>;
}

/** 攒够这么多字符才考虑朗读——把大量小增量合并成一次合成，避免逐字起停。 */
export const MIN_CHUNK_CHARS = 12;

/** 单次合成的最长字符数：长文没有句末标点时也要及时开口。 */
export const MAX_CHUNK_CHARS = 80;

/** 默认语速（略快于浏览器默认，缓解听感拖沓）。 */
export const DEFAULT_RATE = 1.1;

/** 等待播放结束时的轮询间隔（毫秒）。 */
const IDLE_POLL_MS = 100;

/** 等待播放结束的最长时限：浏览器不触发 onend 时不能把麦克风永久锁死。 */
export const DEFAULT_IDLE_TIMEOUT_MS = 15000;

/** 句末标点（含中文全角与省略号、换行）。 */
const SENTENCE_END = /[。！？!?；;…\n]/;

/**
 * 清洗文本，只保留适合朗读的部分。
 *
 * <p>顺序有讲究：先去掉围栏代码块与行内代码，再处理链接，最后剥 markdown 标记——否则标记会破坏
 * 前面的结构匹配。清洗失败的代价只是"少念/normal念"，所以规则宁可激进。
 *
 * @param raw 原始文本
 * @return 适合朗读的文本（可能为空串）
 */
export function sanitizeForSpeech(raw: string): string {
  let t = raw ?? "";
  // 围栏代码块：不逐字念代码，用一句提示替代
  t = t.replace(/```[\s\S]*?```/g, "（代码）");
  // 行内代码保留内容、去掉反引号
  t = t.replace(/`([^`]*)`/g, "$1");
  t = t.replace(/`/g, "");
  // 图片/链接：保留可读文字，丢掉地址
  t = t.replace(/!\[([^\]]*)\]\([^)]*\)/g, "$1");
  t = t.replace(/\[([^\]]*)\]\([^)]*\)/g, "$1");
  // 裸 URL
  t = t.replace(/https?:\/\/\S+/g, "链接");
  // emoji / 符号 / 变体选择符 / 零宽连接符 / 区域指示符 / 键帽
  t = t.replace(
    /[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{FE0F}\u{200D}\u{20E3}\u{1F1E6}-\u{1F1FF}]/gu,
    "",
  );
  // 表格：分隔行整行丢弃，单元格竖线变空格
  t = t.replace(/^\s*\|?[\s:|-]+\|[\s:|-]*$/gm, "");
  t = t.replace(/\|/g, " ");
  // 标题 / 引用 / 列表符号
  t = t.replace(/^\s{0,3}#{1,6}\s*/gm, "");
  t = t.replace(/^\s{0,3}>\s?/gm, "");
  t = t.replace(/^\s{0,3}[-*+]\s+/gm, "");
  t = t.replace(/^\s{0,3}\d+\.\s+/gm, "");
  // 水平线
  t = t.replace(/^\s*([-*_]\s*){3,}$/gm, "");
  // 强调 / 删除线标记
  t = t.replace(/(\*\*|__|~~|\*|_)/g, "");
  // 空白归一：换行也折叠成空格，避免标题/列表项被念成断裂的短句
  t = t.replace(/\s+/g, " ");
  return t.trim();
}

/** 找出「最后一个句末标点之后」的位置下标（用于在窗口内切分）；无则返回 -1。 */
function lastSentenceCut(s: string): number {
  for (let i = s.length - 1; i >= 0; i--) {
    if (SENTENCE_END.test(s[i])) return i + 1;
  }
  return -1;
}

/** 是否以句末标点（允许尾随空白）结束。 */
function endsWithSentenceEnd(s: string): boolean {
  const trimmed = s.replace(/\s+$/, "");
  return trimmed.length > 0 && SENTENCE_END.test(trimmed[trimmed.length - 1]);
}

/**
 * 创建基于浏览器 speechSynthesis 的朗读器。
 *
 * @param defaultMuted 初始是否静音
 * @param rateOverride 语速覆盖（默认 {@link DEFAULT_RATE}）
 */
export function createVoice(defaultMuted = false, rateOverride?: number): VoiceReader {
  let muted = defaultMuted;
  let buffer = "";
  let pending = 0;
  const rate = rateOverride ?? DEFAULT_RATE;
  const synth: SpeechSynthesis | undefined =
    typeof window !== "undefined" ? window.speechSynthesis : undefined;

  /** 优先中文音色；选不到就用默认（部分浏览器首次 getVoices() 为空，不阻塞朗读）。 */
  function pickVoice(): SpeechSynthesisVoice | undefined {
    try {
      const voices = synth?.getVoices?.() ?? [];
      return (
        voices.find((v) => v.lang === "zh-CN" && v.localService) ??
        voices.find((v) => v.lang === "zh-CN") ??
        voices.find((v) => v.lang?.toLowerCase().startsWith("zh"))
      );
    } catch {
      return undefined;
    }
  }

  /** 真正交给语音合成（清洗后为空则跳过）。 */
  function utter(text: string): void {
    if (muted || !synth) return;
    const clean = sanitizeForSpeech(text);
    if (!clean) return;
    try {
      const u = new SpeechSynthesisUtterance(clean);
      u.lang = "zh-CN";
      u.rate = rate;
      const v = pickVoice();
      if (v) u.voice = v;
      // 计数在"提交"时就加、在 onend/onerror/看门狗里减：Chrome 偶发不触发 onend，
      // 因此必须有基于文本长度的兜底，否则 isSpeaking() 会永远为真、麦克风再也开不了。
      pending++;
      let settled = false;
      const settle = () => {
        if (settled) return;
        settled = true;
        pending = Math.max(0, pending - 1);
        if (watchdog) clearTimeout(watchdog);
      };
      const watchdog = setTimeout(settle, estimateSpeakMs(clean, rate));
      u.onend = settle;
      u.onerror = settle;
      synth.speak(u);
    } catch {
      /* 朗读失败不阻断 */
    }
  }

  /** 从缓冲里取出可朗读的片段：成句或达到长度上限；force 时把尾巴也读出。 */
  function drain(force: boolean): void {
    for (;;) {
      if (buffer.length >= MIN_CHUNK_CHARS && endsWithSentenceEnd(buffer)) {
        const chunk = buffer;
        buffer = "";
        utter(chunk);
        continue;
      }
      if (buffer.length >= MAX_CHUNK_CHARS) {
        const cut = lastSentenceCut(buffer);
        const at = cut > 0 ? cut : MAX_CHUNK_CHARS;
        const chunk = buffer.slice(0, at);
        buffer = buffer.slice(at);
        utter(chunk);
        continue;
      }
      break;
    }
    if (force && buffer.trim()) {
      const chunk = buffer;
      buffer = "";
      utter(chunk);
    }
  }

  return {
    speak(text) {
      if (muted || !text) return;
      buffer += text;
      drain(false);
    },
    flush() {
      drain(true);
    },
    cancel() {
      buffer = "";
      pending = 0;
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
      if (m) {
        buffer = "";
        pending = 0;
        synth?.cancel();
      }
    },
    isSpeaking() {
      return pending > 0;
    },
    waitUntilIdle(timeoutMs = DEFAULT_IDLE_TIMEOUT_MS) {
      if (pending === 0) return Promise.resolve();
      return new Promise<void>((resolve) => {
        const deadline = Date.now() + timeoutMs;
        const tick = () => {
          if (pending === 0 || Date.now() >= deadline) {
            resolve();
            return;
          }
          setTimeout(tick, IDLE_POLL_MS);
        };
        setTimeout(tick, IDLE_POLL_MS);
      });
    },
  };
}

/**
 * 估算一段文本的朗读时长（毫秒），用作 onend 不触发时的兜底。
 *
 * <p>中文按约 4.5 字/秒估，除以语速；最少 2 秒，避免极短句被误判为已结束。
 *
 * @param text 已清洗文本
 * @param rate 语速
 * @returns 估算毫秒数
 */
function estimateSpeakMs(text: string, rate: number): number {
  const base = (text.length / 4.5) * 1000;
  return Math.max(2000, Math.round(base / Math.max(0.5, rate)) + 1500);
}
