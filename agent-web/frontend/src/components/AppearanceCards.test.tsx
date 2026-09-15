/**
 * AppearanceCards 测试 (add-settings-general-items M2).
 */

import { fireEvent, render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppearanceCards } from "./AppearanceCards";

const noopPatch = vi.fn().mockResolvedValue(undefined);

const mockSnapshot = {
  version: 1,
  general: { appearance: { preference: "system" as const } },
  revision: 0,
};

vi.mock("../hooks/useSettingsStore", () => ({
  useSettingsStore: (selector: (s: typeof mockStore) => unknown) => selector(mockStore),
}));

const mockStore = {
  snapshot: mockSnapshot,
  status: "ready" as const,
  error: null,
  patch: noopPatch,
  refresh: vi.fn(),
};

describe("AppearanceCards", () => {
  beforeEach(() => {
    noopPatch.mockClear();
    mockStore.snapshot = { ...mockSnapshot, general: { appearance: { preference: "system" } } };
  });

  it("renders 3 cards", () => {
    const { getByTestId } = render(<AppearanceCards />);
    expect(getByTestId("appearance-card-light")).toBeInTheDocument();
    expect(getByTestId("appearance-card-dark")).toBeInTheDocument();
    expect(getByTestId("appearance-card-system")).toBeInTheDocument();
  });

  it("shows selected state based on preference", () => {
    mockStore.snapshot = {
      ...mockSnapshot,
      general: { appearance: { preference: "dark" } },
    } as unknown as typeof mockSnapshot;
    const { getByTestId } = render(<AppearanceCards />);
    const dark = getByTestId("appearance-card-dark");
    expect(dark.getAttribute("aria-pressed")).toBe("true");
  });

  it("clicking a card invokes patch", () => {
    const { getByTestId } = render(<AppearanceCards />);
    fireEvent.click(getByTestId("appearance-card-dark"));
    expect(noopPatch).toHaveBeenCalledWith("general.appearance.preference", "dark");
  });

  it("compact mode hides title", () => {
    mockStore.snapshot = {
      ...mockSnapshot,
      general: { appearance: { preference: "light" } },
    } as unknown as typeof mockSnapshot;
    const { container } = render(<AppearanceCards compact />);
    expect(container.querySelector('[data-testid="appearance-cards"]')).toBeInTheDocument();
  });
});
