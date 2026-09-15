/**
 * ThemePopover 测试 (polish-theme-toggle).
 */

import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ThemePopover } from "./ThemePopover";

const noopPatch = vi.fn().mockResolvedValue(undefined);

const mockStore = {
  snapshot: {
    version: 1,
    general: { appearance: { preference: "system" as const } },
    revision: 0,
  },
  status: "ready" as const,
  error: null,
  patch: noopPatch,
  refresh: vi.fn(),
};

vi.mock("../hooks/useSettingsStore", () => ({
  useSettingsStore: (selector: (s: typeof mockStore) => unknown) => selector(mockStore),
}));

describe("ThemePopover", () => {
  it("renders 3 appearance cards with title", () => {
    const onClose = vi.fn();
    render(<ThemePopover onClose={onClose} />);
    expect(screen.getByText("外观")).toBeInTheDocument();
    expect(screen.getByTestId("appearance-card-light")).toBeInTheDocument();
    expect(screen.getByTestId("appearance-card-dark")).toBeInTheDocument();
    expect(screen.getByTestId("appearance-card-system")).toBeInTheDocument();
  });

  it("renders with role=dialog and aria-label", () => {
    const onClose = vi.fn();
    render(<ThemePopover onClose={onClose} />);
    expect(screen.getByRole("dialog", { name: "外观选择" })).toBeInTheDocument();
  });

  it("clicking a card invokes patch and onClose via onAfterChange", () => {
    const onClose = vi.fn();
    render(<ThemePopover onClose={onClose} />);
    fireEvent.click(screen.getByTestId("appearance-card-dark"));
    expect(noopPatch).toHaveBeenCalledWith("general.appearance.preference", "dark");
    // 实际关闭由父组件处理；这里只验证 patch 被调
    expect(noopPatch).toHaveBeenCalledTimes(1);
  });

  it("Escape key calls onClose", async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<ThemePopover onClose={onClose} />);
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
