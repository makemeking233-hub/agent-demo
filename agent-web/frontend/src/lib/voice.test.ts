import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  DEFAULT_ECHO_WINDOW_MS,
  ECHO_OVERLAP_THRESHOLD,
  MAX_CHUNK_CHARS,
  MIN_CHUNK_CHARS,
  createVoice,
  looksLikeEcho,
  sanitizeForSpeech,
} from "./voice";
import { ECHO_GUARD_MS } from "./useVoiceChat";

let synth: {
  speak: ReturnType<typeof vi.fn>;
  cancel: ReturnType<typeof vi.fn>;
  getVoices: ReturnType<typeof vi.fn>;
  utterances: any[];
};

function makeSynth(voices: any[] = []) {
  const utterances: any[] = [];
  return {
    speak: vi.fn((u: any) => utterances.push(u)),
    cancel: vi.fn(),
    getVoices: vi.fn(() => voices),
    utterances,
  };
}

beforeEach(() => {
  synth = makeSynth();
  (globalThis as any).speechSynthesis = synth;
  (globalThis as any).SpeechSynthesisUtterance = vi.fn(function (this: any, text: string) {
    this.text = text;
    this.lang = "";
    this.rate = 1;
    this.onend = null;
    this.onerror = null;
  });
});

/** 取所有已交给语音合成的文本。 */
function spoken(): string[] {
  return synth.utterances.map((u) => u.text);
}

describe("sanitizeForSpeech（improve-voice-readout）", () => {
  it("剥离 emoji，不把表情念出来", () => {
    expect(sanitizeForSpeech("好的 😄 这就去做 ✅")).toBe("好的 这就去做");
  });

  it("剥离 markdown 标记，保留自然语言", () => {
    expect(sanitizeForSpeech("## 标题\n**加粗**与*斜体*，还有 `code`")).toBe(
      "标题 加粗与斜体，还有 code",
    );
  });

  it("围栏代码块不逐字念，用提示替代", () => {
    const out = sanitizeForSpeech("看这段：\n```java\nint x = 1;\n```\n就是这样");
    expect(out).not.toContain("int x = 1");
    expect(out).toContain("（代码）");
  });

  it("链接只念文字，不念地址", () => {
    expect(sanitizeForSpeech("见 [文档](https://example.com/a/b)")).toBe("见 文档");
    expect(sanitizeForSpeech("见 https://example.com/a/b")).toBe("见 链接");
  });

  it("表格竖线与分隔行不念", () => {
    const out = sanitizeForSpeech("| a | b |\n|---|---|\n| 1 | 2 |");
    expect(out).not.toContain("|");
    expect(out).not.toContain("---");
  });
});

describe("voice 按句聚合（improve-voice-readout）", () => {
  it("短增量不朗读，攒成一句才朗读一次", () => {
    const v = createVoice();
    v.speak("你好");
    v.speak("，我是");
    expect(synth.speak).not.toHaveBeenCalled();

    // 攒够长度（≥MIN_CHUNK_CHARS=24）且以句末标点收尾 → 一次合成，减少合成间停顿
    v.speak("助手，很高兴见到你，我们现在就开始正式对话吧。");
    expect(synth.speak).toHaveBeenCalledTimes(1);
    expect(spoken()[0]).toBe("你好，我是助手，很高兴见到你，我们现在就开始正式对话吧。");
  });

  it("flush 把不足一句的尾巴读掉", () => {
    const v = createVoice();
    v.speak("这句话没有句号");
    expect(synth.speak).not.toHaveBeenCalled();
    v.flush();
    expect(spoken()).toEqual(["这句话没有句号"]);
  });

  it("超长无标点也按上限切分，不会一直不开口", () => {
    const v = createVoice();
    v.speak("啊".repeat(MAX_CHUNK_CHARS + 10));
    expect(synth.speak).toHaveBeenCalled();
    expect(spoken()[0].length).toBe(MAX_CHUNK_CHARS);
  });

  it("设置语速与 zh-CN", () => {
    const v = createVoice();
    v.speak("。".repeat(MIN_CHUNK_CHARS));
    expect(synth.utterances[0].lang).toBe("zh-CN");
    // 用户反馈默认与 1.1 都偏慢 → 默认 1.5
    expect(synth.utterances[0].rate).toBeGreaterThanOrEqual(1.5);
  });

  it("recentSpeech 返回刚朗读过的文本，供回声判定", () => {
    const v = createVoice();
    v.speak("这是一句会被朗读的话。");
    v.flush();
    expect(v.recentSpeech()).toContain("这是一句会被朗读的话");
    // 窗口收缩到负值（即"未来"）则不再返回任何内容
    expect(v.recentSpeech(-1)).toBe("");
  });

  it("cancel 清空最近朗读记录", () => {
    const v = createVoice();
    v.speak("一句会被朗读的话。");
    v.flush();
    v.cancel();
    expect(v.recentSpeech()).toBe("");
  });

  it("优先选用中文音色", () => {
    const zh = { lang: "zh-CN", localService: true, name: "zh" };
    (globalThis as any).speechSynthesis = synth = makeSynth([{ lang: "en-US" }, zh]);
    const v = createVoice();
    v.speak("。".repeat(MIN_CHUNK_CHARS));
    expect(synth.utterances[0].voice).toBe(zh);
  });
});

describe("looksLikeEcho（第三道回声保险）", () => {
  // 真实样本：用户实测中 Vosk 把助手刚朗读的内容残缺转写出来
  const SPOKEN =
    "哈哈，好嘞，小白 那就正常聊起来了。从「眼角含小」到「现在看再正常不过了」，你这语音输入终于是走对了。那接下来想干点啥？随便聊也行，要我干活也行，你说";

  it("识别出助手刚说过的话 → 判为回声", () => {
    expect(looksLikeEcho("再正常不过了那接下来想干", SPOKEN)).toBe(true);
  });

  it("整句被听回来 → 判为回声", () => {
    expect(looksLikeEcho("那接下来想干点啥", SPOKEN)).toBe(true);
  });

  it("用户独立说的话 → 不是回声", () => {
    expect(looksLikeEcho("帮我把昨天那个日志文件清一下", SPOKEN)).toBe(false);
  });

  it("过短的结果不作为判据（避免误杀「好的」「嗯」）", () => {
    expect(looksLikeEcho("好的", SPOKEN)).toBe(false);
  });

  it("没有任何朗读记录时不误判", () => {
    expect(looksLikeEcho("随便说点什么吧", "")).toBe(false);
  });

  it("标点与空白不影响判定", () => {
    expect(looksLikeEcho("再正常不过了，那接下来想干点啥？", SPOKEN)).toBe(true);
  });

  // improve-voice-accuracy T1.4：阈值从 0.6 提到 0.75（更严，避免近音词跌破阈值被漏判）
  it("回声防护常量符合改善版（T1.1-1.3）", () => {
    expect(ECHO_GUARD_MS).toBe(1500); // 700 → 1500，覆盖扬声器混响尾巴
    expect(ECHO_OVERLAP_THRESHOLD).toBe(0.75); // 0.6 → 0.75，更严的判定
    expect(DEFAULT_ECHO_WINDOW_MS).toBe(6000); // 10000 → 6000，6 秒足够覆盖真实回声窗口
  });

  // 边界值 0.74 vs 0.75：构造 heard 让命中字符比例 ≥ 0.75（验证阈值提到 0.75 后仍判为回声）
  // 注：heard 必须 ≥ ECHO_MIN_CHARS (=6) 才会走重合率判定，否则直接 false
  it("高重合（≥ 0.75）→ 判为回声", () => {
    // "哈哈好嘞小白" 8 字符，全部命中 SPOKEN 里的字符 → 8/8 = 1.0
    expect(looksLikeEcho("哈哈好嘞小白", SPOKEN)).toBe(true);
  });
});

describe("voice 播放状态（回声防护）", () => {
  it("提交后 isSpeaking 为真，onend 后转假", async () => {
    const v = createVoice();
    v.speak("。".repeat(MIN_CHUNK_CHARS));
    expect(v.isSpeaking()).toBe(true);

    synth.utterances[0].onend?.();
    expect(v.isSpeaking()).toBe(false);
    await expect(v.waitUntilIdle(1000)).resolves.toBeUndefined();
  });

  it("waitUntilIdle 在结算后立刻返回", async () => {
    const v = createVoice();
    await expect(v.waitUntilIdle(1000)).resolves.toBeUndefined();
  });

  it("cancel 清空播放状态", () => {
    const v = createVoice();
    v.speak("。".repeat(MIN_CHUNK_CHARS));
    v.cancel();
    expect(v.isSpeaking()).toBe(false);
  });

  it("静音时不朗读也不计入播放状态", () => {
    const v = createVoice();
    v.setMuted(true);
    v.speak("。".repeat(MIN_CHUNK_CHARS));
    expect(synth.speak).not.toHaveBeenCalled();
    expect(v.isSpeaking()).toBe(false);
  });

  it("cancel 调用 browser cancel", () => {
    const v = createVoice();
    v.cancel();
    expect(synth.cancel).toHaveBeenCalled();
  });

  it("无 speechSynthesis 时不抛错", () => {
    (globalThis as any).speechSynthesis = undefined;
    const v = createVoice();
    expect(() => v.speak("。".repeat(MIN_CHUNK_CHARS))).not.toThrow();
    expect(() => v.cancel()).not.toThrow();
    expect(v.isSpeaking()).toBe(false);
  });
});
