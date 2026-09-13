import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  MAX_CHUNK_CHARS,
  MIN_CHUNK_CHARS,
  createVoice,
  sanitizeForSpeech,
} from "./voice";

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

    // 攒够长度且以句末标点收尾 → 一次合成
    v.speak("助手，很高兴见到你。");
    expect(synth.speak).toHaveBeenCalledTimes(1);
    expect(spoken()[0]).toBe("你好，我是助手，很高兴见到你。");
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
    expect(synth.utterances[0].rate).toBeGreaterThan(1);
  });

  it("优先选用中文音色", () => {
    const zh = { lang: "zh-CN", localService: true, name: "zh" };
    (globalThis as any).speechSynthesis = synth = makeSynth([{ lang: "en-US" }, zh]);
    const v = createVoice();
    v.speak("。".repeat(MIN_CHUNK_CHARS));
    expect(synth.utterances[0].voice).toBe(zh);
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
