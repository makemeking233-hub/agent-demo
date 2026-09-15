/**
 * useSettingsStore 测试 (add-settings-foundation M1).
 */

import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { __resetSettingsStoreForTest, useSettingsStore } from "./useSettingsStore";

const MOCK_VIEW = {
  version: 1,
  general: { appearance: { preference: "system" } },
  revision: 0,
};

describe("useSettingsStore", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => MOCK_VIEW,
    });
    vi.stubGlobal("fetch", fetchMock);
    const fakeEs = {
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      close: vi.fn(),
      onerror: null as ((ev: Event) => void) | null,
      onopen: null as ((ev: Event) => void) | null,
    };
    vi.stubGlobal("EventSource", vi.fn().mockImplementation(() => fakeEs));
    __resetSettingsStoreForTest();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    __resetSettingsStoreForTest();
  });

  it("returns initial loading status then ready", async () => {
    const { result } = renderHook(() => useSettingsStore((s) => s));
    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(result.current.snapshot).toEqual(MOCK_VIEW);
  });

  it("patch updates local snapshot", async () => {
    fetchMock
      .mockResolvedValueOnce({ ok: true, json: async () => MOCK_VIEW })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ ...MOCK_VIEW, revision: 1 }),
      });
    const { result } = renderHook(() => useSettingsStore((s) => s));
    await waitFor(() => expect(result.current.status).toBe("ready"));
    await act(async () => {
      await result.current.patch("general.appearance.preference", "dark");
    });
    expect(result.current.snapshot?.revision).toBe(1);
  });

  it("patch on error keeps old snapshot and sets error", async () => {
    fetchMock
      .mockResolvedValueOnce({ ok: true, json: async () => MOCK_VIEW })
      .mockResolvedValueOnce({
        ok: false,
        status: 400,
        json: async () => ({ error: "bad_value" }),
      });
    const { result } = renderHook(() => useSettingsStore((s) => s));
    await waitFor(() => expect(result.current.status).toBe("ready"));
    await act(async () => {
      try {
        await result.current.patch("general.appearance.preference", "bogus");
      } catch {
        /* expected */
      }
    });
    expect(result.current.snapshot?.revision).toBe(0);
    expect(result.current.error?.status).toBe(400);
  });

  it("refresh forces re-fetch", async () => {
    fetchMock.mockResolvedValue({ ok: true, json: async () => MOCK_VIEW });
    const { result } = renderHook(() => useSettingsStore((s) => s));
    await waitFor(() => expect(result.current.status).toBe("ready"));
    await act(async () => {
      await result.current.refresh();
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("selector returns only the chosen slice", async () => {
    const { result } = renderHook(() => useSettingsStore((s) => s.status));
    await waitFor(() => expect(result.current).toBe("ready"));
  });
});
