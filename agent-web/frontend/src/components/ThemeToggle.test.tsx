/**
 * ThemeToggle 测试 (polish-theme-toggle).
 */

import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeToggle } from "./ThemeToggle";

const noopPatch = vi.fn().mockResolvedValue(undefined);

interface MockSnapshot {
  version: number;
  general: { appearance: { preference: "light" | "dark" | "system" } };
  revision: number;
}

const mockStore: {
  snapshot: MockSnapshot;
  status: "ready";
  error: null;
  patch: typeof noopPatch;
  refresh: () => Promise<void>;
} = {
  snapshot: {
    version: 1,
    general: { appearance: { preference: "system" } },
    revision: 0,
  },
  status: "ready",
  error: null,
  patch: noopPatch,
  refresh: vi.fn(),
};

vi.mock("../hooks/useSettingsStore", () => ({
  useSettingsStore: (selector: (s: typeof mockStore) => unknown) => selector(mockStore),
}));

describe("ThemeToggle", () => {
  beforeEach(() => {
    noopPatch.mockClear();
    mockStore.snapshot = {
      version: 1,
      general: { appearance: { preference: "system" } },
      revision: 0,
    };
  });

  it("renders a single trigger button", () => {
    render(<ThemeToggle />);
    expect(screen.getByTestId("theme-toggle-trigger")).toBeInTheDocument();
    expect(screen.getByLabelText("切换主题")).toBeInTheDocument();
  });

  it("does not render popover by default", () => {
    render(<ThemeToggle />);
    expect(screen.queryByTestId("theme-popover")).toBeNull();
  });

  it("aria-expanded is false initially", () => {
    render(<ThemeToggle />);
    expect(screen.getByTestId("theme-toggle-trigger").getAttribute("aria-expanded")).toBe("false");
  });

  it("clicking trigger toggles popover open", () => {
    render(<ThemeToggle />);
    fireEvent.click(screen.getByTestId("theme-toggle-trigger"));
    expect(screen.getByTestId("theme-popover")).toBeInTheDocument();
    expect(screen.getByTestId("theme-toggle-trigger").getAttribute("aria-expanded")).toBe("true");
  });

  it("renders 4 appearance cards inside popover（含 hc）", () => {
    render(<ThemeToggle />);
    fireEvent.click(screen.getByTestId("theme-toggle-trigger"));
    expect(screen.getByTestId("appearance-card-light")).toBeInTheDocument();
    expect(screen.getByTestId("appearance-card-dark")).toBeInTheDocument();
    expect(screen.getByTestId("appearance-card-system")).toBeInTheDocument();
    expect(screen.getByTestId("appearance-card-hc")).toBeInTheDocument();
  });

  it("clicking a card closes popover (via onAfterChange)", () => {
    render(<ThemeToggle />);
    fireEvent.click(screen.getByTestId("theme-toggle-trigger"));
    fireEvent.click(screen.getByTestId("appearance-card-dark"));
    expect(screen.queryByTestId("theme-popover")).toBeNull();
    expect(noopPatch).toHaveBeenCalledWith("general.appearance.preference", "dark");
  });
});
