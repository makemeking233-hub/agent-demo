import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useVoiceChat, ECHO_GUARD_MS } from "./useVoiceChat";
import type { Stt } from "./stt";
import type { VoiceReader } from "./voice";

function mockStt(startImpl?: (cb: (t: string) => void) => void): Stt {
  return { start: vi.fn(startImpl), stop: vi.fn() };
}

function mockVoice(opts: { speaking?: boolean } = {}): VoiceReader {
  return {
    speak: vi.fn(),
    flush: vi.fn(),
    cancel: vi.fn(),
    muted: false as any,
    setMuted: vi.fn(),
    isSpeaking: vi.fn(() => opts.speaking ?? false),
    waitUntilIdle: vi.fn(async () => {}),
  };
}

describe("useVoiceChat", () => {
  it("start 后收到 final 即提交并停止监听", async () => {
    let cb: ((t: string) => void) | undefined;
    const stt = mockStt((c) => (cb = c));
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
    const stt = mockStt((c) => (cb = c));
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
    const stt = mockStt((c) => (cb = c));
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
    const stt = mockStt((c) => (cb = c));
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
    const stt = mockStt((c) => (cb = c));
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
});
