import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useVoiceChat, ECHO_GUARD_MS } from "./useVoiceChat";
import type { Stt } from "./stt";
import type { VoiceReader } from "./voice";

function mockStt(
  startImpl?: (
    onFinal: (t: string) => void,
    onPartial?: (t: string) => void,
  ) => Promise<void> | void,
): Stt {
  // 默认实现：返回 Promise.resolve；测试可注入 startImpl 捕获回调。
  return {
    start: vi.fn(async (onFinal: (t: string) => void, onPartial?: (t: string) => void) => {
      if (startImpl) return startImpl(onFinal, onPartial);
      return Promise.resolve();
    }),
    stop: vi.fn(),
  };
}

function mockVoice(opts: { speaking?: boolean; recent?: string } = {}): VoiceReader {
  return {
    speak: vi.fn(),
    flush: vi.fn(),
    cancel: vi.fn(),
    muted: false as any,
    setMuted: vi.fn(),
    isSpeaking: vi.fn(() => opts.speaking ?? false),
    waitUntilIdle: vi.fn(async () => {}),
    recentSpeech: vi.fn(() => opts.recent ?? ""),
  };
}

describe("useVoiceChat", () => {
  it("start 后收到 final 即提交并停止监听", async () => {
    let cb: ((t: string) => void) | undefined;
    const stt = mockStt((onFinal, _onPartial) => {
      cb = onFinal;
    });
    const voice = mockVoice();
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );

    await act(async () => {
      await result.current.start();
    });
    expect(result.current.state).toBe("listening");

    act(() => cb!("你好"));
    expect(onSubmit).toHaveBeenCalledWith("你好");
    expect(stt.stop).toHaveBeenCalled();
    expect(result.current.state).toBe("sending");
  });

  it("空文本或不可提交时忽略", async () => {
    let cb: ((t: string) => void) | undefined;
    const stt = mockStt((onFinal, _onPartial) => {
      cb = onFinal;
    });
    const voice = mockVoice();
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => false }),
    );
    await act(async () => {
      await result.current.start();
    });
    act(() => cb!("   "));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("onAssistantDelta 朗读，onTurnEnd 重新监听", async () => {
    let cb: ((t: string) => void) | undefined;
    const stt = mockStt((onFinal, _onPartial) => {
      cb = onFinal;
    });
    const voice = mockVoice();
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });
    act(() => result.current.onAssistantDelta("好的"));
    expect(voice.speak).toHaveBeenCalledWith("好的");

    await act(async () => {
      result.current.onTurnEnd();
    });
    expect(result.current.state).toBe("listening");
  });

  it("提交后本轮结束仍恢复监听（自由语音循环不自行退出，仅手动 stop 退出）", async () => {
    let cb: ((t: string) => void) | undefined;
    const stt = mockStt((onFinal, _onPartial) => {
      cb = onFinal;
    });
    const voice = mockVoice();
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });

    // 收到一句 final 并提交
    act(() => cb!("你好"));
    expect(onSubmit).toHaveBeenCalledWith("你好");
    expect(result.current.state).toBe("sending");

    // 本轮结束 → 应恢复监听（循环继续），而非自动退出。
    // improve-voice-readout：现在要等朗读播完 + 静音余量后才开麦，故需越过这段时间。
    await act(async () => {
      result.current.onTurnEnd();
      await new Promise((r) => setTimeout(r, ECHO_GUARD_MS + 200));
    });
    expect(result.current.state).toBe("listening");

    // 只有手动 stop 才退出循环
    act(() => result.current.stop());
    expect(result.current.state).toBe("idle");
  });

  it("onTurnEnd 先 flush 朗读尾巴，并等播放结束才开麦", async () => {
    const stt = mockStt();
    const voice = mockVoice();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit: vi.fn(), canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });
    (stt.start as any).mockClear();

    act(() => result.current.onTurnEnd());
    // 等待期内不应开麦（否则 Vosk 会听到助手自己的声音）
    expect(voice.flush).toHaveBeenCalled();
    expect(voice.waitUntilIdle).toHaveBeenCalled();
    expect(stt.start).not.toHaveBeenCalled();

    await act(async () => {
      await new Promise((r) => setTimeout(r, ECHO_GUARD_MS + 200));
    });
    expect(stt.start).toHaveBeenCalledTimes(1);
  });

  it("朗读期间收到的识别结果被丢弃（回声防护第二道保险）", async () => {
    let cb: ((t: string) => void) | undefined;
    const stt = mockStt((onFinal, _onPartial) => {
      cb = onFinal;
    });
    const voice = mockVoice({ speaking: true });
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });

    act(() => cb!("这是助手自己说的话"));

    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("朗读刚结束时把助手自己的话听回来 → 丢弃（第三道保险）", async () => {
    // 真实样本：用户实测中 Vosk 把助手刚说的话残缺地转写出来
    let cb: ((t: string) => void) | undefined;
    const stt = mockStt((onFinal, _onPartial) => {
      cb = onFinal;
    });
    const voice = mockVoice({
      recent: "从「眼角含小」到「现在看再正常不过了」，你这语音输入终于是走对了。那接下来想干点啥？",
    });
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });

    act(() => cb!("再正常不过了那接下来想干"));

    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("用户真的说话时正常提交（不被回声过滤误杀）", async () => {
    let cb: ((t: string) => void) | undefined;
    const stt = mockStt((onFinal, _onPartial) => {
      cb = onFinal;
    });
    const voice = mockVoice({ recent: "好的，我这就去看一下那个文件。" });
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });

    act(() => cb!("帮我把那个日志文件删掉"));

    expect(onSubmit).toHaveBeenCalledWith("帮我把那个日志文件删掉");
  });

  it("stop 停止监听与朗读", async () => {
    const stt = mockStt();
    const voice = mockVoice();
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });
    act(() => result.current.stop());
    expect(stt.stop).toHaveBeenCalled();
    expect(voice.cancel).toHaveBeenCalled();
    expect(result.current.state).toBe("idle");
  });

  it("start 失败则回 idle 并抛错", async () => {
    const stt = mockStt();
    const voice = mockVoice();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => { throw new Error("no model"); }, voice, onSubmit: vi.fn(), canSubmit: () => true }),
    );
    await expect(async () => {
      await act(async () => {
        await result.current.start();
      });
    }).rejects.toThrow("no model");
    expect(result.current.state).toBe("idle");
  });

  // improve-voice-accuracy T2.3：partial 回调能把 Vosk 渐进文本暴露到 lastPartial state，
  // 给 T6 partial UI 用。验证：注入 partial 回调 → 触发 → lastPartial 更新。
  it("partial 回调暴露到 lastPartial state", async () => {
    let partialCb: ((t: string) => void) | undefined;
    const stt = mockStt((_onFinal, onPartial) => {
      partialCb = onPartial;
      return Promise.resolve();
    });
    const voice = mockVoice();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit: vi.fn(), canSubmit: () => true }),
    );

    expect(result.current.lastPartial).toBe("");

    await act(async () => {
      await result.current.start();
    });

    act(() => partialCb?.("邪念"));
    expect(result.current.lastPartial).toBe("邪念");

    act(() => partialCb?.("邪念 眼角"));
    expect(result.current.lastPartial).toBe("邪念 眼角");
  });

  // T2.3：提交 final 后 partial UI 应立即清空（避免与新对话残留）
  it("提交 final 后 lastPartial 立即清空", async () => {
    let cb: ((t: string) => void) | undefined;
    let partialCb: ((t: string) => void) | undefined;
    const stt = mockStt((onFinal, onPartial) => {
      cb = onFinal;
      partialCb = onPartial;
      return Promise.resolve();
    });
    const voice = mockVoice();
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });

    // 先有 partial，再触发 final
    act(() => partialCb?.("帮我看看"));
    expect(result.current.lastPartial).toBe("帮我看看");
    act(() => cb!("帮我看看日志文件"));
    expect(onSubmit).toHaveBeenCalledWith("帮我看看日志文件");
    expect(result.current.lastPartial).toBe("");
  });

  // improve-voice-accuracy T4.1：partial 连续 3 次相同 → 触发 final 提交
  it("partial buf 内 3 个元素都等于最新 → 触发 final 提交", async () => {
    let partialCb: ((t: string) => void) | undefined;
    const stt = mockStt((_onFinal, onPartial) => {
      partialCb = onPartial;
      return Promise.resolve();
    });
    const voice = mockVoice();
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });

    // 模拟 Vosk partial 渐进：3 次相同 = "凶手"
    act(() => partialCb?.("凶手"));
    act(() => partialCb?.("凶手"));
    act(() => partialCb?.("凶手")); // 第 3 次相同 → 触发
    expect(onSubmit).toHaveBeenCalledWith("凶手");
    expect(result.current.state).toBe("sending");
    expect(result.current.lastPartial).toBe("");
  });

  // T4：partial 触发 final 后，Vosk final 到达时不应重复提交
  it("partial 触发 final 后，Vosk final 到达不再重复 onSubmit", async () => {
    let cb: ((t: string) => void) | undefined;
    let partialCb: ((t: string) => void) | undefined;
    const stt = mockStt((onFinal, onPartial) => {
      cb = onFinal;
      partialCb = onPartial;
      return Promise.resolve();
    });
    const voice = mockVoice();
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });

    // partial 稳定触发
    act(() => partialCb?.("帮我看看"));
    act(() => partialCb?.("帮我看看"));
    act(() => partialCb?.("帮我看看"));
    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith("帮我看看");

    // Vosk final 到达（被 partial 触发后去重）
    act(() => cb?.("帮我看看日志文件"));
    // 验证 onSubmit 仍只调用 1 次（第二次被去重）
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });

  // improve-voice-accuracy T3.1+T5：filterShort 丢弃纯语气词 + dedupeRepeats 去重
  it("filterShort 丢弃纯语气词（如「嗯」），dedupeRepeats 去重复模式", async () => {
    let cb: ((t: string) => void) | undefined;
    const stt = mockStt((onFinal, _onPartial) => {
      cb = onFinal;
    });
    const voice = mockVoice();
    const onSubmit = vi.fn();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit, canSubmit: () => true }),
    );
    await act(async () => {
      await result.current.start();
    });

    // 纯语气词 → 不提交
    act(() => cb?.("嗯"));
    expect(onSubmit).not.toHaveBeenCalled();

    // 重复模式 → 去重后提交"凶手"
    act(() => cb?.("凶手凶手凶手"));
    expect(onSubmit).toHaveBeenCalledWith("凶手");
  });

  // T5.3：postProcessEnabled 暴露在 hook return 里 + setPostProcessEnabled 可切换
  it("暴露 postProcessEnabled 与 setPostProcessEnabled", async () => {
    const stt = mockStt();
    const voice = mockVoice();
    const { result } = renderHook(() =>
      useVoiceChat({ getStt: async () => stt, voice, onSubmit: vi.fn(), canSubmit: () => true }),
    );

    // 默认 true
    expect(result.current.postProcessEnabled).toBe(true);
    // 切换到 false
    act(() => result.current.setPostProcessEnabled(false));
    expect(result.current.postProcessEnabled).toBe(false);
  });
});
