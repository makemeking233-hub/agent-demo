/**
 * useThemeApplication 测试 (shadcn-components-p1).
 *
 * 验证 preference → <html data-theme> 映射：
 * light / dark / system（跟随 prefers-color-scheme）/ hc（高对比度）。
 */

import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mockStore: { snapshot: { version: number; general: { appearance: { preference: string } }; revision: number } } = {
  snapshot: {
    version: 1,
    general: { appearance: { preference: "system" } },
    revision: 0,
  },
};

vi.mock("./useSettingsStore", () => ({
  useSettingsStore: (selector: (s: typeof mockStore) => unknown) => selector(mockStore),
}));

import { useThemeApplication } from "./useThemeApplication";

/** 造一个可切换的 matchMedia stub。 */
function stubMatchMedia(dark: boolean) {
  const listeners: (() => void)[] = [];
  const mql = {
    matches: dark,
    media: "(prefers-color-scheme: dark)",
    addEventListener: (_: string, cb: () => void) => listeners.push(cb),
    removeEventListener: () => undefined,
    dispatch: () => listeners.forEach((cb) => cb()),
  };
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockReturnValue(mql),
  });
  return mql;
}

describe("useThemeApplication", () => {
  beforeEach(() => {
    document.documentElement.removeAttribute("data-theme");
    mockStore.snapshot = { ...mockStore.snapshot, general: { appearance: { preference: "light" } } };
  });

  afterEach(() => {
    document.documentElement.removeAttribute("data-theme");
  });

  it("preference=light 写 data-theme=light", () => {
    stubMatchMedia(false);
    mockStore.snapshot = { ...mockStore.snapshot, general: { appearance: { preference: "light" } } };
    renderHook(() => useThemeApplication());
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
  });

  it("preference=dark 写 data-theme=dark", () => {
    stubMatchMedia(false);
    mockStore.snapshot = { ...mockStore.snapshot, general: { appearance: { preference: "dark" } } };
    renderHook(() => useThemeApplication());
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
  });

  it("preference=hc 写 data-theme=hc（高对比度）", () => {
    stubMatchMedia(false);
    mockStore.snapshot = { ...mockStore.snapshot, general: { appearance: { preference: "hc" } } };
    renderHook(() => useThemeApplication());
    expect(document.documentElement.getAttribute("data-theme")).toBe("hc");
  });

  it("preference=system 跟随 matchMedia（false → light）", () => {
    stubMatchMedia(false);
    mockStore.snapshot = { ...mockStore.snapshot, general: { appearance: { preference: "system" } } };
    renderHook(() => useThemeApplication());
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
  });

  it("preference=system 跟随 matchMedia（true → dark）", () => {
    stubMatchMedia(true);
    mockStore.snapshot = { ...mockStore.snapshot, general: { appearance: { preference: "system" } } };
    renderHook(() => useThemeApplication());
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
  });
});