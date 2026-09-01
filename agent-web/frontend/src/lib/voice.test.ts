import { beforeEach, describe, expect, it, vi } from "vitest";
import { createVoice } from "./voice";

let synth: { speak: ReturnType<typeof vi.fn>; cancel: ReturnType<typeof vi.fn>; utterances: any[] };

beforeEach(() => {
  const utterances: any[] = [];
  synth = {
    speak: vi.fn((u: any) => utterances.push(u)),
    cancel: vi.fn(),
    utterances,
  };
  (globalThis as any).speechSynthesis = synth;
  (globalThis as any).SpeechSynthesisUtterance = vi.fn(function (this: any, text: string) {
    this.text = text;
    this.lang = "";
  });
});

describe("voice", () => {
  it("speak 用 zh-CN 朗读并调用 browser speechSynthesis", () => {
    const v = createVoice();
    v.speak("你好");
    expect(synth.speak).toHaveBeenCalledTimes(1);
    expect(synth.utterances[0].lang).toBe("zh-CN");
    expect(synth.utterances[0].text).toBe("你好");
  });

  it("静音时不朗读", () => {
    const v = createVoice();
    v.setMuted(true);
    v.speak("你好");
    expect(synth.speak).not.toHaveBeenCalled();
  });

  it("cancel 调用 browser cancel", () => {
    const v = createVoice();
    v.cancel();
    expect(synth.cancel).toHaveBeenCalled();
  });

  it("setMuted(true) 立即打断", () => {
    const v = createVoice();
    v.setMuted(true);
    expect(synth.cancel).toHaveBeenCalled();
  });

  it("无 speechSynthesis 时不抛错", () => {
    (globalThis as any).speechSynthesis = undefined;
    const v = createVoice();
    expect(() => v.speak("x")).not.toThrow();
    expect(() => v.cancel()).not.toThrow();
  });
});
