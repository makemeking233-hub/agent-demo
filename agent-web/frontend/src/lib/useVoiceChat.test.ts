import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useVoiceChat } from "./useVoiceChat";
import type { Stt } from "./stt";
import type { VoiceReader } from "./voice";

function mockStt(startImpl?: (cb: (t: string) => void) => void): Stt {
  return { start: vi.fn(startImpl), stop: vi.fn() };
}

function mockVoice(): VoiceReader {
  return { speak: vi.fn(), cancel: vi.fn(), muted: false as any, setMuted: vi.fn() };
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

    // 本轮结束 → 应恢复监听（循环继续），而非自动退出
    await act(async () => {
      result.current.onTurnEnd();
    });
    expect(result.current.state).toBe("listening");

    // 只有手动 stop 才退出循环
    act(() => result.current.stop());
    expect(result.current.state).toBe("idle");
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
